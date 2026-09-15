package com.curbme.app.core.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Live domain autocomplete utility that queries DuckDuckGo and Google autocomplete APIs
 * to suggest matching website domains for partial text input.
 */
object DomainSuggestionApi {

    private const val TAG = "DomainSuggestionApi"

    suspend fun suggest(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().lowercase(Locale.ROOT)
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')

        if (trimmed.isEmpty()) return@withContext emptyList()

        try {
            val rawCandidates = mutableListOf<String>()

            // 1. Generate direct domain variations for query
            if (!trimmed.contains(' ')) {
                if (trimmed.contains('.')) {
                    val baseHost = trimmed.substringBefore('/').substringBefore('?').substringBefore('#')
                    rawCandidates.add(baseHost)
                    rawCandidates.add("web.$baseHost")
                    rawCandidates.add("m.$baseHost")
                } else {
                    rawCandidates.add("$trimmed.com")
                    rawCandidates.add("web.$trimmed.com")
                    rawCandidates.add("$trimmed.org")
                    rawCandidates.add("web.$trimmed.org")
                    rawCandidates.add("$trimmed.net")
                    rawCandidates.add("web.$trimmed.net")
                    if ("telegram".contains(trimmed) || trimmed.contains("telegram")) {
                        rawCandidates.add("web.telegram.org")
                        rawCandidates.add("telegram.org")
                        rawCandidates.add("t.me")
                        rawCandidates.add("telegram.me")
                    }
                }
            }

            // 2. Fetch live DuckDuckGo & Google autocomplete phrases
            val ddgResults = fetchDuckDuckGoSuggestions(trimmed)
            rawCandidates.addAll(ddgResults)

            if (rawCandidates.size < 6) {
                val googleResults = fetchGoogleSuggestions(trimmed)
                rawCandidates.addAll(googleResults)
            }

            // 3. Process, clean, and filter domains matching/containing the query substring
            val processed = rawCandidates.mapNotNull { raw ->
                val clean = raw.trim()
                    .lowercase(Locale.ROOT)
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .trimEnd('/')

                val domain = when {
                    clean.contains('.') -> clean.substringBefore('/').substringBefore('?').substringBefore('#')
                    clean.contains(' ') -> "${clean.replace(' ', '.')}.com"
                    clean.length >= 2 -> "$clean.com"
                    else -> null
                }

                if (domain != null && domain.contains('.')) domain else null
            }.filter { candidate ->
                // Keep domains that contain the query substring, or short domains like t.me
                candidate.contains(trimmed) || trimmed.contains(candidate.substringBefore('.')) || candidate == "t.me"
            }.distinct().take(10)

            return@withContext processed
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching domain suggestions for $query: ${e.message}")
            return@withContext emptyList()
        }
    }

    private fun fetchDuckDuckGoSuggestions(query: String): List<String> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://duckduckgo.com/ac/?q=$encoded")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 2000
            readTimeout = 2000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }

        val results = mutableListOf<String>()
        try {
            if (connection.responseCode == 200) {
                val jsonText = connection.inputStream.bufferedReader().readText()
                val jsonArray = JSONArray(jsonText)
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(i)
                    val phrase = item?.optString("phrase")
                    if (!phrase.isNullOrBlank()) {
                        results.add(phrase)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            connection.disconnect()
        }
        return results
    }

    private fun fetchGoogleSuggestions(query: String): List<String> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://www.google.com/complete/search?client=chrome&q=$encoded")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 2000
            readTimeout = 2000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }

        val results = mutableListOf<String>()
        try {
            if (connection.responseCode == 200) {
                val jsonText = connection.inputStream.bufferedReader().readText()
                val jsonArray = JSONArray(jsonText)
                if (jsonArray.length() > 1) {
                    val list = jsonArray.optJSONArray(1)
                    if (list != null) {
                        for (i in 0 until list.length()) {
                            val sug = list.optString(i)
                            if (!sug.isNullOrBlank()) {
                                results.add(sug)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            connection.disconnect()
        }
        return results
    }
}
