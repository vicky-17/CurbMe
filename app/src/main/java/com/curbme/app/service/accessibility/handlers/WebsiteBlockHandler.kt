package com.curbme.app.service.accessibility.handlers

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.curbme.app.core.utils.KeywordMatcher
import com.curbme.app.data.local.prefs.Settings
import com.curbme.app.service.accessibility.detectors.BrowserUrlReader
import com.curbme.app.ui.block.BlockedPageActivity

/**
 * Handles real-time accessibility-based website and URL path blocking.
 */
class WebsiteBlockHandler(private val context: Context) {

    private var lastBlockedTarget: String = ""
    private var blockSuppressedUntil: Long = 0L

    fun handle(
        rootNode: AccessibilityNodeInfo?,
        packageName: String,
        settings: Settings,
        performGlobalAction: (Int) -> Boolean
    ): Boolean {
        if (packageName.isBlank()) return false
        val siteInfo = BrowserUrlReader.readSiteInfo(rootNode, packageName) ?: return false

        val isBlocked = isWebsiteBlocked(siteInfo.domain, siteInfo.urlIdentifier, settings)
        if (!isBlocked) return false

        val now = SystemClock.elapsedRealtime()
        if (siteInfo.urlIdentifier == lastBlockedTarget && now < blockSuppressedUntil) {
            return true
        }

        lastBlockedTarget = siteInfo.urlIdentifier
        blockSuppressedUntil = now + 5000L

        Log.w("WebsiteBlockHandler", "🚫 Website blocked via Accessibility: ${siteInfo.urlIdentifier}")

        // Navigate away via BACK then HOME and show block page
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

        try {
            val intent = BlockedPageActivity.websiteBlock(context, siteInfo.domain)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("WebsiteBlockHandler", "Failed to launch BlockedPageActivity", e)
        }

        return true
    }

    private fun isWebsiteBlocked(domain: String, urlIdentifier: String, settings: Settings): Boolean {
        if (settings.blockedWebsites.isEmpty()) return false

        // 1. Direct domain or URL match
        if (settings.blockedWebsites.contains(domain) || settings.blockedWebsites.contains(urlIdentifier)) {
            return true
        }

        // 2. KeywordMatcher pattern match (supports wildcards *.domain.com, paths /shorts, etc.)
        return KeywordMatcher.isMatch(settings.blockedWebsites, urlIdentifier)
    }
}
