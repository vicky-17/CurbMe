package com.curbme.app.service.vpn

import android.util.Log
import com.curbme.app.BuildConfig
import com.curbme.app.core.utils.KeywordMatcher
import com.curbme.app.data.local.prefs.Settings
import com.curbme.app.service.vpn.blocklist.PornDomainBlocklist.isBlocked
import java.util.Collections
import java.util.Locale

/**
 * Why we made this file:
 * The DnsVpnService handles the raw network plumbing, but it is "dumb" — it doesn't
 * know what a packet means. This Engine is the "Brain". For every single website
 * the child's phone tries to visit, this engine receives the domain name and makes
 * a lightning-fast routing decision: Allow, Block, or Redirect.
 * 
 * What the file name defines:
 * "DnsFilter" defines its purpose (filtering Domain Name System requests).
 * "Engine" denotes it as a core business-logic processor.
 */
sealed class FilterDecision {
    /** Forward to upstream DNS normally  */
    object Allow : FilterDecision()

    /** Return NXDOMAIN — domain does not exist  */
    object Block : FilterDecision()

    /** Return a fake A record pointing to the SafeSearch IP  */
    data class SafeSearchRedirect(val redirectIp: String) : FilterDecision()
}

class DnsFilterEngine {

    fun decide(domain: String?, queryType: Int, settings: Settings): FilterDecision {
        if (domain == null) return FilterDecision.Allow

        // Normalize the domain so "YouTube.com" matches "youtube.com"
        val lowerDomain = domain.trim().lowercase(Locale.ROOT).removePrefix("www.")

        // ── 1. SafeSearch enforcement ─────────────────────────────────────────
        if (settings.isSafeSearchEnabled) {
            val safeIp: String? = SAFESEARCH_DOMAINS[lowerDomain] ?: SAFESEARCH_DOMAINS[domain.lowercase(Locale.ROOT)]

            if (safeIp != null && queryType == DnsPacketParser.TYPE_A) {
                Log.d(TAG, "🔍 SafeSearch redirect: $domain → $safeIp")
                return FilterDecision.SafeSearchRedirect(safeIp)
            }
        }

        // ── 2. Porn / adult content blocking ─────────────────────────────────
        if (settings.isBlockPorn || settings.isSafeSearchEnabled) {
            if (isBlocked(lowerDomain) || isBlocked(domain.lowercase(Locale.ROOT))) {
                Log.d(TAG, "🚫 Porn blocked: $domain")
                return FilterDecision.Block
            }
        }

        // ── 3. Custom user-defined domain blocklist ───────────────────────────
        if (isCustomBlocked(domain, settings)) {
            Log.d(TAG, "🚫 Custom blocked: $domain")
            return FilterDecision.Block
        }

        return FilterDecision.Allow
    }

    private fun isCustomBlocked(rawDomain: String?, settings: Settings): Boolean {
        if (rawDomain.isNullOrBlank() || settings.blockedWebsites.isEmpty()) return false
        val normalizedDomain = rawDomain.trim().lowercase(Locale.ROOT).removePrefix("www.")

        val isBlocked = settings.blockedWebsites.contains(rawDomain) ||
                settings.blockedWebsites.contains(normalizedDomain) ||
                KeywordMatcher.isMatch(settings.blockedWebsites, normalizedDomain)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Blocked-website check: raw=$rawDomain, normalized=$normalizedDomain, match=$isBlocked")
        }

        return isBlocked
    }

    companion object {
        private const val TAG = "DnsFilterEngine"

        // ── SafeSearch Map Initialization ─────────────────────────────────────────
        private val SAFESEARCH_DOMAINS: MutableMap<String?, String?>

        init {
            val map: MutableMap<String?, String?> = HashMap<String?, String?>()
            // Google Search
            map.put("google.com", "216.239.38.120")
            map.put("www.google.com", "216.239.38.120")
            map.put("google.co.in", "216.239.38.120")
            map.put("google.co.uk", "216.239.38.120")
            map.put("google.com.au", "216.239.38.120")
            map.put("google.ca", "216.239.38.120")
            map.put("google.de", "216.239.38.120")
            map.put("google.fr", "216.239.38.120")
            map.put("encrypted.google.com", "216.239.38.120")

            // YouTube
            map.put("youtube.com", "216.239.38.119")
            map.put("www.youtube.com", "216.239.38.119")
            map.put("m.youtube.com", "216.239.38.119")
            map.put("youtubei.googleapis.com", "216.239.38.119")

            // Bing
            map.put("bing.com", "204.79.197.220")
            map.put("www.bing.com", "204.79.197.220")

            // DuckDuckGo safe mode
            map.put("duckduckgo.com", "54.191.125.83")
            map.put("www.duckduckgo.com", "54.191.125.83")

            SAFESEARCH_DOMAINS = Collections.unmodifiableMap<String?, String?>(map)
        }
    }
}