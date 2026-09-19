package com.curbme.app.service.overlay

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.curbme.app.core.utils.NtpFetcher
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.entity.FocusSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single source of truth for Focus Mode sessions.
 * Handles persistent storage, process-safe atomic CAS re-anchoring across reboots,
 * pure monotonic hardware uptime calculations, and auto-recovery across processes.
 */
object FocusSessionManager {
    private const val TAG = "FocusSessionManager"

    /**
     * Attempts to start a new Focus session.
     * Uses atomic insertIfNoActiveSession DAO query to prevent check-then-insert race conditions.
     */
    suspend fun startNewSession(context: Context, durationSeconds: Int): FocusSessionEntity? = withContext(Dispatchers.IO) {
        if (!isStorageUnlocked(context)) {
            Log.w(TAG, "Cannot start session — storage locked")
            return@withContext null
        }

        val dao = AppDatabase.getDatabase(context).focusSessionDao()
        val existingActive = dao.getActiveSession()
        if (existingActive != null) {
            val remainingSec = calculateRemainingSeconds(context, existingActive)
            if (remainingSec > 0) {
                Log.w(TAG, "Start new session rejected — Active session ${existingActive.id} exists with ${remainingSec}s remaining")
                return@withContext null
            } else {
                Log.i(TAG, "Previous session ${existingActive.id} expired — marking COMPLETED")
                markCompleted(context, existingActive)
            }
        }

        val currentBootCount = getBootCount(context)
        val nowMs = System.currentTimeMillis()
        val elapsedRealtimeMs = SystemClock.elapsedRealtime()

        val newSession = FocusSessionEntity(
            status = FocusSessionEntity.STATUS_ACTIVE,
            totalDurationSeconds = durationSeconds,
            startTimeMs = nowMs,
            plannedEndTimeMs = nowMs + (durationSeconds * 1000L),
            elapsedTimeMs = 0L,
            lastUpdatedTimeMs = nowMs,
            bootCount = currentBootCount,
            startElapsedRealtimeMs = elapsedRealtimeMs
        )

        val insertedId = dao.insertIfNoActiveSession(newSession)
        if (insertedId == -1L) {
            Log.w(TAG, "Start new session atomic insertion rejected — an active session was concurrently created")
            return@withContext null
        }

        val createdSession = newSession.copy(id = insertedId)
        Log.i(TAG, "Created new Focus session: id=$insertedId, duration=${durationSeconds}s, bootCount=$currentBootCount")
        return@withContext createdSession
    }

    /**
     * Fetches NTP time off the main thread if an active session exists.
     * Stores the NTP offset along with the current boot count on the session row.
     */
    suspend fun fetchAndUpdateNtpOffset(context: Context) = withContext(Dispatchers.IO) {
        if (!isStorageUnlocked(context)) return@withContext
        val dao = AppDatabase.getDatabase(context).focusSessionDao()
        val activeSession = dao.getActiveSession() ?: return@withContext
        val currentBoot = getBootCount(context)

        val ntpTime = NtpFetcher.fetchNtpTime()
        if (ntpTime > 0) {
            val offset = ntpTime - System.currentTimeMillis()
            dao.updateNtpOffset(activeSession.id, offset, currentBoot)
            Log.i(TAG, "NTP time fetched for active session ${activeSession.id}: ntpTime=$ntpTime, offsetMs=$offset, bootCount=$currentBoot")
        } else {
            Log.w(TAG, "NTP fetch unavailable — keeping existing session clock anchor")
        }
    }

    /**
     * Atomically re-anchors session in DB after a device reboot using compare-and-set on bootCount.
     * Prevents multi-process race conditions between main process and :guardian process.
     */
    suspend fun reanchorSessionAfterReboot(context: Context, session: FocusSessionEntity): Int = withContext(Dispatchers.IO) {
        if (!isStorageUnlocked(context)) return@withContext 0
        val dao = AppDatabase.getDatabase(context).focusSessionDao()
        val latestSession = dao.getActiveSession() ?: session

        val currentBoot = getBootCount(context)
        if (currentBoot == -1) {
            Log.w(TAG, "BOOT_COUNT unavailable (-1) — falling back to safe same-boot time calculation")
            return@withContext calculateRemainingSeconds(context, latestSession)
        }

        if (latestSession.bootCount == currentBoot) {
            // Already re-anchored by another process
            return@withContext calculateRemainingSeconds(context, latestSession)
        }

        val ntpOffsetOrNull = if (latestSession.ntpBootCount == currentBoot && latestSession.ntpOffsetMs != 0L) {
            latestSession.ntpOffsetMs
        } else null

        val nowWallMs = System.currentTimeMillis()
        val nowElapsedRealtimeMs = SystemClock.elapsedRealtime()

        val consumedMs = FocusTimeUtils.consumedMs(
            session = latestSession,
            currentBootCount = currentBoot,
            nowElapsedRealtimeMs = nowElapsedRealtimeMs,
            nowWallClockMs = nowWallMs,
            validNtpOffsetOrNull = ntpOffsetOrNull
        )

        val totalMs = latestSession.totalDurationSeconds * 1000L
        if (consumedMs >= totalMs) {
            Log.i(TAG, "Session ${latestSession.id} expired during reboot downtime — marking COMPLETED")
            markCompleted(context, latestSession)
            return@withContext 0
        }

        val rowsUpdated = dao.reanchorRebootCas(
            sessionId = latestSession.id,
            expectedOldBootCount = latestSession.bootCount,
            newBootCount = currentBoot,
            nowElapsedRealtimeMs = nowElapsedRealtimeMs,
            newElapsedTimeMs = consumedMs,
            nowWallClockMs = maxOf(nowWallMs, latestSession.lastUpdatedTimeMs),
            ntpOffsetMs = latestSession.ntpOffsetMs,
            ntpBootCount = latestSession.ntpBootCount
        )

        if (rowsUpdated > 0) {
            Log.i(TAG, "Atomically re-anchored Focus session ${latestSession.id} after reboot: newBootCount=$currentBoot, elapsedTimeMs=$consumedMs")
        } else {
            Log.i(TAG, "Re-anchor CAS on session ${latestSession.id} skipped — already updated by another process")
        }

        val updatedSession = dao.getActiveSession() ?: latestSession
        return@withContext FocusTimeUtils.remainingSeconds(updatedSession, currentBoot, SystemClock.elapsedRealtime(), System.currentTimeMillis())
    }

    /**
     * Calculates remaining seconds for an active session using pure FocusTimeUtils functions.
     * Pure and non-blocking. If reboot re-anchor is needed, schedules re-anchor asynchronously on IO dispatcher.
     */
    fun calculateRemainingSeconds(context: Context, session: FocusSessionEntity): Int {
        val currentBoot = getBootCount(context)
        val nowElapsedRealtimeMs = SystemClock.elapsedRealtime()
        val nowWallClockMs = System.currentTimeMillis()

        val validNtpOffsetOrNull = if (session.ntpBootCount == currentBoot && session.ntpOffsetMs != 0L) {
            session.ntpOffsetMs
        } else null

        if (currentBoot != -1 && session.bootCount != currentBoot) {
            // Asynchronously perform CAS re-anchor off the main thread
            CoroutineScope(Dispatchers.IO).launch {
                reanchorSessionAfterReboot(context, session)
            }
        }

        return FocusTimeUtils.remainingSeconds(
            session = session,
            currentBootCount = currentBoot,
            nowElapsedRealtimeMs = nowElapsedRealtimeMs,
            nowWallClockMs = nowWallClockMs,
            validNtpOffsetOrNull = validNtpOffsetOrNull
        )
    }

    /**
     * Marks session COMPLETED and prunes finished sessions.
     */
    suspend fun markCompleted(context: Context, session: FocusSessionEntity) = withContext(Dispatchers.IO) {
        if (!isStorageUnlocked(context)) return@withContext
        val dao = AppDatabase.getDatabase(context).focusSessionDao()
        val nowMs = System.currentTimeMillis()
        dao.markCompletedById(session.id, nowMs)
        dao.pruneFinishedSessions(20)
        Log.i(TAG, "Focus session ${session.id} marked COMPLETED")
    }

    /**
     * Marks session CANCELLED_BY_PAYMENT (for future Play Billing integration).
     */
    suspend fun cancelSessionWithPayment(context: Context, session: FocusSessionEntity) = withContext(Dispatchers.IO) {
        if (!isStorageUnlocked(context)) return@withContext
        val dao = AppDatabase.getDatabase(context).focusSessionDao()
        val nowMs = System.currentTimeMillis()
        dao.markCancelledByPaymentById(session.id, nowMs)
        dao.pruneFinishedSessions(20)
        Log.i(TAG, "Focus session ${session.id} marked CANCELLED_BY_PAYMENT")
    }

    /**
     * Queries current active session in database.
     */
    suspend fun getActiveSession(context: Context): FocusSessionEntity? = withContext(Dispatchers.IO) {
        if (!isStorageUnlocked(context)) {
            Log.d(TAG, "Storage locked — skipping active session query")
            return@withContext null
        }
        AppDatabase.getDatabase(context).focusSessionDao().getActiveSession()
    }

    /**
     * Checks database for an ACTIVE session and resumes the overlay if time remains.
     * Uses ACTION_RESUME_FOCUS — NEVER creates a new session.
     */
    fun checkAndResumeActiveSession(context: Context, source: String = "Unknown") {
        if (!isStorageUnlocked(context)) {
            Log.i(TAG, "[$source] Device storage locked (direct boot) — skipping Focus session recovery")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).focusSessionDao()
                val activeSession = dao.getActiveSession() ?: run {
                    Log.d(TAG, "[$source] Recovery check: No active Focus session found")
                    return@launch
                }

                val remainingSeconds = calculateRemainingSeconds(context, activeSession)
                Log.i(TAG, "[$source] Recovery check: Found active session ${activeSession.id} with ${remainingSeconds}s remaining")

                if (remainingSeconds <= 0) {
                    Log.i(TAG, "[$source] Active session ${activeSession.id} expired — marking COMPLETED")
                    markCompleted(context, activeSession)
                    return@launch
                }

                if (!Settings.canDrawOverlays(context)) {
                    Log.w(TAG, "[$source] Overlay permission missing — cannot show Focus Mode overlay")
                    return@launch
                }

                // Resume overlay via ACTION_RESUME_FOCUS (never passes duration, never creates new session)
                FocusModeOverlayService.resumeFocusMode(context)
                Log.i(TAG, "[$source] Triggered overlay resume for session ${activeSession.id}")
            } catch (e: Exception) {
                Log.e(TAG, "[$source] Error during Focus session recovery", e)
            }
        }
    }

    private fun isStorageUnlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = context.getSystemService(UserManager::class.java)
            if (userManager != null && !userManager.isUserUnlocked) {
                return false
            }
        }
        return true
    }

    fun getBootCount(context: Context): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (e: Exception) {
            -1
        }
    }
}
