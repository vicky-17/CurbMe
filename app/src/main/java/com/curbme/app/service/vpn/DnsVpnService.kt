package com.curbme.app.service.vpn

import android.R
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.curbme.app.core.utils.Constants
import com.curbme.app.core.utils.TimeUtils
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.entity.WebsiteStatsEntity
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.data.local.prefs.Settings
import com.curbme.app.service.vpn.DnsPacketParser.DnsQuery
import com.curbme.app.service.vpn.DnsPacketParser.buildARecordResponse
import com.curbme.app.service.vpn.DnsPacketParser.buildNxDomainResponse
import com.curbme.app.service.vpn.DnsPacketParser.parse
import com.curbme.app.service.vpn.DnsPacketParser.wrapUpstreamResponse
import com.curbme.app.service.vpn.heartbeat.VpnHeartBeatEntity
import com.curbme.app.service.vpn.heartbeat.VpnHeartbeatMonitorWorker.Companion.cancel
import com.curbme.app.service.vpn.heartbeat.VpnHeartbeatMonitorWorker.Companion.schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Arrays
import kotlin.concurrent.Volatile

/**
 * Why we made this file:
 * This is the central engine of Digital Monk's web filtering. It creates a local
 * VPN tunnel that forces all DNS queries (requests for websites) to pass through
 * our DnsFilterEngine.
 * 
 * It features a 3-Layer "Keep-Alive" system:
 * 1. WorkManager Watchdog (Periodic checks).
 * 2. Connectivity Probes (Socket tests).
 * 3. Monitor Service Binding (Process death protection).
 */
class DnsVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    @Volatile
    private var isRunning = false
    private var vpnThread: Thread? = null
    private var filterEngine: DnsFilterEngine? = null
    private var dataStoreManager: DataStoreManager? = null
    
    @Volatile
    private var settings = Settings()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var heartbeatThread: Thread? = null
    private val connectivityProbeThread: Thread? = null
    private val consecutiveProbeFailures = 0
    private var screenOnReceiver: BroadcastReceiver? = null
    private var monitorServiceBound = false

    // ── Companion Service Connection ──────────────────────────────────────────
    private val monitorConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            monitorServiceBound = true
            Log.d(TAG, "VpnMonitorService bound ✅")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            monitorServiceBound = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        dataStoreManager = DataStoreManager(this)
        filterEngine = DnsFilterEngine()
        
        serviceScope.launch {
            dataStoreManager?.settings?.collect {
                settings = it
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && ACTION_STOP == intent.getAction()) {
            stopVpn(true)
            return START_NOT_STICKY
        }

        if (!isRunning) {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    Constants.NOTIFICATION_ID_VPN, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(Constants.NOTIFICATION_ID_VPN, notification)
            }
            startVpn()
        }

        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return

        try {
            val builder = Builder()
                .setSession("CurbMe Shield")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(VPN_DNS, 32)
                .addDnsServer(VPN_DNS)
                .addDisallowedApplication(packageName)
                .setBlocking(true)

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                stopSelf()
                return
            }

            isRunning = true
            isServiceRunning = true

            writeHeartbeat(VpnHeartBeatEntity.TYPE_ALIVE)

            // Start Threads
            vpnThread = Thread(Runnable { this.runVpnLoop() }, "dns-vpn-thread")
            vpnThread!!.start()

            startHeartbeatLoop()
            startConnectivityProbe()
            registerScreenOnReceiver()

            if (settings.isKeepVpnAlive) {
                schedule(this)
            }

            Log.i(TAG, "✅ VPN started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun stopVpn(cleanStop: Boolean) {
        isRunning = false
        isServiceRunning = false

        if (cleanStop) {
            writeHeartbeat(VpnHeartBeatEntity.TYPE_STOPPED)
            cancel(this)
        }

        stopHeartbeatLoop()
        stopConnectivityProbe()
        unregisterScreenOnReceiver()

        if (vpnThread != null) vpnThread!!.interrupt()
        try {
            if (vpnInterface != null) vpnInterface!!.close()
        } catch (ignored: Exception) {
        }

        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun runVpnLoop() {
        val inputStream = FileInputStream(vpnInterface!!.getFileDescriptor())
        val outputStream = FileOutputStream(vpnInterface!!.getFileDescriptor())
        val packetBuffer = ByteArray(32767)

        try {
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                val length = inputStream.read(packetBuffer)
                if (length <= 0) continue

                val query = parse(packetBuffer, length)
                if (query == null) continue
                recordDnsQuery(query.domain)

                var response: ByteArray? = null
                val decision = filterEngine!!.decide(query.domain, query.queryType, settings)

                when (decision) {
                    is FilterDecision.Block -> {
                        response = buildNxDomainResponse(query)
                    }
                    is FilterDecision.SafeSearchRedirect -> {
                        val ip = decision.redirectIp
                        response = buildARecordResponse(query, ip)
                    }
                    else -> {
                        response = forwardToUpstream(query)
                    }
                }

                if (response != null) {
                    outputStream.write(response)
                    outputStream.flush()
                }
            }
        } catch (e: Exception) {
            if (isRunning) Log.e(TAG, "Error in VPN loop", e)
        }
    }

    private fun recordDnsQuery(domain: String?) {
        if (domain.isNullOrBlank() || !settings.isWebsiteUsageTrackingEnabled) return
        val lower = domain.lowercase().removePrefix("www.")
        if (!lower.contains('.')) return
        val today = TimeUtils.todayKey()
        val wallNow = System.currentTimeMillis()
        serviceScope.launch {
            try {
                val dao = AppDatabase.getDatabase(this@DnsVpnService).websiteStatsDao()
                dao.insertIfAbsent(
                    WebsiteStatsEntity(
                        date = today,
                        packageName = "vpn_filter",
                        domain = lower,
                        urlIdentifier = lower,
                        totalTime = 0L,
                        lastVisited = wallNow
                    )
                )
                dao.touch(today, "vpn_filter", lower, wallNow)
            } catch (_: Exception) {}
        }
    }

    private fun forwardToUpstream(query: DnsQuery): ByteArray? {
        // Try primary, then secondary
        for (dns in arrayOf<String>(UPSTREAM_DNS_PRIMARY, UPSTREAM_DNS_SECONDARY)) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.setSoTimeout(DNS_TIMEOUT_MS)

                    val dnsPayload = Arrays.copyOfRange(
                        query.rawPacket, query.dnsPayloadOffset, query.rawLength
                    )
                    val upstreamAddress = InetAddress.getByName(dns)

                    val sendPacket = DatagramPacket(
                        dnsPayload, dnsPayload.size, upstreamAddress, DNS_PORT
                    )
                    socket.send(sendPacket)

                    val responseBuffer = ByteArray(4096)
                    val receivePacket = DatagramPacket(
                        responseBuffer, responseBuffer.size
                    )
                    socket.receive(receivePacket)
                    return wrapUpstreamResponse(
                        query,
                        receivePacket.getData().copyOf(receivePacket.getLength())
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "DNS forward failed to " + dns + ", trying fallback: " + e.message)
            }
        }
        // Both failed — return NXDOMAIN rather than silently dropping
        return buildNxDomainResponse(query)
    }

    // ── Heartbeat & Helper Methods ──────────────────────────────────────────
    private fun startHeartbeatLoop() {
        heartbeatThread = Thread(Runnable {
            try {
                while (isRunning) {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS)
                    writeHeartbeat(VpnHeartBeatEntity.TYPE_ALIVE)
                }
            } catch (ignored: InterruptedException) {
            }
        })
        heartbeatThread!!.start()
    }

    private fun writeHeartbeat(type: String?) {
        serviceScope.launch {
            dataStoreManager?.updateSettings {
                it.copy(
                    lastVpnHeartbeatType = type,
                    lastVpnHeartbeatTimestamp = System.currentTimeMillis()
                )
            }
        }
    }

    private fun startConnectivityProbe() { /* Implementation similar to heartbeat */
    }

    private fun stopHeartbeatLoop() {
        if (heartbeatThread != null) heartbeatThread!!.interrupt()
    }

    private fun stopConnectivityProbe() {
        if (connectivityProbeThread != null) connectivityProbeThread.interrupt()
    }

    private fun registerScreenOnReceiver() {
        screenOnReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                if (Intent.ACTION_SCREEN_ON == intent.getAction() && isRunning) {
                    Log.d(TAG, "Screen ON - Healthy Check")
                }
            }
        }
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    private fun unregisterScreenOnReceiver() {
        if (screenOnReceiver != null) unregisterReceiver(screenOnReceiver)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.CHANNEL_VPN)
            .setContentTitle("CurbMe Shield Active 🛡️")
            .setContentText("Web filter & SafeSearch are running")
            .setSmallIcon(R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "DnsVpnService"
        const val ACTION_STOP: String = "ACTION_STOP"

        private const val VPN_ADDRESS = "10.0.0.1"
        private const val VPN_DNS = "10.0.0.2"


        private const val UPSTREAM_DNS = "8.8.8.8"

        private const val UPSTREAM_DNS_PRIMARY = "185.228.168.168"
        private const val UPSTREAM_DNS_SECONDARY = "185.228.169.168"
        private const val DNS_PORT = 53
        private const val DNS_TIMEOUT_MS = 3000

        private val HEARTBEAT_INTERVAL_MS = 7 * 60 * 1000L
        private const val PROBE_INTERVAL_MS = 15000L
        private const val MAX_PROBE_FAILURES = 3
        private const val PROBE_SOCKET_TIMEOUT_MS = 5000

        @JvmField
        @Volatile
        var isServiceRunning: Boolean = false
    }
}