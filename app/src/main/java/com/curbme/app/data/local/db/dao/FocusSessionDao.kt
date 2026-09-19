package com.curbme.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.curbme.app.data.local.db.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Transaction
    suspend fun insertIfNoActiveSession(session: FocusSessionEntity): Long {
        val active = getActiveSession()
        if (active != null) {
            return -1L
        }
        return insertSession(session)
    }

    @Update
    suspend fun updateSession(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    suspend fun getActiveSession(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    fun getActiveSessionFlow(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions ORDER BY id DESC")
    suspend fun getAllSessions(): List<FocusSessionEntity>

    /**
     * Same-boot atomic progress update.
     * Advances elapsedTimeMs by (nowElapsedRealtimeMs - startElapsedRealtimeMs) and re-anchors
     * startElapsedRealtimeMs to nowElapsedRealtimeMs in ONE atomic operation.
     * Prevents double counting and ensures elapsedTimeMs never decreases.
     */
    @Query("UPDATE focus_sessions SET elapsedTimeMs = elapsedTimeMs + (:nowElapsedRealtimeMs - startElapsedRealtimeMs), startElapsedRealtimeMs = :nowElapsedRealtimeMs, lastUpdatedTimeMs = :nowWallClockMs WHERE id = :sessionId AND status = 'ACTIVE' AND bootCount = :currentBootCount AND :nowElapsedRealtimeMs >= startElapsedRealtimeMs")
    suspend fun updateProgressSameBoot(
        sessionId: Long,
        currentBootCount: Int,
        nowElapsedRealtimeMs: Long,
        nowWallClockMs: Long
    ): Int

    /**
     * Compare-and-Set re-anchor for device reboots.
     * Atomically sets new bootCount, startElapsedRealtimeMs, elapsedTimeMs, and lastUpdatedTimeMs
     * ONLY if bootCount still equals expectedOldBootCount.
     * Ensures process-safe idempotency between main and :guardian processes.
     */
    @Query("UPDATE focus_sessions SET bootCount = :newBootCount, startElapsedRealtimeMs = :nowElapsedRealtimeMs, elapsedTimeMs = :newElapsedTimeMs, lastUpdatedTimeMs = :nowWallClockMs, ntpOffsetMs = :ntpOffsetMs, ntpBootCount = :ntpBootCount WHERE id = :sessionId AND status = 'ACTIVE' AND bootCount = :expectedOldBootCount")
    suspend fun reanchorRebootCas(
        sessionId: Long,
        expectedOldBootCount: Int,
        newBootCount: Int,
        nowElapsedRealtimeMs: Long,
        newElapsedTimeMs: Long,
        nowWallClockMs: Long,
        ntpOffsetMs: Long,
        ntpBootCount: Int
    ): Int

    @Query("UPDATE focus_sessions SET ntpOffsetMs = :ntpOffsetMs, ntpBootCount = :ntpBootCount WHERE id = :sessionId AND status = 'ACTIVE'")
    suspend fun updateNtpOffset(
        sessionId: Long,
        ntpOffsetMs: Long,
        ntpBootCount: Int
    ): Int

    @Query("UPDATE focus_sessions SET status = 'COMPLETED', elapsedTimeMs = totalDurationSeconds * 1000, lastUpdatedTimeMs = :nowWallClockMs WHERE id = :sessionId AND status = 'ACTIVE'")
    suspend fun markCompletedById(sessionId: Long, nowWallClockMs: Long): Int

    @Query("UPDATE focus_sessions SET status = 'CANCELLED_BY_PAYMENT', lastUpdatedTimeMs = :nowWallClockMs WHERE id = :sessionId AND status = 'ACTIVE'")
    suspend fun markCancelledByPaymentById(sessionId: Long, nowWallClockMs: Long): Int

    @Query("DELETE FROM focus_sessions WHERE status != 'ACTIVE' AND id NOT IN (SELECT id FROM focus_sessions WHERE status != 'ACTIVE' ORDER BY lastUpdatedTimeMs DESC LIMIT :keepCount)")
    suspend fun pruneFinishedSessions(keepCount: Int = 20)

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAllSessions()
}
