package com.curbme.app.service.overlay

import com.curbme.app.data.local.db.entity.FocusSessionEntity

/**
 * Pure, side-effect-free time calculations for Focus Sessions.
 * Contains zero database calls, no runBlocking, and no side effects.
 */
object FocusTimeUtils {

    /**
     * Calculates consumed time in milliseconds for a focus session given hardware uptime and wall clock time.
     *
     * Rules:
     * 1. Same boot (currentBootCount != -1 && currentBootCount == session.bootCount):
     *    consumed = session.elapsedTimeMs + max(0, nowElapsedRealtimeMs - session.startElapsedRealtimeMs)
     *    Wall clock shifts (forward or backward) in the same boot have 0 impact on consumed time.
     * 2. After reboot (currentBootCount != session.bootCount):
     *    downtime = max(0, effectiveWallNow - session.lastUpdatedTimeMs)
     *    consumed = session.elapsedTimeMs + downtime
     */
    fun consumedMs(
        session: FocusSessionEntity,
        currentBootCount: Int,
        nowElapsedRealtimeMs: Long,
        nowWallClockMs: Long,
        validNtpOffsetOrNull: Long? = null
    ): Long {
        if (session.status != FocusSessionEntity.STATUS_ACTIVE) {
            return session.totalDurationSeconds * 1000L
        }

        return if (currentBootCount != -1 && currentBootCount == session.bootCount) {
            // Same boot: hardware uptime (SystemClock.elapsedRealtime) is monotonic
            val realtimeElapsed = (nowElapsedRealtimeMs - session.startElapsedRealtimeMs).coerceAtLeast(0L)
            session.elapsedTimeMs + realtimeElapsed
        } else {
            // Reboot / Fallback: wall clock elapsed time
            val effectiveWallNow = if (validNtpOffsetOrNull != null) {
                nowWallClockMs + validNtpOffsetOrNull
            } else {
                nowWallClockMs
            }
            val wallDowntime = (effectiveWallNow - session.lastUpdatedTimeMs).coerceAtLeast(0L)
            session.elapsedTimeMs + wallDowntime
        }
    }

    /**
     * Calculates remaining time in milliseconds for a focus session.
     */
    fun remainingMs(
        session: FocusSessionEntity,
        currentBootCount: Int,
        nowElapsedRealtimeMs: Long,
        nowWallClockMs: Long,
        validNtpOffsetOrNull: Long? = null
    ): Long {
        val totalMs = session.totalDurationSeconds * 1000L
        val consumed = consumedMs(session, currentBootCount, nowElapsedRealtimeMs, nowWallClockMs, validNtpOffsetOrNull)
        return (totalMs - consumed).coerceAtLeast(0L)
    }

    /**
     * Calculates remaining seconds for UI display.
     * Ceilings to next second if remainingMs has fractional millisecond remaining.
     */
    fun remainingSeconds(
        session: FocusSessionEntity,
        currentBootCount: Int,
        nowElapsedRealtimeMs: Long,
        nowWallClockMs: Long,
        validNtpOffsetOrNull: Long? = null
    ): Int {
        val remMs = remainingMs(session, currentBootCount, nowElapsedRealtimeMs, nowWallClockMs, validNtpOffsetOrNull)
        if (remMs <= 0L) return 0
        val sec = (remMs / 1000L).toInt() + (if (remMs % 1000L > 0) 1 else 0)
        return sec.coerceIn(0, session.totalDurationSeconds)
    }
}
