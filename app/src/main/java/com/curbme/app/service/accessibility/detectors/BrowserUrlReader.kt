package com.curbme.app.service.accessibility.detectors

import android.view.accessibility.AccessibilityNodeInfo
import java.net.URI
import java.util.Locale

/**
 * Extracts and normalizes domain names and full URL identifiers from browser address bar accessibility nodes.
 */
object BrowserUrlReader {

    data class SiteInfo(
        val domain: String,
        val urlIdentifier: String
    )

    private val BROWSER_URL_BAR_IDS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("url_bar", "location_bar"),
        "com.chrome.beta" to listOf("url_bar", "location_bar"),
        "com.chrome.canary" to listOf("url_bar", "location_bar"),
        "com.chrome.dev" to listOf("url_bar", "location_bar"),
        "com.brave.browser" to listOf("url_bar", "location_bar"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "url_bar_text"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.fenix" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.focus" to listOf("mozac_browser_toolbar_url_view"),
        "com.microsoft.emmx" to listOf("url_bar", "search_box"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text", "url_bar"),
        "com.opera.browser" to listOf("url_field", "url_bar"),
        "com.opera.mini.native" to listOf("url_field", "url_bar"),
        "com.vivaldi.browser" to listOf("url_bar", "location_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar", "location_bar"),
        "com.duckduckgo.mobile.android" to listOf("omnibar_text_input", "search_box"),
        "org.cromite.cromite" to listOf("url_bar"),
        "app.vanadium.browser" to listOf("url_bar")
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

        return null
    }

    /**
     * Legacy helper extracting only the domain.
     */
    fun readDomain(rootNode: AccessibilityNodeInfo?, packageName: String): String? {
        return readSiteInfo(rootNode, packageName)?.domain
    }

    private fun filterOutUrlFromPlainText(inputText: String): String? {
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
