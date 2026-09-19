package com.curbme.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.service.overlay.FocusSessionManager
import com.curbme.app.service.vpn.DnsVpnService
import com.curbme.app.service.vpn.heartbeat.VpnHeartbeatMonitorWorker

/**
 * Why we made this file:
 * When an Android app is updated through the Play Store or during development
 * via ADB, the Android system kills all of the app's running background services.
 * * For a parental control app, this is dangerous because the VPN filter would
 * turn off and stay off. This BroadcastReceiver listens specifically for the
 * "MY_PACKAGE_REPLACED" system event and immediately turns the VPN and watchdogs
 * back on so the child remains protected after an update.
 * 
 * What the file name defines:
 * "AppUpdate" defines the event it listens for.
 * "Receiver" identifies it as a BroadcastReceiver component in the Android framework.
 */
class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Safe check for null intent and exact action match in Java
        if (intent == null || Intent.ACTION_MY_PACKAGE_REPLACED != intent.getAction()) {
            return
        }

        Log.i(TAG, "App updated — checking if VPN and Focus overlay need restart")

        FocusSessionManager.checkAndResumeActiveSession(context, "AppUpdateReceiver")

        val prefs = PrefsManager(context)

        if (!prefs.hasPin()) {
            Log.d(TAG, "App not set up — skipping")
            return
        }

        // Re-schedule WorkManager watchdog regardless (it gets cancelled on update)
        if (prefs.isSafeSearchEnabled && prefs.isKeepVpnAlive) {
            VpnHeartbeatMonitorWorker.schedule(context)
        }

        // Restart VPN if it was active before the update
        if (prefs.isSafeSearchEnabled) {
            Log.i(TAG, "Restarting DnsVpnService after app update")
            try {
                val vpnIntent = Intent(context, DnsVpnService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(vpnIntent)
                } else {
                    context.startService(vpnIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart VPN after update", e)
            }
        }
    }

    companion object {
        private const val TAG = "AppUpdateReceiver"
    }
}
