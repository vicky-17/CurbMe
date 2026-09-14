package com.curbme.app.data.local.db

/**
 * Encodes and decodes 24 hourly usage durations (ms) into a compact comma-separated string for DB storage.
 */
object WebsiteHourlyUsageCodec {

    fun encode(hourlyUsage: IntArray): String {
        return hourlyUsage.joinToString(",")
    }

    fun decode(encoded: String?): IntArray {
        val array = IntArray(24)
        if (encoded.isNullOrBlank()) return array
        try {
            val parts = encoded.split(",")
            for (i in 0 until minOf(24, parts.size)) {
                array[i] = parts[i].trim().toIntOrNull() ?: 0
            }
        } catch (_: Exception) {}
        return array
    }

    fun addTimeToHour(encoded: String?, hour: Int, durationMs: Long): String {
        val array = decode(encoded)
        if (hour in 0..23) {
            array[hour] = (array[hour] + durationMs).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        return encode(array)
    }
}
