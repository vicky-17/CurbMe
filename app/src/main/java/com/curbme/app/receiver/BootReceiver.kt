package com.curbme.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.curbme.app.core.deviceowner.DevicePolicyHelper
import com.curbme.app.core.utils.AlarmScheduler
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.service.WatchdogService
import com.curbme.app.service.overlay.FocusSessionManager
import com.curbme.app.service.vpn.DnsVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.getAction() ?: return

        if ((Intent.ACTION_BOOT_COMPLETED != action) &&
            (Intent.ACTION_LOCKED_BOOT_COMPLETED != action) &&
            ("android.intent.action.QUICKBOOT_POWERON" != action) &&
            ("com.htc.intent.action.QUICKBOOT_POWERON" != action)
        ) {
            return
        }

        Log.i(TAG, "Boot event received ($action) — starting Digital Monk services")

        if (Intent.ACTION_LOCKED_BOOT_COMPLETED == action) {
            Log.i(TAG, "Locked boot event (direct boot) — skipping Room DB and credential-encrypted PrefsManager access until device unlock")
            return
        }

        // Check and resume active Focus Mode session
        FocusSessionManager.checkAndResumeActiveSession(context, "BootReceiver")

        val prefs = PrefsManager(context)

        // Only start services if the app was set up (has a PIN)
        if (!prefs.hasPin()) {
            Log.i(TAG, "App not set up yet — skipping service start")
            return
        }

        // 1. Always start WatchdogService — it's the root guardian
        WatchdogService.start(context)
        WatchdogService.startAppBlockEngine(context) // Also start blocker
        AlarmScheduler.scheduleRepeating(context)

        // 2. Restart VPN if it was active before reboot
        if (prefs.isSafeSearchEnabled) {
            Log.i(TAG, "Restarting DnsVpnService (was active before reboot)")
            try {
                val vpnIntent = Intent(context, DnsVpnService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(vpnIntent)
                } else {
                    context.startService(vpnIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart DnsVpnService on boot", e)
                // WatchdogService will retry in its health check loop
            }
        }

        // 3. Re-schedule the JobScheduler backup
        WatchdogService.scheduleJobBackup(context)

        // 4. Re-apply Private DNS — this does a blocking connectivity check,
        //    so it MUST run off the main thread via goAsync().
        if (prefs.isPrivateDnsEnabled || prefs.isPrivateDnsLocked) {
            val pendingResult = goAsync()
            Thread {
                try {
                    DevicePolicyHelper.reapplyPolicyIfMismatched(context)
                } finally {
                    pendingResult.finish()
                }
            }.start()
        }

        Log.i(TAG, "✅ All services started after boot")
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
