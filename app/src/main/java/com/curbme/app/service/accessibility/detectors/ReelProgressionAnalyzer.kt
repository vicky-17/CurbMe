package com.curbme.app.service.accessibility.detectors

import android.util.LruCache

/**
 * Analyzes dynamic text comparator strings extracted from short video screens
 * to detect genuine reel scroll transitions while deduplicating minor UI state updates.
 */
class ReelProgressionAnalyzer {

    private val lastDynamicText = mutableMapOf<String, String>()
    private val seenReelsCache = mutableMapOf<String, LruCache<String, Boolean>>()

    /**
     * Checks if currentText represents a new reel/short watched on packageName.
     * Returns true if a new reel scroll event was detected.
     */
    fun checkReelProgression(packageName: String, currentText: String): Boolean {
        if (currentText.isBlank()) return false
        if (isGenericMarker(currentText)) return false

        val previousText = lastDynamicText[packageName] ?: ""
        if (currentText == previousText) return false

        val isSubstantialChange = isSubstantialTextChange(currentText, previousText)
        if (isSubstantialChange) {
            val appCache = seenReelsCache.getOrPut(packageName) { LruCache(50) }
            if (appCache.get(currentText) == null) {
                appCache.put(currentText, true)
                lastDynamicText[packageName] = currentText
                return true
            }
        }

        if (isSubstantialChange || currentText.length > previousText.length) {
            lastDynamicText[packageName] = currentText
        }

        return false
    }

    private fun isGenericMarker(text: String): Boolean {
        return text == "youtube_shorts_active" ||
                text == "instagram_reel_active" ||
                text == "snapchat_spotlight_active" ||
                text == "facebook_reel_active" ||
                text == "tiktok_active"
    }

    /** Clears tracked state for package or all packages. */
    fun clear(packageName: String? = null) {
        if (packageName != null) {
            lastDynamicText.remove(packageName)
            seenReelsCache.remove(packageName)
        } else {
            lastDynamicText.clear()
            seenReelsCache.clear()
        }
    }

    private fun isSubstantialTextChange(currentText: String, previousText: String): Boolean {
        if (currentText.isEmpty() || previousText.isEmpty()) return true

        val currentWords = countWords(currentText)
        val previousWords = countWords(previousText)

        if (currentWords.isEmpty() || previousWords.isEmpty()) return true

        val smallerMap = if (currentWords.size < previousWords.size) currentWords else previousWords
        val largerMap = if (currentWords.size < previousWords.size) previousWords else currentWords

        var intersectionSize = 0
        var totalSmaller = 0

        for ((word, count) in smallerMap) {
            totalSmaller += count
            val largerCount = largerMap[word] ?: 0
            intersectionSize += minOf(count, largerCount)
        }

        if (totalSmaller == 0) return true

        val overlapRatio = intersectionSize.toFloat() / totalSmaller
        // If word overlap is less than 90%, it's a substantial text change (new video/reel)
        return overlapRatio < 0.90f
    }

    private fun countWords(text: String): HashMap<String, Int> {
        val wordCounts = HashMap<String, Int>()
        val len = text.length
        var start = -1
        for (i in 0 until len) {
            if (text[i].isWhitespace()) {
                if (start != -1) {
                    val word = text.substring(start, i)
                    wordCounts[word] = wordCounts.getOrDefault(word, 0) + 1
                    start = -1
                }
            } else {
                if (start == -1) start = i
            }
        }
        if (start != -1) {
            val word = text.substring(start, len)
            wordCounts[word] = wordCounts.getOrDefault(word, 0) + 1
        }
        return wordCounts
    }
}
