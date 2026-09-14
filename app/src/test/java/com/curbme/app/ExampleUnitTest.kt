package com.curbme.app

import com.curbme.app.core.utils.KeywordMatcher
import com.curbme.app.data.local.prefs.Settings
import com.curbme.app.service.vpn.DnsFilterEngine
import com.curbme.app.service.vpn.FilterDecision
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testDomainMatching() {
        val blockedList = setOf("youtube.com")

        // 1. Direct match
        assertTrue(KeywordMatcher.isMatch(blockedList, "youtube.com"))

        // 2. www prefix match
        assertTrue(KeywordMatcher.isMatch(blockedList, "www.youtube.com"))

        // 3. Subdomain match (m.youtube.com)
        assertTrue(KeywordMatcher.isMatch(blockedList, "m.youtube.com"))

        // 4. URL path match
        assertTrue(KeywordMatcher.isMatch(blockedList, "youtube.com/watch?v=123"))

        // 5. Non-matching domain
        assertFalse(KeywordMatcher.isMatch(blockedList, "notyoutube.com"))
        assertFalse(KeywordMatcher.isMatch(blockedList, "youtube.com.attacker.com"))
    }

    @Test
    fun testWildcardMatching() {
        val blockedList = setOf("*.reddit.com")

        assertTrue(KeywordMatcher.isMatch(blockedList, "reddit.com"))
        assertTrue(KeywordMatcher.isMatch(blockedList, "www.reddit.com"))
        assertTrue(KeywordMatcher.isMatch(blockedList, "old.reddit.com"))
        assertTrue(KeywordMatcher.isMatch(blockedList, "sub.old.reddit.com"))

        assertFalse(KeywordMatcher.isMatch(blockedList, "notreddit.com"))
    }

    @Test
    fun testDnsFilterEngineCustomBlock() {
        val engine = DnsFilterEngine()
        val settings = Settings(blockedWebsites = setOf("youtube.com", "*.reddit.com"))

        // youtube.com, www.youtube.com, m.youtube.com must all return FilterDecision.Block
        assertEquals(FilterDecision.Block, engine.decide("youtube.com", 1, settings))
        assertEquals(FilterDecision.Block, engine.decide("www.youtube.com", 1, settings))
        assertEquals(FilterDecision.Block, engine.decide("m.youtube.com", 1, settings))

        // Wildcard *.reddit.com through DNS
        assertEquals(FilterDecision.Block, engine.decide("reddit.com", 1, settings))
        assertEquals(FilterDecision.Block, engine.decide("old.reddit.com", 1, settings))
        assertEquals(FilterDecision.Block, engine.decide("www.reddit.com", 1, settings))

        // Allowed domains
        assertEquals(FilterDecision.Allow, engine.decide("wikipedia.org", 1, settings))
        assertEquals(FilterDecision.Allow, engine.decide("notyoutube.com", 1, settings))
    }
}
