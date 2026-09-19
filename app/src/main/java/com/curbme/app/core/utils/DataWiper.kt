package com.curbme.app.core.utils

import android.content.Context
import android.util.Log
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.data.local.prefs.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

/**
 * Utility to perform a complete emergency wipe of all app settings,
 * database files, SharedPreferences, PINs, and local cache.
 */
object DataWiper {
    private const val TAG = "DataWiper"

    /**
     * Completely wipes all settings, databases, SharedPreferences, and cache, then exits the app process.
     */
    fun wipeAllDataAndExit(context: Context) {
        val activeSession = runBlocking(Dispatchers.IO) {
            try {
                AppDatabase.getDatabase(context).focusSessionDao().getActiveSession()
            } catch (_: Exception) {
                null
            }
        }

        if (activeSession != null) {
            Log.w(TAG, "Data wipe rejected — an active Focus session (ID=${activeSession.id}) is in progress.")
            return
        }

        try {
            Log.w(TAG, "Initiating complete data wipe...")

            // 1. Reset DataStore settings JSON to default
            runBlocking(Dispatchers.IO) {
                try {
                    DataStoreManager(context).resetAllSettings()
                } catch (e: Exception) {
                    Log.e(TAG, "Error resetting DataStore", e)
                }
            }

            // 2. Delete Room Database
            try {
                AppDatabase.deleteDatabaseFile(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting database file", e)
            }

            // 3. Clear PrefsManager & all SharedPreferences files
            try {
                PrefsManager(context).clearPin()
            } catch (_: Exception) {}

            val prefsNames = listOf(
                Constants.PREFS_NAME,
                "monk_prefs",
                "app_blocker_prefs",
                "AppPreferences",
                "blocklist_prefs",
                "vpn_prefs"
            )

            for (prefsName in prefsNames) {
                try {
                    context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing $prefsName", e)
                }
            }

            // 4. Delete Datastore folder & internal files
            try {
                val datastoreDir = File(context.filesDir, "datastore")
                if (datastoreDir.exists()) datastoreDir.deleteRecursively()

                context.cacheDir?.listFiles()?.forEach { file ->
                    if (file.exists()) file.deleteRecursively()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing cache/files", e)
            }

            Log.w(TAG, "Complete data wipe successful. Terminating process.")

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during data wipe", e)
        } finally {
            exitProcess(status = 0)
        }
    }
}
