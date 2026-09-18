package com.curbme.app.service.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.provider.Settings
import android.util.Log
import com.curbme.app.core.utils.PermissionHelper
import com.curbme.app.core.utils.PersistenceManager
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.service.vpn.DnsVpnService
import java.util.Collections
import java.util.EnumSet


class ProtectionStateMonitor(context: Context) {
    private val context: Context
    private val prefs: PrefsManager

    // ── Constructor ───────────────────────────────────────────────────────────
    init {
        this.context = context.applicationContext
        this.prefs = PrefsManager(this.context)
    }

    fun check(currentForegroundPackage: String? = null): MutableSet<ProtectionIssue?> {
        val issues: MutableSet<ProtectionIssue?> =
            EnumSet.noneOf<ProtectionIssue?>(ProtectionIssue::class.java)

        checkPermissions(issues, currentForegroundPackage)
        checkVpnState(issues)

        if (!issues.isEmpty()) {
            Log.w(TAG, "Protection issues detected: " + issues)
        }

        return Collections.unmodifiableSet<ProtectionIssue?>(issues)
    }

    /**
     * Convenience method: returns true if ANY issue is currently active.
     */
    fun hasAnyIssue(): Boolean {
        return !check().isEmpty()
    }

    /**
     * Convenience method: returns true if a specific issue is active.
     */
    fun hasIssue(issue: ProtectionIssue?): Boolean {
        return check().contains(issue)
    }

    // ── Permission checks ─────────────────────────────────────────────────────
    private var overlayMissingCount = 0
    private var lastHealAttemptMs = 0L

    private fun checkPermissions(issues: MutableSet<ProtectionIssue?>, currentForegroundPackage: String? = null) {
        val now = System.currentTimeMillis()

        // 1. Shizuku Auto-Healing for Accessibility Service (runs whenever Accessibility is needed by any active feature)
        val isAccessibilityNeeded = prefs.isBlockShorts || prefs.isAntiUninstallEnabled || prefs.isPermissionBlockEnabled || prefs.isDisablePopupWindowEnabled || prefs.blockedPackages.isNotEmpty()
        if (!PermissionHelper.isAccessibilityEnabled(context) && isAccessibilityNeeded) {
            if (prefs.isAutoHealEnabled && com.curbme.app.core.utils.ShizukuManager.hasShizukuPermission()) {
                if (now - lastHealAttemptMs > 3000L) {
                    lastHealAttemptMs = now
                    com.curbme.app.core.utils.ShizukuManager.healAccessibilityService(context)
                }
            }
        }

        // If the user disabled strict permission blocking overlay from the security screen, bypass overlay checks
        if (!prefs.isPermissionBlockEnabled) {
            return
        }

        // 2. Strict Permission Check (for overlay warnings)
        if (!PermissionHelper.isAccessibilityEnabled(context)) {
            // Give Shizuku grace period to execute shell write and re-bind service
            if (prefs.isAutoHealEnabled && com.curbme.app.core.utils.ShizukuManager.hasShizukuPermission()) {
                if (PermissionHelper.isAccessibilityEnabled(context) || (now - lastHealAttemptMs < 2500L)) {
                    if (PermissionHelper.isAccessibilityEnabled(context)) {
                        return
                    }
                }
            }

            // Check if Banking Bypass is active AND we are in the allowed app
            val isBypassActive = prefs.isBankingBypassEnabled
            val bypassPkg = prefs.bankingBypassPackage
            val startTime = prefs.bankingBypassStartTime
            val isTimedOut = (now - startTime) > 10 * 60 * 1000L // 10 minutes

            // During Banking Mode, we allow:
            // 1. The selected Banking App
            // 2. Our own app (Digital Monk) - so the user can finish the mode
            // 3. The Home Screen (Launcher)
            // 4. "null" (if foreground app detection is transitioning, don't block immediately)
            val isAllowedApp = currentForegroundPackage == null || 
                               currentForegroundPackage == bypassPkg || 
                               currentForegroundPackage == context.packageName || 
                               currentForegroundPackage == PermissionHelper.getLauncherPackageName(context)

            val shouldSuppress = isBypassActive && !isTimedOut && isAllowedApp

            if (!shouldSuppress) {
                issues.add(ProtectionIssue.ACCESSIBILITY_DISABLED)
            }
        }

        // 2. Display over other apps
        if (!PermissionHelper.canDrawOverlays(context)) {
            overlayMissingCount++
            if (overlayMissingCount >= OVERLAY_MISSING_THRESHOLD) {
                issues.add(ProtectionIssue.OVERLAY_PERMISSION_MISSING)
            }
        } else {
            overlayMissingCount = 0 // reset on success
        }

        // 3. Usage stats
        if (!PermissionHelper.hasUsageStatsPermission(context)) {
            issues.add(ProtectionIssue.USAGE_STATS_MISSING)
        }

        // 4. Battery optimization
        //    We use PersistenceManager here (same as WatchdogService and PermissionHelper)
        // Battery optimization
        if (!PersistenceManager.isBatteryOptimizationDisabled(context)) {
            // MIUI bug:
            // When VPN is active some Xiaomi devices falsely report
            // battery optimization as enabled.

            if (!DnsVpnService.isServiceRunning) {
                issues.add(ProtectionIssue.BATTERY_OPTIMIZATION_ACTIVE)
            }
        }
    }

    // ── VPN / content filter checks ───────────────────────────────────────────
    private fun checkVpnState(issues: MutableSet<ProtectionIssue?>) {
        // VPN checks only matter when the parent has enabled the filter
        if (!prefs.isSafeSearchEnabled) {
            return
        }

        // ── Check 1: VPN permission revoked ───────────────────────────────────
        // VpnService.prepare() returns null when permission is already granted.
        // A non-null result means we need to re-ask.
        try {
            val vpnPrepareIntent = VpnService.prepare(context)
            if (vpnPrepareIntent != null) {
                // Permission was revoked or never granted in this boot cycle
                issues.add(ProtectionIssue.VPN_PERMISSION_REVOKED)
                // If permission is gone, the service can't run — no point checking further
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "VpnService.prepare() threw: " + e.message)
        }

        // ── Check 2: Our VPN service is dead ──────────────────────────────────
        if (!DnsVpnService.isServiceRunning) {
            issues.add(ProtectionIssue.VPN_SERVICE_DEAD)
        }

        // ── Check 3: Always-On VPN not set to us ─────────────────────────────
        // We check the system setting directly (same approach as PermissionHelper)
        if (!this.isAlwaysOnVpnSetToUs) {
            issues.add(ProtectionIssue.ALWAYS_ON_VPN_NOT_SET)
        }

        // ── Check 4: Another VPN is active ────────────────────────────────────
        // If our service IS running but another VPN is also active via
        // NetworkCapabilities, it means someone found a way to layer a second VPN
        // on top (some rooted approaches or specific OEM behaviors).
        // If our service is NOT running, check if a foreign VPN took our slot.
        if (this.isAnotherVpnActive) {
            issues.add(ProtectionIssue.ANOTHER_VPN_ACTIVE)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private val isAlwaysOnVpnSetToUs: Boolean
        /**
         * Returns true if the system's Always-On VPN is set to our package.
         * 
         * Reads the undocumented but stable Settings.Secure key "always_on_vpn_app"
         * which is the same approach used in PermissionHelper.isAlwaysOnVpnActive().
         */
        get() {
            try {
                val alwaysOnApp = Settings.Secure.getString(
                    context.getContentResolver(),
                    "always_on_vpn_app"
                )
                return context.getPackageName() == alwaysOnApp
            } catch (e: Exception) {
                // If we can't read this setting, assume it's not set (conservative)
                Log.w(
                    TAG,
                    "Could not read always_on_vpn_app setting: " + e.message
                )
                return false
            }
        }

    private val isAnotherVpnActive: Boolean
        /**
         * Returns true if any VPN is active on the device that is NOT ours.
         * 
         * Strategy: use ConnectivityManager.NetworkCapabilities to detect an active
         * VPN transport. If a VPN transport is present AND our service is not running,
         * a foreign VPN is active. If our service IS running alongside another VPN
         * transport, it's likely the same situation.
         * 
         * This is API 21+ but our minSdk is 26 so no guard needed.
         */
        get() {
            try {
                val cm =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
                if (cm == null) return false

                val activeNetwork = cm.getActiveNetwork()
                if (activeNetwork == null) return false

                val caps = cm.getNetworkCapabilities(activeNetwork)
                if (caps == null) return false

                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    // A VPN transport is active. Is it ours?
                    if (!DnsVpnService.isServiceRunning) {
                        // Our service is off but a VPN is active → foreign VPN
                        return true
                    }
                    // Our service is running → this VPN IS ours, expected
                    return false
                }
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "VPN transport check failed: " + e.message
                )
            }
            return false
        }

    private val isAnyVpnActive: Boolean
        get() {
            try {
                val cm =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?

                if (cm == null) return false

                val activeNetwork = cm.getActiveNetwork()
                if (activeNetwork == null) return false

                val caps =
                    cm.getNetworkCapabilities(activeNetwork)

                return caps != null &&
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "isAnyVpnActive failed: " + e.message
                )
                return false
            }
        }

    companion object {
        private const val TAG = "ProtectionStateMonitor"

        private const val OVERLAY_MISSING_THRESHOLD = 30 // 30 × 1s = 30 seconds
    }
}