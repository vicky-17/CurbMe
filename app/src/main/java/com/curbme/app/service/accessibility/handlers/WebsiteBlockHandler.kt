package com.curbme.app.service.accessibility.handlers

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.curbme.app.BuildConfig
import com.curbme.app.core.utils.KeywordMatcher
import com.curbme.app.data.local.prefs.Settings
import com.curbme.app.service.accessibility.detectors.BrowserUrlReader
import com.curbme.app.ui.block.BlockedPageActivity
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles real-time accessibility-based website and URL path blocking.
 */
class WebsiteBlockHandler(private val context: Context) {

    private val suppressedTargets = ConcurrentHashMap<String, Long>()
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

        val isBlocked = isWebsiteBlocked(siteInfo.domain, siteInfo.urlIdentifier, settings)
        if (!isBlocked) return false

        val now = SystemClock.elapsedRealtime()

        // Periodically prune expired suppression targets
        if (now - lastPruneTime > PRUNE_INTERVAL_MS) {
            lastPruneTime = now
            suppressedTargets.entries.removeIf { it.value <= now }
        }

        val suppressedUntil = suppressedTargets[siteInfo.urlIdentifier]
        if (suppressedUntil != null && now < suppressedUntil) {
            return true
        }

        suppressedTargets[siteInfo.urlIdentifier] = now + SUPPRESSION_DURATION_MS

        Log.w(TAG, "🚫 Website blocked via Accessibility: ${siteInfo.urlIdentifier}")

        // Navigate away via BACK then HOME and show block page
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

        try {
            val intent = BlockedPageActivity.websiteBlock(context, siteInfo.domain)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BlockedPageActivity", e)
        }

        return true
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
