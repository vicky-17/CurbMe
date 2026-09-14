package com.curbme.app.service.accessibility.detectors

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.curbme.app.BuildConfig
import com.curbme.app.core.utils.Constants.BrowserConstants
import java.net.URI
import java.util.Locale

/**
 * Extracts and normalizes domain names and full URL identifiers from browser address bar accessibility nodes.
 */
object BrowserUrlReader {

    private const val TAG = "BrowserUrlReader"

    data class SiteInfo(
        val domain: String,
        val urlIdentifier: String
    )

    private val BROWSER_URL_BAR_IDS: Map<String, List<String>> = mapOf(
        BrowserConstants.CHROME to listOf("url_bar", "location_bar"),
        "com.chrome.beta" to listOf("url_bar", "location_bar"),
        "com.chrome.canary" to listOf("url_bar", "location_bar"),
        "com.chrome.dev" to listOf("url_bar", "location_bar"),
        BrowserConstants.BRAVE to listOf("url_bar", "location_bar"),
        BrowserConstants.FIREFOX to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "url_bar_text"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.fenix" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.focus" to listOf("mozac_browser_toolbar_url_view"),
        BrowserConstants.EDGE to listOf("url_bar", "search_box"),
        BrowserConstants.SAMSUNG_INTERNET to listOf("location_bar_edit_text", "url_bar"),
        BrowserConstants.OPERA to listOf("url_field", "url_bar"),
        "com.opera.mini.native" to listOf("url_field", "url_bar"),
        "com.vivaldi.browser" to listOf("url_bar", "location_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar", "location_bar"),
        "com.duckduckgo.mobile.android" to listOf("omnibar_text_input", "search_box"),
        "org.cromite.cromite" to listOf("url_bar"),
        "app.vanadium.browser" to listOf("url_bar"),
        BrowserConstants.TOR to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "url_bar_text"),
        BrowserConstants.TOR_ALPHA to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "url_bar_text")
    )

    fun isSupportedBrowser(packageName: String): Boolean {
        return BROWSER_URL_BAR_IDS.containsKey(packageName)
    }

    /**
     * Extracts SiteInfo (domain and urlIdentifier) open in browser.
     */
    fun readSiteInfo(rootNode: AccessibilityNodeInfo?, packageName: String): SiteInfo? {
        if (rootNode == null) return null
        val urlBarIds = BROWSER_URL_BAR_IDS[packageName] ?: return null

        for (idName in urlBarIds) {
            val fullId = "$packageName:id/$idName"
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(fullId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val rawText = (node.text ?: node.contentDescription)?.toString()
                    @Suppress("DEPRECATION")
                    node.recycle()
                    if (!rawText.isNullOrBlank()) {
                        val siteInfo = extractSiteInfoFromText(rawText)
                        if (siteInfo != null) return siteInfo
                    }
                }
            }
        }

        // Generic fallback scan when known view IDs return nothing
        val fallbackSiteInfo = findUrlGenerically(rootNode)
        if (fallbackSiteInfo != null) return fallbackSiteInfo

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Failed to resolve URL bar for supported browser package: $packageName")
        }

        return null
    }

    private fun findUrlGenerically(node: AccessibilityNodeInfo?): SiteInfo? {
        if (node == null) return null

        val rawText = (node.text ?: node.contentDescription)?.toString()
        if (!rawText.isNullOrBlank()) {
            val siteInfo = extractSiteInfoFromText(rawText)
            if (siteInfo != null) return siteInfo
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findUrlGenerically(child)
            @Suppress("DEPRECATION")
            child.recycle()
            if (result != null) return result
        }

        return null
    }

    fun isOnChromeHome(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        val className = rootNode.className?.toString().orEmpty()
        if (className.contains("NtpActivity", ignoreCase = true) || className.contains("StartSurface", ignoreCase = true)) {
            return true
        }
        val ntpNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/search_box_text")
        if (!ntpNodes.isNullOrEmpty()) {
            ntpNodes.forEach { @Suppress("DEPRECATION") it.recycle() }
            return true
        }
        return false
    }

    fun isMereShortcutClick(rootNode: AccessibilityNodeInfo?, packageName: String): Boolean {
        if (packageName == BrowserConstants.CHROME) {
            if (isOnChromeHome(rootNode)) {
                val activeUrlBar = rootNode?.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
                if (activeUrlBar.isNullOrEmpty()) {
                    return true
                }
                activeUrlBar.forEach { @Suppress("DEPRECATION") it.recycle() }
            }
        }
        return false
    }

    /**
     * Legacy helper extracting only the domain.
     */
    fun readDomain(rootNode: AccessibilityNodeInfo?, packageName: String): String? {
        return readSiteInfo(rootNode, packageName)?.domain
    }

    private fun filterOutUrlFromPlainText(inputText: String): String? {
        val trimmed = inputText.trim()
        if (trimmed.startsWith("chrome://", ignoreCase = true) ||
            trimmed.startsWith("chrome-native://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true)
        ) {
            return null
        }

        val urlRegex = Regex(
            pattern = """(?:https?://|www\.)?[^\s<>\"']+""",
            option = RegexOption.IGNORE_CASE
        )

        for (match in urlRegex.findAll(inputText)) {
            val cleanUrl = match.value
                .trimStart('(', '[', '{')
                .trimEnd('.', ',', ')', ']', '}', '!', ';', ':')
            val uriText = if (cleanUrl.startsWith("http://", ignoreCase = true) ||
                cleanUrl.startsWith("https://", ignoreCase = true)
            ) cleanUrl else "https://$cleanUrl"

            val uri = runCatching { URI(uriText) }.getOrNull() ?: continue
            val host = uri.host ?: continue
            if (!host.contains('.')) continue

            val normalizedHost = host.removePrefix("www.")
            val path = uri.rawPath.orEmpty().let { if (it == "/") "" else it }
            val query = uri.rawQuery?.let { "?$it" }.orEmpty()
            val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
            return "$normalizedHost$path$query$fragment"
        }

        return null
    }

    private fun extractSiteInfoFromText(inputText: String): SiteInfo? {
        val filtered = filterOutUrlFromPlainText(inputText) ?: inputText.trim()
        if (filtered.isEmpty()) return null

        val urlCandidate = if (filtered.startsWith("http://", ignoreCase = true) ||
            filtered.startsWith("https://", ignoreCase = true)
        ) filtered else "https://$filtered"

        return try {
            val uri = URI(urlCandidate)
            val host = uri.host ?: return null
            if (!host.contains('.')) return null
            val domain = host.lowercase(Locale.ROOT).removePrefix("www.")
            val path = uri.rawPath.orEmpty().let { if (it == "/") "" else it }
            val query = uri.rawQuery?.let { "?$it" }.orEmpty()
            val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
            val identifier = "$domain$path$query$fragment"

            SiteInfo(domain, identifier)
        } catch (_: Exception) {
            null
        }
    }
}
