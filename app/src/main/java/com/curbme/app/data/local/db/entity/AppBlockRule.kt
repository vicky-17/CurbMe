package com.curbme.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_block_rules")
data class AppBlockRule(
    @PrimaryKey val packageName: String,
    val appName: String,
    val iconPath: String? = null,
    val planType: String, // STAY_FOCUSED, TIME_LIMIT, HABIT_TRAINING, SCREEN_BREAK

    // Time Limit Fields
    val allowedMinutes: Int = 0,
    val intervalMinutes: Int = 0,

    // Train Habits Fields
    val maxLaunches: Int = 0,
    val maxTimePerLaunch: Int = 0,
    val inactivityTimeout: Int = 0,
    val minBreakBetween: Int = 0,

    // Screen Breaks Fields
    val breakLength: Int = 0,
    val breakEvery: Int = 0,
    val minBreakForSkip: Int = 0,

    // Interstitial / Regain Mode
    val useInterstitial: Boolean = false,
    val quickUseOptions: String = "2,5,10,20", // Stored as comma-separated string

    // App Settings
    val blockMethod: String = "SUSPEND", // SUSPEND, HIDE, KILL, GO_HOME
    val isBlacklist: Boolean = true,

    // Timing Mode
    val timingMode: String = "ON_DEMAND", // ON_DEMAND, WEEKLY, POMODORO, MULTI_DAY

    // Scheduling
    val activeDays: Int = 127, // Bitmask for MTWTFSS (127 = all days)
    val startTime: String? = null,
    val endTime: String? = null,
    val expiryTimestamp: Long = 0,

    // Plan Info
    val planName: String = "",
    val isAntiUninstallEnabled: Boolean = false,
    val stopChallengeType: String = "NONE", // NONE, RANDOM_CHARS, PIN
    val stopDelayMinutes: Int = 0
)