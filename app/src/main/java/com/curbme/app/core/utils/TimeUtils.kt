package com.curbme.app.core.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object TimeUtils {
    private val dayKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun dayKey(date: LocalDate): String {
        return date.format(dayKeyFormatter)
    }

    fun todayKey(): String {
        return dayKey(LocalDate.now())
    }

    fun formatDuration(ms: Long): String {
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun formatDurationShort(ms: Long): String {
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
    }
}
