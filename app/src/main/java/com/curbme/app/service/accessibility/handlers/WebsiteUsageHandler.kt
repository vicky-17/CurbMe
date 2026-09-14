package com.curbme.app.service.accessibility.handlers

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.curbme.app.core.utils.TimeUtils
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.entity.WebsiteStatsEntity
import com.curbme.app.service.accessibility.detectors.BrowserUrlReader
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId

/**
 * Handles website and URL identifier usage tracking across supported mobile browsers.
 */
class WebsiteUsageHandler(
    private val context: Context,
    private val onWebsiteObserved: (packageName: String, urlIdentifier: String) -> Unit = { _, _ -> }
) {

    private val websiteStatsDao = AppDatabase.getDatabase(context).websiteStatsDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var activeDomain: String? = null
    private var activeUrlIdentifier: String? = null
    private var activePackage: String? = null
    private var domainStartTimeMs: Long = 0L

    fun handleEvent(rootNode: AccessibilityNodeInfo?, packageName: String?, isTrackingEnabled: Boolean = true) {
        if (!isTrackingEnabled || packageName == null) {
            flushSession()
            return
        }

        val siteInfo = BrowserUrlReader.readSiteInfo(rootNode, packageName)
        if (siteInfo == null) {
            if (activePackage == packageName) {
                flushSession()
            }
            return
        }

        onWebsiteObserved(packageName, siteInfo.urlIdentifier)

        if (siteInfo.urlIdentifier != activeUrlIdentifier || packageName != activePackage) {
            flushSession()
            activeDomain = siteInfo.domain
            activeUrlIdentifier = siteInfo.urlIdentifier
            activePackage = packageName
            domainStartTimeMs = SystemClock.elapsedRealtime()
            saveInitialSession(siteInfo.domain, siteInfo.urlIdentifier, packageName)
        } else {
            // Periodic flush every 15 seconds
            val now = SystemClock.elapsedRealtime()
            if (now - domainStartTimeMs >= 15_000L) {
                flushSession()
                activeDomain = siteInfo.domain
                activeUrlIdentifier = siteInfo.urlIdentifier
                activePackage = packageName
                domainStartTimeMs = now
            }
        }
    }

    fun onDestroy() {
        flushSession()
        scope.cancel()
    }

    private data class HourSlice(val date: String, val hour: Int, val durationMs: Long)

    private fun splitByLocalHour(startMs: Long, endMs: Long): List<HourSlice> {
        if (endMs <= startMs) return emptyList()
        val zone = ZoneId.systemDefault()
        val slices = ArrayList<HourSlice>(2)
        var cursor = startMs
        while (cursor < endMs) {
            val current = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextHour = current.withMinute(0).withSecond(0).withNano(0)
                .plusHours(1).toInstant().toEpochMilli()
            val sliceEnd = minOf(endMs, nextHour.coerceAtLeast(cursor + 1))
            slices += HourSlice(
                date = TimeUtils.todayKey(),
                hour = current.hour,
                durationMs = sliceEnd - cursor
            )
            cursor = sliceEnd
        }
        return slices
    }

    private fun saveInitialSession(domain: String, urlIdentifier: String, pkg: String) {
        val today = TimeUtils.todayKey()
        val wallNow = System.currentTimeMillis()
        scope.launch {
            try {
                websiteStatsDao.insertIfAbsent(
                    WebsiteStatsEntity(
                        date = today,
                        packageName = pkg,
                        domain = domain,
                        urlIdentifier = urlIdentifier,
                        totalTime = 0L,
                        lastVisited = wallNow
                    )
                )
                websiteStatsDao.touch(today, pkg, urlIdentifier, wallNow)
            } catch (_: Exception) {}
        }
    }

    private fun flushSession() {
        val domain = activeDomain ?: return
        val identifier = activeUrlIdentifier ?: return
        val pkg = activePackage ?: return
        val startTime = domainStartTimeMs

        activeDomain = null
        activeUrlIdentifier = null
        activePackage = null
        domainStartTimeMs = 0L

        if (startTime <= 0L) return

        val nowElapsed = SystemClock.elapsedRealtime()
        val deltaMs = (nowElapsed - startTime).coerceIn(0L, 30_000L)
        if (deltaMs < 250L) return

        val wallNow = System.currentTimeMillis()
        val wallStart = (wallNow - deltaMs).coerceAtMost(wallNow)

        scope.launch {
            try {
                splitByLocalHour(wallStart, wallNow).forEach { slice ->
                    val entity = WebsiteStatsEntity(
                        date = slice.date,
                        packageName = pkg,
                        domain = domain,
                        urlIdentifier = identifier,
                        totalTime = 0L,
                        lastVisited = wallNow
                    )
                    websiteStatsDao.insertIfAbsent(entity)
                    websiteStatsDao.addTime(
                        slice.date,
                        pkg,
                        identifier,
                        slice.hour,
                        slice.durationMs,
                        wallNow
                    )
                }
            } catch (_: Exception) {}
        }
    }
}
