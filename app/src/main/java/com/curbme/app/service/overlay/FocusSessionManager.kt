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
import com.curbme.app.data.local.prefs.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Single source of truth for Focus Mode sessions.
 * Handles persistent storage, re-anchoring across reboots, monotonic hardware uptime calculations,
 * NTP validation, and auto-recovery across processes and startup points.
 */
object FocusSessionManager {
    private const val TAG = "FocusSessionManager"

    /**
     * Attempts to start a new Focus session.
     * Rejects if an ACTIVE session already exists in the database.
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

        val insertedId = dao.insertSession(newSession)
        val createdSession = newSession.copy(id = insertedId)
        Log.i(TAG, "Created new Focus session: id=$insertedId, duration=${durationSeconds}s, bootCount=$currentBootCount")
        return@withContext createdSession
    }

    /**
     * Fetches NTP time off the main thread ONLY if an active session exists AND
     * the current system boot count differs from the session's stored boot count.
     */
    suspend fun fetchAndUpdateNtpOffset(context: Context) = withContext(Dispatchers.IO) {
        if (!isStorageUnlocked(context)) return@withContext
        val activeSession = getActiveSession(context) ?: return@withContext
        val currentBoot = getBootCount(context)

        // Only fetch NTP when an active session exists AND boot count differs (after a reboot)
        if (currentBoot != -1 && currentBoot != activeSession.bootCount) {
            val ntpTime = NtpFetcher.fetchNtpTime()
            if (ntpTime > 0) {
                val offset = ntpTime - System.currentTimeMillis()
                PrefsManager(context).focusNtpOffset = offset
                Log.i(TAG, "NTP time fetched after reboot for active session ${activeSession.id}: ntpTime=$ntpTime, offsetMs=$offset")
            } else {
                Log.w(TAG, "NTP fetch unavailable after reboot — falling back to device wall clock")
            }
        }
    }

    /**
     * Re-anchors session in DB after a device reboot.
     * Computes consumed time using wall-clock (+ NTP offset if available),
     * then updates DB row with current boot count, new startElapsedRealtimeMs,
     * and updated elapsedTimeMs so same-boot monotonic calculation applies from then on.
     */
    suspend fun reanchorSessionAfterReboot(context: Context, session: FocusSessionEntity): Int = withContext(Dispatchers.IO) {
        if (!isStorageUnlocked(context)) return@withContext 0
        val dao = AppDatabase.getDatabase(context).focusSessionDao()
        val latestSession = dao.getActiveSession() ?: session

        val ntpOffset = PrefsManager(context).focusNtpOffset
        val wallNowMs = if (ntpOffset != 0L) System.currentTimeMillis() + ntpOffset else System.currentTimeMillis()
        val wallElapsedMs = maxOf(0L, wallNowMs - latestSession.lastUpdatedTimeMs)
        val newTotalElapsedMs = latestSession.elapsedTimeMs + wallElapsedMs

        val totalDurationMs = latestSession.totalDurationSeconds * 1000L
        val remainingMs = (totalDurationMs - newTotalElapsedMs).coerceAtLeast(0L)

        if (remainingMs <= 0L) {
            Log.i(TAG, "Session ${latestSession.id} expired during reboot recovery — marking COMPLETED")
            markCompleted(context, latestSession)
            return@withContext 0
        }

        val currentBoot = getBootCount(context)
        val currentElapsedRealtime = SystemClock.elapsedRealtime()
        val nowMs = System.currentTimeMillis()

        val reanchoredSession = latestSession.copy(
            bootCount = currentBoot,
            startElapsedRealtimeMs = currentElapsedRealtime,
            elapsedTimeMs = newTotalElapsedMs,
            lastUpdatedTimeMs = maxOf(nowMs, latestSession.lastUpdatedTimeMs)
        )
        dao.updateSession(reanchoredSession)
        Log.i(TAG, "Re-anchored Focus session ${session.id} after reboot: newBootCount=$currentBoot, elapsedTimeMs=$newTotalElapsedMs, remainingMs=$remainingMs")

        val remainingSec = (remainingMs / 1000L).toInt() + (if (remainingMs % 1000L > 0) 1 else 0)
        return@withContext remainingSec.coerceIn(0, session.totalDurationSeconds)
    }

    /**
     * Calculates remaining seconds for an active session with tamper protection.
     * 
     * Time Rule Logic:
     * 1. Same Boot (currentBootCount == session.bootCount):
     *    elapsed = session.elapsedTimeMs + (currentElapsedRealtime - session.startElapsedRealtimeMs)
     *    remaining = totalDuration - elapsed
     *    Changing system clock forward or backward in same boot has 0 effect on remaining time.
     * 2. After Reboot (currentBootCount != session.bootCount):
     *    Computes elapsed from wall-clock (+ NTP offset if available), then re-anchors DB row
     *    to current boot count and current elapsedRealtime.
     */
    fun calculateRemainingSeconds(context: Context, session: FocusSessionEntity): Int {
        val currentBootCount = getBootCount(context)
        val currentElapsedRealtimeMs = SystemClock.elapsedRealtime()

        if (currentBootCount != -1 && currentBootCount == session.bootCount) {
            // Same Boot: Monotonic hardware uptime calculation
            val realtimeElapsed = (currentElapsedRealtimeMs - session.startElapsedRealtimeMs).coerceAtLeast(0L)
            val totalElapsedMs = session.elapsedTimeMs + realtimeElapsed
            val totalMs = session.totalDurationSeconds * 1000L
            val remainingMs = (totalMs - totalElapsedMs).coerceAtLeast(0L)

            val remainingSeconds = (remainingMs / 1000L).toInt() + (if (remainingMs % 1000L > 0) 1 else 0)
            return remainingSeconds.coerceIn(0, session.totalDurationSeconds)
        } else {
            // After Reboot: Re-anchor asynchronously or synchronously
            var remainingSec = 0
            runBlocking(Dispatchers.IO) {
                remainingSec = reanchorSessionAfterReboot(context, session)
            }
            return remainingSec
        }
    }

    /**
     * Updates elapsed progress to the database off the main thread.
     * Always loads the latest row from DB before writing updates.
     */
    suspend fun updateProgress(context: Context, session: FocusSessionEntity, elapsedMs: Long) = withContext(Dispatchers.IO) {
        if (!isStorageUnlocked(context)) return@withContext
        val dao = AppDatabase.getDatabase(context).focusSessionDao()
        val latestSession = dao.getActiveSession() ?: return@withContext
        if (latestSession.id != session.id) return@withContext

        val nowMs = System.currentTimeMillis()
        val updatedSession = latestSession.copy(
            elapsedTimeMs = maxOf(latestSession.elapsedTimeMs, elapsedMs),
            lastUpdatedTimeMs = maxOf(nowMs, latestSession.lastUpdatedTimeMs)
        )
        dao.updateSession(updatedSession)
        Log.d(TAG, "Progress updated for session ${latestSession.id}: elapsedTimeMs=${updatedSession.elapsedTimeMs}")
    }

    /**
     * Marks session COMPLETED and prunes old sessions.
     */
    suspend fun markCompleted(context: Context, session: FocusSessionEntity) = withContext(Dispatchers.IO) {
        if (!isStorageUnlocked(context)) return@withContext
        val dao = AppDatabase.getDatabase(context).focusSessionDao()
        val latestSession = dao.getActiveSession() ?: session
        val nowMs = System.currentTimeMillis()
        val completedSession = latestSession.copy(
            status = FocusSessionEntity.STATUS_COMPLETED,
            elapsedTimeMs = latestSession.totalDurationSeconds * 1000L,
            lastUpdatedTimeMs = maxOf(nowMs, latestSession.lastUpdatedTimeMs)
        )
        dao.updateSession(completedSession)
        dao.pruneFinishedSessions(20)
        Log.i(TAG, "Focus session ${completedSession.id} marked COMPLETED")
    }

    /**
     * Marks session CANCELLED_BY_PAYMENT (for future Play Billing integration).
     */
    suspend fun cancelSessionWithPayment(context: Context, session: FocusSessionEntity) = withContext(Dispatchers.IO) {
        if (!isStorageUnlocked(context)) return@withContext
        val dao = AppDatabase.getDatabase(context).focusSessionDao()
        val latestSession = dao.getActiveSession() ?: session
        val nowMs = System.currentTimeMillis()
        val cancelledSession = latestSession.copy(
            status = FocusSessionEntity.STATUS_CANCELLED_BY_PAYMENT,
            lastUpdatedTimeMs = maxOf(nowMs, latestSession.lastUpdatedTimeMs)
        )
        dao.updateSession(cancelledSession)
        dao.pruneFinishedSessions(20)
        Log.i(TAG, "Focus session ${cancelledSession.id} marked CANCELLED_BY_PAYMENT")
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
     * Safe for calling from any process or recovery point.
     */
    fun checkAndResumeActiveSession(context: Context, source: String = "Unknown") {
        if (!isStorageUnlocked(context)) {
            Log.i(TAG, "[$source] Device storage locked (direct boot) — skipping Focus session recovery")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch NTP offset ONLY if active session exists and boot count differs
                fetchAndUpdateNtpOffset(context)

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

                // Request service start. Duplicate protection is handled inside FocusModeOverlayService itself.
                FocusModeOverlayService.startFocusMode(context, remainingSeconds)
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

    private fun getBootCount(context: Context): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (e: Exception) {
            -1
        }
    }
}
