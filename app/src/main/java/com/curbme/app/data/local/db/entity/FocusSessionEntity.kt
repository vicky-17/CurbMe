package com.curbme.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a Focus Mode session persisted in Room.
 * Ensures focus session survives app kills, recents swipes, and device reboots.
 */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val status: String = STATUS_ACTIVE,
    val totalDurationSeconds: Int,
    val startTimeMs: Long,
    val plannedEndTimeMs: Long,
    val elapsedTimeMs: Long = 0L,
    val lastUpdatedTimeMs: Long = startTimeMs,
    val bootCount: Int = -1,
    val startElapsedRealtimeMs: Long = 0L,
    val ntpOffsetMs: Long = 0L,
    val ntpBootCount: Int = -1,
    // TODO: Extension point for allowed apps passthrough in Focus Mode (e.g. comma-separated package names or JSON array)
    val allowedPackageNames: String? = null
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CANCELLED_BY_PAYMENT = "CANCELLED_BY_PAYMENT"
    }
}
