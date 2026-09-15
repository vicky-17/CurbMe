package com.curbme.app.data.local.prefs

import com.curbme.app.data.models.ReelPlanConfig

/**
 * Data class representing all app settings, used by DataStore for reactive,
 * process-safe updates.
 */
data class Settings(
    val parentPin: String = "",
    val isSetupComplete: Boolean = false,
    val isBlockShorts: Boolean = false,
    val isBlockPorn: Boolean = false,
    val isSafeSearchEnabled: Boolean = false,
    val isYoutubeFilterEnabled: Boolean = false,
    val blockedPackages: Set<String> = emptySet(),
    val dailyScreenTimeLimitMinutes: Int = 0,
    val isScreenTimeEnabled: Boolean = false,
    val isVpnFilterEnabled: Boolean = false,
    val isKeepVpnAlive: Boolean = false,
    val isPreventVpnOverride: Boolean = false,
    val isPremium: Boolean = false,
    val premiumExpiryEpoch: Long = 0,
    val userUid: String? = null,
    val userEmail: String? = null,
    val isAntiUninstallEnabled: Boolean = false,
    val lockUntilTimestamp: Long = 0,
    val lockDurationMs: Long = 0,
    val lockAnchorElapsed: Long = 0,
    val lockNtpOffset: Long = Long.MIN_VALUE,
    val lastKnownDeviceTime: Long = 0,
    val isPermissionBlockEnabled: Boolean = false,
    val isPrivateDnsEnabled: Boolean = false,
    val selectedPrivateDnsHostname: String = "dns.adguard.com",
    val customPrivateDnsHostnames: Set<String> = setOf(
        "family-filter-dns.cleanbrowsing.org",
        "family.cloudflare-dns.com",
        "adult-filter-dns.cleanbrowsing.org",
        "dns.adguard.com",
        "dns.quad9.net",
        "cloudflare-dns.com",
        "doh.cleanbrowsing.org",
        "family-filter-dns.com"
    ),
    val isPrivateDnsLocked: Boolean = false,
    val blockedWebsites: Set<String> = emptySet(),
    val isBlockUnsupportedBrowsers: Boolean = false,
    val isBankingBypassEnabled: Boolean = false,
    val bankingBypassPackage: String? = null,
    val bankingBypassStartTime: Long = 0L,
    val appBypassMap: Map<String, Long> = emptyMap(),
    val lastVpnHeartbeatType: String? = "",
    val lastVpnHeartbeatTimestamp: Long = 0L,
    val isAppUsageTrackingEnabled: Boolean = true,
    val isAutoHealEnabled: Boolean = true,
    val isWebsiteUsageTrackingEnabled: Boolean = true,
    val isStrictModeEnabled: Boolean = false,
    val strictModeUntil: Long = 0L,
    val reelPlanConfig: ReelPlanConfig = ReelPlanConfig()
) {
    val isWebsiteStrictModeActive: Boolean
        get() = isStrictModeEnabled && System.currentTimeMillis() < strictModeUntil

    val isSettingsLocked: Boolean
        get() {
            val now = System.currentTimeMillis()
            val duration = this.lockDurationMs
            if (duration <= 0) return false

            // Method A: ElapsedRealtime (tamper-proof within boot)
            // Note: In multi-process, anchorElapsed should be synced via DataStore
            val anchorElapsed = this.lockAnchorElapsed
            if (anchorElapsed > 0) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - anchorElapsed
                if (elapsed < duration) return true
            }

            // Method B: NTP-adjusted time
            val ntpOffset = this.lockNtpOffset
            if (ntpOffset != Long.MIN_VALUE) {
                val ntpNow = now + ntpOffset
                val ntpUnlockAt = this.lockUntilTimestamp
                if (ntpNow < ntpUnlockAt) return true
            }

            // Method C: Tamper detection — clock jumped backward
            val lastKnown = this.lastKnownDeviceTime
            if (lastKnown > 0 && now < lastKnown - 60000L) {
                return true
            }

            return false
        }
}
