package com.curbme.app.core.utils

/**
 * Why we made this file:
 * In a complex system like CurbMe, many different parts of the app
 * (Services, Receivers, and UI) need to refer to the same specific values,
 * such as Notification IDs or Preference keys.
 * 
 * Instead of "hard-coding" these values (writing "curbme_prefs" in five
 * different files), we store them here. This creates a "Single Source of Truth."
 * If you ever need to change a channel ID or a request code, you only change it
 * here once, and the entire app updates automatically.
 * 
 * What the file name defines:
 * "Constants" signifies that this class holds immutable (unchanging) values
 * that are used globally across the project.
 */
object Constants {
    // ── Logging ───────────────────────────────────────────────────────────────
    const val LOG_TAG: String = "CurbMe"

    // ── Notification IDs ──────────────────────────────────────────────────────
    const val NOTIFICATION_ID_GUARDIAN: Int = 1
    const val NOTIFICATION_ID_VPN: Int = 3


    // ── Notification Channels ─────────────────────────────────────────────────
    const val CHANNEL_GUARDIAN: String = "channel_guardian"
    const val CHANNEL_OVERLAY: String = "channel_overlay"
    const val CHANNEL_VPN: String = "channel_vpn"
    const val CHANNEL_SCREEN_TIME: String = "channel_screen_time"
    const val CHANNEL_ALERTS: String = "channel_alerts"
    const val CHANNEL_SILENT: String = "channel_silent"

    const val NOTIFICATION_ID_SETTINGS_BLOCK: Int = 1004


    // ── SharedPrefs / DataStore keys ──────────────────────────────────────────
    const val PREFS_NAME: String = "curbme_prefs"

    // ── Request Codes ─────────────────────────────────────────────────────────
    const val RC_OVERLAY_PERMISSION: Int = 1001
    const val RC_USAGE_STATS: Int = 1002
    const val RC_DEVICE_ADMIN: Int = 1003
    const val RC_VPN_PERMISSION: Int = 1004
    const val RC_NOTIFICATION_PERM: Int = 1005

    // ── WorkManager Tags ──────────────────────────────────────────────────────
    const val WORK_USAGE_SYNC: String = "work_usage_sync"
    const val WORK_BLOCKLIST_UPDATE: String = "work_blocklist_update"

    // ── Deep-link / Intent extras ─────────────────────────────────────────────
    const val EXTRA_TARGET_SCREEN: String = "extra_target_screen"
    const val EXTRA_BLOCKED_PACKAGE: String = "extra_blocked_package"

    const val NOTIFICATION_ID_LOCK_TIMER: Int = 1005


    // ── Allowlist page keywords ────────────────────────────────────────────────
    const val ALLOW_ACCESSIBILITY: String = "accessibility"
    const val ALLOW_OVERLAY: String = "display over other apps"
    const val ALLOW_USAGE_STATS: String = "usage access"
    const val ALLOW_BATTERY: String = "battery optimization"
    const val ALLOW_DEVICE_ADMIN: String = "device admin"
    const val ALLOW_VPN: String = "vpn"
    const val ALLOW_NOTIFICATIONS: String = "notifications"
    const val ALLOW_AUTOSTART: String = "autostart"

    // ── Browser Constants ─────────────────────────────────────────────────────
    object BrowserConstants {
        const val CHROME = "com.android.chrome"
        const val FIREFOX = "org.mozilla.firefox"
        const val EDGE = "com.microsoft.emmx"
        const val OPERA = "com.opera.browser"
        const val SAMSUNG_INTERNET = "com.sec.android.app.sbrowser"
        const val BRAVE = "com.brave.browser"
        const val TOR = "org.torproject.torbrowser"
        const val TOR_ALPHA = "org.torproject.torbrowser_alpha"

        val SUPPORTED_BROWSERS = listOf(
            CHROME, FIREFOX, EDGE, OPERA, SAMSUNG_INTERNET, BRAVE, TOR, TOR_ALPHA
        )
    }
}