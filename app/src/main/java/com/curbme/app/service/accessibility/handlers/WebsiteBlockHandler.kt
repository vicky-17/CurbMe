package com.curbme.app.service.accessibility.handlers

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.curbme.app.BuildConfig
import com.curbme.app.core.utils.KeywordMatcher
import com.curbme.app.core.utils.OnlineAdultChecker
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.entity.AdultDomainEntity
import com.curbme.app.data.local.prefs.Settings
import com.curbme.app.service.accessibility.detectors.BrowserUrlReader
import com.curbme.app.service.vpn.blocklist.PornDomainBlocklist
import com.curbme.app.ui.block.BlockedPageActivity
import kotlinx.coroutines.*
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles real-time accessibility-based website and URL path blocking.
 */
class WebsiteBlockHandler(private val context: Context) {

    private val suppressedTargets = ConcurrentHashMap<String, Long>()
    private val memoryCache = ConcurrentHashMap<String, Boolean>()
    private val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastPruneTime: Long = 0L

    fun handle(
        rootNode: AccessibilityNodeInfo?,
        packageName: String,
        settings: Settings,
        performGlobalAction: (Int) -> Boolean
    ): Boolean {
        if (packageName.isBlank()) return false
        if (BrowserUrlReader.isMereShortcutClick(rootNode, packageName)) return false
        val siteInfo = BrowserUrlReader.readSiteInfo(rootNode, packageName) ?: return false

        val normalizedDomain = siteInfo.domain.lowercase(Locale.ROOT).removePrefix("www.")

        // 1. Custom website blocklist
        if (isWebsiteBlocked(siteInfo.domain, siteInfo.urlIdentifier, settings)) {
            triggerBlock(siteInfo.domain, siteInfo.urlIdentifier, performGlobalAction)
            return true
        }

        // 2. Static porn domain blocklist
        if (settings.isBlockPorn && PornDomainBlocklist.isBlocked(normalizedDomain)) {
            triggerBlock(siteInfo.domain, siteInfo.urlIdentifier, performGlobalAction)
            return true
        }

        // 3. Online CleanBrowsing adult site check
        if (settings.isOnlineAdultCheckEnabled || settings.blockedWebsites.contains("adult_websites_all")) {
            checkOnlineAdultSiteAsync(siteInfo.domain, siteInfo.urlIdentifier, normalizedDomain, performGlobalAction)
        }

        return false
    }

    private fun checkOnlineAdultSiteAsync(
        rawDomain: String,
        urlIdentifier: String,
        normalizedDomain: String,
        performGlobalAction: (Int) -> Boolean
    ) {
        val cached = memoryCache[normalizedDomain]
        if (cached == true) {
            triggerBlock(rawDomain, urlIdentifier, performGlobalAction)
            return
        } else if (cached == false) {
            return
        }

        handlerScope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val dbEntity = db.adultDomainDao().get(normalizedDomain)
                if (dbEntity != null) {
                    memoryCache[normalizedDomain] = dbEntity.isAdult
                    if (dbEntity.isAdult) {
                        withContext(Dispatchers.Main) {
                            triggerBlock(rawDomain, urlIdentifier, performGlobalAction)
                        }
                    }
                    return@launch
                }

                // Unknown domain -> Query CleanBrowsing Adult DNS
                val isAdult = OnlineAdultChecker.isAdultSite(normalizedDomain)
                db.adultDomainDao().insert(AdultDomainEntity(domain = normalizedDomain, isAdult = isAdult))
                memoryCache[normalizedDomain] = isAdult

                if (isAdult) {
                    Log.w(TAG, "🚫 Online adult check flagged $normalizedDomain as adult content")
                    withContext(Dispatchers.Main) {
                        triggerBlock(rawDomain, urlIdentifier, performGlobalAction)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in online adult site check for $normalizedDomain", e)
            }
        }
    }

    private fun triggerBlock(
        domain: String,
        urlIdentifier: String,
        performGlobalAction: (Int) -> Boolean
    ) {
        val now = SystemClock.elapsedRealtime()

        if (now - lastPruneTime > PRUNE_INTERVAL_MS) {
            lastPruneTime = now
            suppressedTargets.entries.removeIf { it.value <= now }
        }

        val suppressedUntil = suppressedTargets[urlIdentifier]
        if (suppressedUntil != null && now < suppressedUntil) {
            return
        }

        suppressedTargets[urlIdentifier] = now + SUPPRESSION_DURATION_MS

        Log.w(TAG, "🚫 Website blocked via Accessibility: $urlIdentifier")

        performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

        try {
            val intent = BlockedPageActivity.websiteBlock(context, domain)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BlockedPageActivity", e)
        }
    }

    private fun isWebsiteBlocked(domain: String, urlIdentifier: String, settings: Settings): Boolean {
        if (settings.blockedWebsites.isEmpty()) return false
        val normalizedDomain = domain.lowercase(Locale.ROOT).removePrefix("www.")

        val isBlocked = settings.blockedWebsites.contains(domain) ||
                settings.blockedWebsites.contains(normalizedDomain) ||
                settings.blockedWebsites.contains(urlIdentifier) ||
                KeywordMatcher.isMatch(settings.blockedWebsites, urlIdentifier) ||
                KeywordMatcher.isMatch(settings.blockedWebsites, normalizedDomain)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Blocked-website check: raw=$domain, urlIdentifier=$urlIdentifier, normalized=$normalizedDomain, match=$isBlocked")
        }

        return isBlocked
    }

    companion object {
        private const val TAG = "WebsiteBlockHandler"
        private const val SUPPRESSION_DURATION_MS = 5000L
        private const val PRUNE_INTERVAL_MS = 30000L
    }
}
