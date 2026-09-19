package com.curbme.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.curbme.app.data.local.db.dao.AppBlockDao
import com.curbme.app.data.local.db.dao.AppGroupDao
import com.curbme.app.data.local.db.dao.AppUsageDao
import com.curbme.app.data.local.db.dao.ReelStatsDao
import com.curbme.app.data.local.db.dao.ReelUsageStatsDao
import com.curbme.app.data.local.db.dao.UsageLogDao
import com.curbme.app.data.local.db.dao.WebsiteStatsDao
import com.curbme.app.data.local.db.entity.AppBlockRule
import com.curbme.app.data.local.db.entity.AppGroupEntity
import com.curbme.app.data.local.db.entity.AppUsageEntity
import com.curbme.app.data.local.db.entity.ReelStatsEntity
import com.curbme.app.data.local.db.entity.ReelUsageStatsEntity
import com.curbme.app.data.local.db.entity.UsageLogEntity
import com.curbme.app.data.local.db.dao.AdultDomainDao
import com.curbme.app.data.local.db.dao.FocusSessionDao
import com.curbme.app.data.local.db.entity.AdultDomainEntity
import com.curbme.app.data.local.db.entity.FocusSessionEntity
import com.curbme.app.data.local.db.entity.WebsiteStatsEntity
import com.curbme.app.service.vpn.heartbeat.VpnHeartBeatDao
import com.curbme.app.service.vpn.heartbeat.VpnHeartBeatEntity
import kotlin.concurrent.Volatile

/**
 * The sole Database class for the application.
 * All entities (UsageLogs, Heartbeats, ReelStats, WebsiteStats, AppGroups, etc.) are registered here.
 */
@Database(
    entities = [
        UsageLogEntity::class,
        VpnHeartBeatEntity::class,
        AppBlockRule::class,
        AppUsageEntity::class,
        ReelStatsEntity::class,
        ReelUsageStatsEntity::class,
        WebsiteStatsEntity::class,
        AppGroupEntity::class,
        AdultDomainEntity::class,
        FocusSessionEntity::class
    ],
    version = 12,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageLogDao(): UsageLogDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun vpnHeartBeatDao(): VpnHeartBeatDao
    abstract fun appBlockDao(): AppBlockDao
    abstract fun reelStatsDao(): ReelStatsDao
    abstract fun reelUsageStatsDao(): ReelUsageStatsDao
    abstract fun websiteStatsDao(): WebsiteStatsDao
    abstract fun appGroupDao(): AppGroupDao
    abstract fun adultDomainDao(): AdultDomainDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `focus_sessions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`totalDurationSeconds` INTEGER NOT NULL, " +
                    "`startTimeMs` INTEGER NOT NULL, " +
                    "`plannedEndTimeMs` INTEGER NOT NULL, " +
                    "`elapsedTimeMs` INTEGER NOT NULL, " +
                    "`lastUpdatedTimeMs` INTEGER NOT NULL, " +
                    "`bootCount` INTEGER NOT NULL, " +
                    "`startElapsedRealtimeMs` INTEGER NOT NULL, " +
                    "`allowedPackageNames` TEXT)"
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `ntpOffsetMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `ntpBootCount` INTEGER NOT NULL DEFAULT -1")
            }
        }

        /**
         * Standard Singleton pattern to provide access to the database.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(AppDatabase::class.java) {
                INSTANCE ?: databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database",
                )
                    .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
                    .enableMultiInstanceInvalidation()
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
                    .build().also { INSTANCE = it }
            }
        }

        /**
         * Emergency utility to wipe the database files from disk.
         * Used to fix "Integrity Hash" crashes without uninstallation.
         */
        fun deleteDatabaseFile(context: Context) {
            INSTANCE?.close()
            INSTANCE = null
            context.deleteDatabase("app_database")
        }
    }
}
