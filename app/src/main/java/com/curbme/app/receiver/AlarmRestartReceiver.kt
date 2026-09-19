package com.curbme.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.curbme.app.core.utils.AlarmScheduler.scheduleRepeating
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.service.WatchdogService
import com.curbme.app.service.overlay.FocusSessionManager
import com.curbme.app.service.vpn.DnsVpnService

class AlarmRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Alarm fired — reviving services")

        FocusSessionManager.checkAndResumeActiveSession(context, "AlarmRestartReceiver")

        val prefs = PrefsManager(context)
        if (!prefs.hasPin()) return

        // Restart Watchdog
        WatchdogService.start(context)

        // Restart VPN if needed
        if (prefs.isSafeSearchEnabled && !DnsVpnService.isServiceRunning) {
            try {
                val vpn = Intent(context, DnsVpnService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(vpn)
                } else {
                    context.startService(vpn)
                }
            } catch (e: Exception) {
                Log.e(TAG, "VPN restart failed", e)
            }
        }

        // Re-schedule next alarm immediately (keeps the chain alive)
        scheduleRepeating(context)
    }

    companion object {
        private const val TAG = "AlarmRestartReceiver"
        const val ACTION_ALARM_RESTART: String = "com.curbme.app.ALARM_RESTART"
    }
}
