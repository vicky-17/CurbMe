package com.curbme.app.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.curbme.app.core.utils.Constants
import androidx.core.content.edit


class PrefsManager(context: Context) {
    private val prefs: SharedPreferences

    // ── PIN ───────────────────────────────────────────────────────────────────
    fun savePin(pin: String?) {
        prefs.edit { putString(KEY_PIN, pin) }
    }

    val pin: String
        get() = prefs.getString(KEY_PIN, "")!!

    fun hasPin(): Boolean {
        val pin: String = prefs.getString(KEY_PIN, "")!!
        return !pin.isEmpty()
    }

    fun clearPin() {
        prefs.edit { remove(KEY_PIN) }
    }

    var isSetupComplete: Boolean
        // ── Setup ─────────────────────────────────────────────────────────────────
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) {
            prefs.edit { putBoolean(KEY_SETUP_COMPLETE, value) }
        }

    var isBlockShorts: Boolean
        // ── Content Filters ───────────────────────────────────────────────────────
        get() = prefs.getBoolean(KEY_BLOCK_SHORTS, false)
        set(value) {
            prefs.edit { putBoolean(KEY_BLOCK_SHORTS, value) }
        }

    var isReelCounterOverlayOn: Boolean
        get() = prefs.getBoolean("key_reel_counter_overlay", true)
        set(value) {
            prefs.edit { putBoolean("key_reel_counter_overlay", value) }
        }

    var isBlockPorn: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_PORN, false)
        set(value) {
            prefs.edit { putBoolean(KEY_BLOCK_PORN, value) }
        }

    var isEnforceSafeSearch: Boolean
        get() = prefs.getBoolean(KEY_SAFE_SEARCH, false)
        set(value) {
            prefs.edit { putBoolean(KEY_SAFE_SEARCH, value) }
        }

    val blockedPackages: MutableSet<String?>
        // ── App Blocking ──────────────────────────────────────────────────────────
        get() = prefs.getStringSet(
            PrefsManager.Companion.KEY_BLOCKED_PACKAGES,
            java.util.HashSet<kotlin.String?>()
        )!!

    fun saveBlockedPackages(packages: MutableSet<String?>?) {
        prefs.edit { putStringSet(KEY_BLOCKED_PACKAGES, packages) }
    }

    fun isAppBlocked(packageName: String?): Boolean {
        return this.blockedPackages.contains(packageName)
    }

    fun addBlockedPackage(packageName: String?) {
        val packages: MutableSet<String?> = HashSet<String?>(
            this.blockedPackages
        )
        packages.add(packageName)
        saveBlockedPackages(packages)
    }

    fun removeBlockedPackage(packageName: String?) {
        val packages: MutableSet<String?> = HashSet<String?>(
            this.blockedPackages
        )
        packages.remove(packageName)
        saveBlockedPackages(packages)
    }

    var dailyScreenTimeLimitMinutes: Int
        // ── Screen Time ───────────────────────────────────────────────────────────
        get() = prefs.getInt(KEY_SCREEN_TIME_LIMIT, 0)
        set(value) {
            prefs.edit { putInt(KEY_SCREEN_TIME_LIMIT, value) }
        }

    var isScreenTimeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_TIME_ENABLED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_SCREEN_TIME_ENABLED, value) }
        }

    var isVpnFilterEnabled: Boolean
        // ── VPN / DNS Filter ─────────────────────────────────────────────────────
        get() = prefs.getBoolean(KEY_VPN_FILTER, false)
        set(value) {
            prefs.edit { putBoolean(KEY_VPN_FILTER, value) }
        }

    var lastVpnHeartbeatType: String?
        // ── VPN Heartbeat ─────────────────────────────────────────────────────────
        get() = prefs.getString(KEY_LAST_HEARTBEAT, "")
        set(value) {
            prefs.edit { putString(KEY_LAST_HEARTBEAT, value) }
        }

    var lastVpnHeartbeatTimestamp: Long
        get() = prefs.getLong(KEY_LAST_HEARTBEAT_TS, 0L)
        set(value) {
            prefs.edit { putLong(KEY_LAST_HEARTBEAT_TS, value) }
        }

    var isKeepVpnAlive: Boolean
        // ── VPN Keep-Alive ────────────────────────────────────────────────────────
        get() = prefs.getBoolean(KEY_KEEP_VPN_ALIVE, false)
        set(value) {
            prefs.edit { putBoolean(KEY_KEEP_VPN_ALIVE, value) }
        }

    var isPreventVpnOverride: Boolean
        // ── Prevent VPN Override ──────────────────────────────────────────────────
        get() = prefs.getBoolean(KEY_PREVENT_VPN_OVERRIDE, false)
        set(value) {
            prefs.edit { putBoolean(KEY_PREVENT_VPN_OVERRIDE, value) }
        }

    var isPremium: Boolean
        // ── Premium ───────────────────────────────────────────────────────────────
        get() = prefs.getBoolean(KEY_IS_PREMIUM, false)
        set(value) {
            prefs.edit { putBoolean(KEY_IS_PREMIUM, value) }
        }

    var premiumExpiryEpoch: Long
        get() = prefs.getLong(KEY_PREMIUM_EXPIRY, 0L)
        set(value) {
            prefs.edit { putLong(KEY_PREMIUM_EXPIRY, value) }
        }

    var userUid: String?
        get() = prefs.getString(KEY_USER_UID, null)
        set(value) {
            prefs.edit { putString(KEY_USER_UID, value) }
        }

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) {
            prefs.edit { putString(KEY_USER_EMAIL, value) }
        }

    var isSafeSearchEnabled: Boolean
        // Add to PrefsManager.java
        get() = prefs.getBoolean("safe_search_enabled", false)
        set(value) {
            prefs.edit { putBoolean("safe_search_enabled", value) }
        }

    fun validatePin(enteredPin: String?): Boolean {
        val savedPin = this.pin // Assumes getPin() already exists
        return enteredPin != null && enteredPin == savedPin
    }

    var isYoutubeFilterEnabled: Boolean
        get() = prefs.getBoolean("youtube_filter_enabled", false)
        set(value) {
            prefs.edit { putBoolean("youtube_filter_enabled", value) }
        }

    var isBlockUnsupportedBrowsers: Boolean
        get() = prefs.getBoolean("block_unsupported_browsers", false)
        set(value) {
            prefs.edit { putBoolean("block_unsupported_browsers", value) }
        }

    var isStrictModeEnabled: Boolean
        get() = prefs.getBoolean("website_strict_mode_enabled", false)
        set(value) {
            prefs.edit { putBoolean("website_strict_mode_enabled", value) }
        }

    var strictModeUntil: Long
        get() = prefs.getLong("website_strict_mode_until", 0L)
        set(value) {
            prefs.edit { putLong("website_strict_mode_until", value) }
        }

    var isOnlineAdultCheckEnabled: Boolean
        get() = prefs.getBoolean("online_adult_check_enabled", false)
        set(value) {
            prefs.edit { putBoolean("online_adult_check_enabled", value) }
        }

    var isAutoHealEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_HEAL_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_AUTO_HEAL_ENABLED, value) }
        }

    var lockUntil: Long
        get() = prefs.getLong(KEY_LOCK_UNTIL_TIMESTAMP, 0L)
        set(epochMs) {
            prefs.edit { putLong(KEY_LOCK_UNTIL_TIMESTAMP, epochMs) }
        }


    var isAntiUninstallEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANTI_UNINSTALL_ENABLED, false)
        set(value) {
            prefs.edit {
                putBoolean(KEY_ANTI_UNINSTALL_ENABLED, value)
            }
        }


    var lockDurationMs: Long
        get() = prefs.getLong(KEY_LOCK_DURATION_MS, 0L)
        // ── Lock Anchors ──────────────────────────────────────────────────────────────
        set(value) {
            prefs.edit { putLong(KEY_LOCK_DURATION_MS, value) }
        }

    var lockAnchorElapsed: Long
        get() = prefs.getLong(KEY_LOCK_ANCHOR_ELAPSED, 0L)
        set(value) {
            prefs.edit { putLong(KEY_LOCK_ANCHOR_ELAPSED, value) }
        }

    var lockNtpOffset: Long
        get() = prefs.getLong(
            KEY_LOCK_NTP_OFFSET,
            Long.MIN_VALUE
        )
        set(value) {
            prefs.edit { putLong(KEY_LOCK_NTP_OFFSET, value) }
        }

    var lastKnownDeviceTime: Long
        get() = prefs.getLong(KEY_LAST_KNOWN_DEVICE_TIME, 0L)
        set(value) {
            prefs.edit { putLong(KEY_LAST_KNOWN_DEVICE_TIME, value) }
        }

    val isSettingsLocked: Boolean
        get() {
            val now = System.currentTimeMillis()
            val duration = this.lockDurationMs
            if (duration <= 0) return false

            // Method A: ElapsedRealtime (tamper-proof within boot)
            val anchorElapsed = this.lockAnchorElapsed
            if (anchorElapsed > 0) {
                val elapsed = SystemClock.elapsedRealtime() - anchorElapsed
                if (elapsed < duration) return true
            }

            // Method B: NTP-adjusted time
            val ntpOffset = this.lockNtpOffset
            if (ntpOffset != Long.MIN_VALUE) {
                val ntpNow = now + ntpOffset
                val ntpUnlockAt =
                    this.lockUntil // reuse existing key as NTP-based unlock epoch
                if (ntpNow < ntpUnlockAt) return true
            }

            // Method C: Tamper detection — clock jumped backward
            val lastKnown = this.lastKnownDeviceTime
            if (lastKnown > 0 && now < lastKnown - 60000L) {
                // Clock went back more than 1 min → extend lock by saving new anchor
                this.lockAnchorElapsed = SystemClock.elapsedRealtime()
                this.lastKnownDeviceTime = now
                return true
            }

            // Update last known time on every check
            if (lastKnown > 0) this.lastKnownDeviceTime = now

            return false
        }

    init {
        this.prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    var isPermissionBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_BLOCK_ENABLED, false)
        set(enabled) {
            prefs.edit {
                putBoolean(KEY_PERMISSION_BLOCK_ENABLED, enabled)
            }
        }

    fun clearLock() {
        prefs.edit {
            remove(KEY_LOCK_UNTIL_TIMESTAMP)
                .remove(KEY_LOCK_DURATION_MS)
                .remove(KEY_LOCK_ANCHOR_ELAPSED)
                .remove(KEY_LOCK_NTP_OFFSET)
                .remove(KEY_LAST_KNOWN_DEVICE_TIME)
        }
    }

    var isPrivateDnsEnabled: Boolean
        get() = prefs.getBoolean(KEY_PRIVATE_DNS_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PRIVATE_DNS_ENABLED, value).apply()
        }

    var selectedPrivateDnsHostname: String
        get() = prefs.getString(KEY_SELECTED_PRIVATE_DNS, "dns.adguard.com") ?: "dns.adguard.com"
        set(value) {
            prefs.edit().putString(KEY_SELECTED_PRIVATE_DNS, value).apply()
        }

    val DEFAULT_PRIVATE_DNS_HOSTS = setOf(
        "family-filter-dns.cleanbrowsing.org",
        "family.cloudflare-dns.com",
        "adult-filter-dns.cleanbrowsing.org",
        "dns.adguard.com",          // AdGuard
        "dns.quad9.net",            // Quad9
        "cloudflare-dns.com",       // Cloudflare
        "doh.cleanbrowsing.org",    // CleanBrowsing (Family Filter)
        "family-filter-dns.com"     // CleanBrowsing (Family)
    )
    var customPrivateDnsHostnames: MutableSet<String>
        get() {
            return prefs.getStringSet(KEY_CUSTOM_DNS_LIST, DEFAULT_PRIVATE_DNS_HOSTS)
                ?: DEFAULT_PRIVATE_DNS_HOSTS.toMutableSet()
        }
        set(value) {
            prefs.edit().putStringSet(KEY_CUSTOM_DNS_LIST, value).apply()
        }

    /** True if this hostname is one of the built-in defaults (can never be deleted). */
    fun isDefaultPrivateDnsHost(hostname: String): Boolean =
        DEFAULT_PRIVATE_DNS_HOSTS.contains(hostname)

    /**
     * Removes a user-added custom DNS hostname from the list.
     * Silently no-ops if the hostname is one of the built-in defaults —
     * defaults can never be deleted this way.
     */
    fun removeCustomPrivateDnsHostname(hostname: String) {
        if (isDefaultPrivateDnsHost(hostname)) return
        val current = customPrivateDnsHostnames.toMutableSet()
        if (current.remove(hostname)) {
            customPrivateDnsHostnames = current
        }
    }

    var isPrivateDnsLocked: Boolean
        get() = prefs.getBoolean(KEY_PRIVATE_DNS_LOCKED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_PRIVATE_DNS_LOCKED, value) }
        }


    var isBankingBypassEnabled: Boolean
        get() = prefs.getBoolean(KEY_BANKING_BYPASS_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_BANKING_BYPASS_ENABLED, value) }

    var bankingBypassPackage: String?
        get() = prefs.getString(KEY_BANKING_BYPASS_PACKAGE, null)
        set(value) = prefs.edit { putString(KEY_BANKING_BYPASS_PACKAGE, value) }

    var bankingBypassStartTime: Long
        get() = prefs.getLong(KEY_BANKING_BYPASS_START_TIME, 0L)
        set(value) = prefs.edit { putLong(KEY_BANKING_BYPASS_START_TIME, value) }

    companion object {
        // ── Keys ──────────────────────────────────────────────────────────────────
        private const val KEY_PIN = "parent_pin"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_BLOCK_SHORTS = "block_shorts"
        private const val KEY_BLOCK_PORN = "block_porn"
        private const val KEY_SAFE_SEARCH = "safe_search"
        private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
        private const val KEY_SCREEN_TIME_LIMIT = "screen_time_limit"
        private const val KEY_SCREEN_TIME_ENABLED = "screen_time_enabled"
        private const val KEY_VPN_FILTER = "vpn_filter_enabled"
        private const val KEY_KEEP_VPN_ALIVE = "keep_vpn_alive"
        private const val KEY_PREVENT_VPN_OVERRIDE = "prevent_vpn_override"
        private const val KEY_LAST_HEARTBEAT = "last_vpn_heartbeat_type"
        private const val KEY_LAST_HEARTBEAT_TS = "last_vpn_heartbeat_ts"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_PREMIUM_EXPIRY = "premium_expiry"
        private const val KEY_USER_UID = "user_uid"
        private const val KEY_USER_EMAIL = "user_email"

        private const val KEY_LOCK_UNTIL_TIMESTAMP = "lock_until_timestamp"


        private const val KEY_LOCK_DURATION_MS = "lock_duration_ms"
        private const val KEY_LOCK_ANCHOR_ELAPSED = "lock_anchor_elapsed"
        private const val KEY_LOCK_NTP_OFFSET = "lock_ntp_offset"
        private const val KEY_LAST_KNOWN_DEVICE_TIME = "last_known_device_time"
        private const val KEY_ANTI_UNINSTALL_ENABLED = "anti_uninstall_enabled"

        private const val KEY_PERMISSION_BLOCK_ENABLED = "permission_block_enabled"

        private const val KEY_PRIVATE_DNS_ENABLED = "private_dns_enabled"
        private const val KEY_SELECTED_PRIVATE_DNS = "selected_private_dns"
        private const val KEY_CUSTOM_DNS_LIST = "custom_dns_list"
        private const val KEY_PRIVATE_DNS_LOCKED = "private_dns_locked"
        private const val KEY_BLOCKED_WEBSITES = "blocked_websites"

        private const val KEY_BANKING_BYPASS_ENABLED = "banking_bypass_enabled"
        private const val KEY_BANKING_BYPASS_PACKAGE = "banking_bypass_package"
        private const val KEY_AUTO_HEAL_ENABLED = "auto_heal_enabled"
        private const val KEY_BANKING_BYPASS_START_TIME = "banking_bypass_start_time"
    }

    @Deprecated("Use DataStoreManager as single source of truth for blockedWebsites")
    var blockedWebsites: MutableSet<String>
        get() = prefs.getStringSet(KEY_BLOCKED_WEBSITES, emptySet()) ?: emptySet<String>().toMutableSet()
        set(value) {
            prefs.edit { putStringSet(KEY_BLOCKED_WEBSITES, value) }
        }

    @Deprecated("Use DataStoreManager.updateSettings as single source of truth")
    @Suppress("DEPRECATION")
    fun addBlockedWebsite(url: String) {
        val websites = blockedWebsites.toMutableSet()
        websites.add(url)
        blockedWebsites = websites
    }

    @Deprecated("Use DataStoreManager.updateSettings as single source of truth")
    @Suppress("DEPRECATION")
    fun removeBlockedWebsite(url: String) {
        val websites = blockedWebsites.toMutableSet()
        websites.remove(url)
        blockedWebsites = websites
    }
}