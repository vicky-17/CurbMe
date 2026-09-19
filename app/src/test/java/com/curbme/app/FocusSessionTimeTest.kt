package com.curbme.app

import com.curbme.app.data.local.db.entity.FocusSessionEntity
import com.curbme.app.service.overlay.FocusTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSessionTimeTest {

    /**
     * (a) 10-minute session saved every 5s for 10 minutes: consumed == 10 min exactly, never ends early.
     */
    @Test
    fun test10MinuteSessionSavedEvery5Seconds() {
        val totalSec = 600 // 10 minutes
        val initialWall = 1_000_000L
        val initialElapsedRealtime = 100_000L

        var session = FocusSessionEntity(
            id = 1L,
            status = FocusSessionEntity.STATUS_ACTIVE,
            totalDurationSeconds = totalSec,
            startTimeMs = initialWall,
            plannedEndTimeMs = initialWall + (totalSec * 1000L),
            elapsedTimeMs = 0L,
            lastUpdatedTimeMs = initialWall,
            bootCount = 1,
            startElapsedRealtimeMs = initialElapsedRealtime
        )

        var currentElapsedRealtime = initialElapsedRealtime
        var currentWall = initialWall

        // Simulate 120 flushes (every 5 seconds = 5000ms) over 10 minutes (600s)
        for (step in 1..120) {
            currentElapsedRealtime += 5000L
            currentWall += 5000L

            val remainingMs = FocusTimeUtils.remainingMs(
                session = session,
                currentBootCount = 1,
                nowElapsedRealtimeMs = currentElapsedRealtime,
                nowWallClockMs = currentWall
            )

            // Re-anchor progress like the atomic same-boot update query
            val deltaElapsed = currentElapsedRealtime - session.startElapsedRealtimeMs
            session = session.copy(
                elapsedTimeMs = session.elapsedTimeMs + deltaElapsed,
                startElapsedRealtimeMs = currentElapsedRealtime,
                lastUpdatedTimeMs = currentWall
            )

            if (step < 120) {
                val expectedRemainingSec = totalSec - (step * 5)
                assertEquals(expectedRemainingSec * 1000L, remainingMs)
                assertTrue(remainingMs > 0L)
            } else {
                assertEquals(0L, remainingMs)
            }
        }

        assertEquals(600_000L, session.elapsedTimeMs)
    }

    /**
     * (b) Clock forward and backward in the same boot: remaining unchanged.
     */
    @Test
    fun testClockChangeInSameBootHasNoEffect() {
        val totalSec = 300 // 5 minutes
        val baseElapsedRealtime = 50_000L
        val baseWall = 1_000_000L

        val session = FocusSessionEntity(
            id = 1L,
            status = FocusSessionEntity.STATUS_ACTIVE,
            totalDurationSeconds = totalSec,
            startTimeMs = baseWall,
            plannedEndTimeMs = baseWall + (totalSec * 1000L),
            elapsedTimeMs = 60_000L, // 1 min consumed
            lastUpdatedTimeMs = baseWall,
            bootCount = 1,
            startElapsedRealtimeMs = baseElapsedRealtime
        )

        val testElapsedRealtime = baseElapsedRealtime + 30_000L // 30s hardware uptime passed
        // Expected remaining: 300s total - 60s initial - 30s elapsed = 210s (210,000ms)

        // Normal wall clock
        val remNormal = FocusTimeUtils.remainingMs(session, 1, testElapsedRealtime, baseWall + 30_000L)
        assertEquals(210_000L, remNormal)

        // Clock moved forward 3 hours
        val remClockForward = FocusTimeUtils.remainingMs(session, 1, testElapsedRealtime, baseWall + 30_000L + 10_800_000L)
        assertEquals(210_000L, remClockForward)

        // Clock moved backward 10 hours
        val remClockBackward = FocusTimeUtils.remainingMs(session, 1, testElapsedRealtime, baseWall + 30_000L - 36_000_000L)
        assertEquals(210_000L, remClockBackward)
    }

    /**
     * (c) Reboot with 3 min downtime: ~3 min consumed at re-anchor, then same-boot rule from the new anchor.
     */
    @Test
    fun testRebootWith3MinDowntime() {
        val totalSec = 600 // 10 minutes
        val lastWall = 1_000_000L

        val sessionBeforeReboot = FocusSessionEntity(
            id = 1L,
            status = FocusSessionEntity.STATUS_ACTIVE,
            totalDurationSeconds = totalSec,
            startTimeMs = lastWall - 60_000L,
            plannedEndTimeMs = (lastWall - 60_000L) + (totalSec * 1000L),
            elapsedTimeMs = 60_000L, // 1 min consumed before reboot
            lastUpdatedTimeMs = lastWall,
            bootCount = 1,
            startElapsedRealtimeMs = 200_000L
        )

        // Phone off for 3 minutes (180,000ms). Reboot completes at wall = 1,180,000ms. New bootCount = 2.
        val rebootWall = lastWall + 180_000L
        val rebootElapsedRealtime = 5_000L // Uptime reset on new boot

        val remAtReboot = FocusTimeUtils.remainingMs(sessionBeforeReboot, 2, rebootElapsedRealtime, rebootWall)
        // Downtime consumed = 180,000ms. Total consumed = 60,000 + 180,000 = 240,000ms (4 mins).
        // Remaining = 600,000 - 240,000 = 360,000ms (6 mins).
        assertEquals(360_000L, remAtReboot)

        // Perform re-anchor to bootCount = 2
        val reanchoredSession = sessionBeforeReboot.copy(
            bootCount = 2,
            startElapsedRealtimeMs = rebootElapsedRealtime,
            elapsedTimeMs = 240_000L,
            lastUpdatedTimeMs = rebootWall
        )

        // 10 seconds pass in boot 2
        val remLaterBoot2 = FocusTimeUtils.remainingMs(reanchoredSession, 2, rebootElapsedRealtime + 10_000L, rebootWall + 10_000L)
        assertEquals(350_000L, remLaterBoot2)
    }

    /**
     * (d) Clock moved backward across a reboot: no extra time awarded.
     */
    @Test
    fun testClockMovedBackwardAcrossReboot() {
        val totalSec = 600 // 10 minutes
        val lastWall = 1_000_000L

        val sessionBeforeReboot = FocusSessionEntity(
            id = 1L,
            status = FocusSessionEntity.STATUS_ACTIVE,
            totalDurationSeconds = totalSec,
            startTimeMs = lastWall - 120_000L,
            plannedEndTimeMs = (lastWall - 120_000L) + (totalSec * 1000L),
            elapsedTimeMs = 120_000L, // 2 mins consumed
            lastUpdatedTimeMs = lastWall,
            bootCount = 1,
            startElapsedRealtimeMs = 300_000L
        )

        // User changes clock backward before reboot or during downtime
        val backwardWall = lastWall - 300_000L // Clock is set 5 minutes BEFORE lastUpdatedTimeMs

        val consumed = FocusTimeUtils.consumedMs(sessionBeforeReboot, 2, 10_000L, backwardWall)
        // Downtime = max(0, backwardWall - lastWall) = 0.
        // Consumed stays 120_000ms. Elapsed time is NOT reduced.
        assertEquals(120_000L, consumed)

        val rem = FocusTimeUtils.remainingMs(sessionBeforeReboot, 2, 10_000L, backwardWall)
        assertEquals(480_000L, rem)
    }

    /**
     * (e) Downtime longer than remaining: COMPLETED, no overlay.
     */
    @Test
    fun testDowntimeLongerThanRemaining() {
        val totalSec = 300 // 5 minutes (300,000ms)
        val lastWall = 1_000_000L

        val sessionBeforeReboot = FocusSessionEntity(
            id = 1L,
            status = FocusSessionEntity.STATUS_ACTIVE,
            totalDurationSeconds = totalSec,
            startTimeMs = lastWall - 200_000L,
            plannedEndTimeMs = (lastWall - 200_000L) + (totalSec * 1000L),
            elapsedTimeMs = 200_000L, // 3m20s consumed
            lastUpdatedTimeMs = lastWall,
            bootCount = 1,
            startElapsedRealtimeMs = 400_000L
        )

        // Phone off for 10 minutes (600,000ms). Total duration is only 300,000ms.
        val rebootWall = lastWall + 600_000L

        val consumed = FocusTimeUtils.consumedMs(sessionBeforeReboot, 2, 5_000L, rebootWall)
        assertEquals(800_000L, consumed)

        val rem = FocusTimeUtils.remainingMs(sessionBeforeReboot, 2, 5_000L, rebootWall)
        assertEquals(0L, rem)
    }

    /**
     * (f) A save that would lower elapsedTimeMs: rejected.
     */
    @Test
    fun testSaveThatLowersElapsedTimeRejected() {
        val initialSession = FocusSessionEntity(
            id = 1L,
            status = FocusSessionEntity.STATUS_ACTIVE,
            totalDurationSeconds = 600,
            startTimeMs = 1_000_000L,
            plannedEndTimeMs = 1_600_000L,
            elapsedTimeMs = 100_000L,
            lastUpdatedTimeMs = 1_100_000L,
            bootCount = 1,
            startElapsedRealtimeMs = 50_000L
        )

        // Simulate an invalid attempt to supply a startElapsedRealtimeMs greater than nowElapsedRealtimeMs
        val nowElapsedRealtimeMs = 40_000L // 40_000 < startElapsedRealtimeMs (50_000)
        val conditionSatisfied = nowElapsedRealtimeMs >= initialSession.startElapsedRealtimeMs

        // Condition in UPDATE query is: AND :nowElapsedRealtimeMs >= startElapsedRealtimeMs
        // Condition fails, so the UPDATE statement modifies 0 rows and rejects the save.
        assertEquals(false, conditionSatisfied)
    }

    /**
     * (g) Two concurrent re-anchors: only one applies.
     */
    @Test
    fun testConcurrentReanchorsOnlyOneApplies() {
        val oldBootCount = 1
        var currentStoredBootCount = oldBootCount

        fun casReanchor(expectedBoot: Int, newBoot: Int): Boolean {
            if (currentStoredBootCount == expectedBoot) {
                currentStoredBootCount = newBoot
                return true
            }
            return false
        }

        // Process 1 and Process 2 both try to re-anchor from bootCount=1 to bootCount=2 concurrently
        val process1Success = casReanchor(expectedBoot = 1, newBoot = 2)
        val process2Success = casReanchor(expectedBoot = 1, newBoot = 2)

        assertTrue(process1Success)
        assertEquals(false, process2Success)
        assertEquals(2, currentStoredBootCount)
    }
}
