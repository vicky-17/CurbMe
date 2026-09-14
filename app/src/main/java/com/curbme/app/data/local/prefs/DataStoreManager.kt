package com.curbme.app.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import com.curbme.app.core.utils.Constants
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type

class SharedPreferencesMigration(private val context: Context) : DataMigration<Settings> {
    override suspend fun shouldMigrate(currentData: Settings): Boolean {
        // Simple heuristic: if pin is empty but SharedPreferences has it, migrate.
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return currentData.parentPin.isEmpty() && prefs.contains("parent_pin")
    }

    override suspend fun migrate(currentData: Settings): Settings {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return Settings(
            parentPin = prefs.getString("parent_pin", "") ?: "",
            isSetupComplete = prefs.getBoolean("setup_complete", false),
            isBlockShorts = prefs.getBoolean("block_shorts", false),
            isBlockPorn = prefs.getBoolean("block_porn", false),
            isSafeSearchEnabled = prefs.getBoolean("safe_search_enabled", false),
            isYoutubeFilterEnabled = prefs.getBoolean("youtube_filter_enabled", false),
            blockedPackages = prefs.getStringSet("blocked_packages", emptySet()) ?: emptySet(),
            dailyScreenTimeLimitMinutes = prefs.getInt("screen_time_limit", 0),
            isScreenTimeEnabled = prefs.getBoolean("screen_time_enabled", false),
            isVpnFilterEnabled = prefs.getBoolean("vpn_filter_enabled", false),
            lastVpnHeartbeatType = prefs.getString("last_vpn_heartbeat_type", ""),
            lastVpnHeartbeatTimestamp = prefs.getLong("last_vpn_heartbeat_ts", 0L),
            isKeepVpnAlive = prefs.getBoolean("keep_vpn_alive", false),
            isPreventVpnOverride = prefs.getBoolean("prevent_vpn_override", false),
            isPremium = prefs.getBoolean("is_premium", false),
            premiumExpiryEpoch = prefs.getLong("premium_expiry", 0),
            userUid = prefs.getString("user_uid", null),
            userEmail = prefs.getString("user_email", null),
            isAntiUninstallEnabled = prefs.getBoolean("anti_uninstall_enabled", false),
            isPermissionBlockEnabled = prefs.getBoolean("permission_block_enabled", false),
            isPrivateDnsEnabled = prefs.getBoolean("private_dns_enabled", false),
            selectedPrivateDnsHostname = prefs.getString("selected_private_dns", "dns.adguard.com") ?: "dns.adguard.com",
            isPrivateDnsLocked = prefs.getBoolean("private_dns_locked", false),
            blockedWebsites = prefs.getStringSet("blocked_websites", emptySet()) ?: emptySet()
        )
    }

    override suspend fun cleanUp() {
        // We could clear SharedPreferences here, but it's safer to keep it for a while.
    }
}

class GsonSerializer<T>(
    private val gson: Gson,
    private val type: Type,
    override val defaultValue: T
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T {
        return try {
            gson.fromJson(input.readBytes().decodeToString(), type) ?: defaultValue
        } catch (e: Exception) {
            e.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(gson.toJson(t).toByteArray())
    }
}

class DataStoreManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        @Volatile
        private var INSTANCE: DataStore<Settings>? = null

        fun getSettingsDataStore(context: Context): DataStore<Settings> {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MultiProcessDataStoreFactory.create(
                    serializer = GsonSerializer(
                        gson = Gson(),
                        type = Settings::class.java,
                        defaultValue = Settings()
                    ),
                    migrations = listOf(SharedPreferencesMigration(context)),
                    produceFile = { File(context.applicationContext.filesDir, "datastore/settings.json") }
                ).also { INSTANCE = it }
            }
        }
    }

    private val dataStore = getSettingsDataStore(context)

    val settings: Flow<Settings> = dataStore.data

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        val updated = dataStore.updateData { transform(it) }
        val prefs = PrefsManager(context)
        prefs.isPrivateDnsEnabled = updated.isPrivateDnsEnabled
        prefs.selectedPrivateDnsHostname = updated.selectedPrivateDnsHostname
        prefs.isPrivateDnsLocked = updated.isPrivateDnsLocked
    }

    suspend fun updateAppBlocked(packageName: String, isBlocked: Boolean) {
        dataStore.updateData { current ->
            val updated = current.blockedPackages.toMutableSet()
            if (isBlocked) updated.add(packageName) else updated.remove(packageName)
            current.copy(blockedPackages = updated)
        }
    }

    suspend fun setPermissionBlockEnabled(enabled: Boolean) {
        dataStore.updateData { it.copy(isPermissionBlockEnabled = enabled) }
    }

    suspend fun setPrivateDnsEnabled(enabled: Boolean) {
        val prefs = PrefsManager(context)
        prefs.isPrivateDnsEnabled = enabled
        dataStore.updateData { it.copy(isPrivateDnsEnabled = enabled) }
    }

    suspend fun setSelectedPrivateDnsHostname(hostname: String) {
        val prefs = PrefsManager(context)
        prefs.selectedPrivateDnsHostname = hostname
        dataStore.updateData { it.copy(selectedPrivateDnsHostname = hostname) }
    }

    suspend fun setPrivateDnsLocked(locked: Boolean) {
        val prefs = PrefsManager(context)
        prefs.isPrivateDnsLocked = locked
        dataStore.updateData { it.copy(isPrivateDnsLocked = locked) }
    }

    suspend fun resetAllSettings() {
        dataStore.updateData { Settings() }
    }

    suspend fun setAutoHealEnabled(enabled: Boolean) {
        dataStore.updateData { it.copy(isAutoHealEnabled = enabled) }
        val prefs = PrefsManager(context)
        prefs.isAutoHealEnabled = enabled
    }

    suspend fun setBankingBypassEnabled(enabled: Boolean, pkg: String? = null) {
        val now = if (enabled) System.currentTimeMillis() else 0L
        dataStore.updateData { 
            it.copy(
                isBankingBypassEnabled = enabled, 
                bankingBypassPackage = pkg,
                bankingBypassStartTime = now
            )
        }
        val prefs = PrefsManager(context)
        prefs.isBankingBypassEnabled = enabled
        prefs.bankingBypassPackage = pkg
        prefs.bankingBypassStartTime = now
    }

    suspend fun setSafeSearchEnabled(enabled: Boolean) {
        dataStore.updateData { it.copy(isSafeSearchEnabled = enabled) }
    }

    suspend fun setBlockShorts(enabled: Boolean) {
        dataStore.updateData { it.copy(isBlockShorts = enabled) }
        val prefs = PrefsManager(context)
        prefs.isBlockShorts = enabled
    }
}
