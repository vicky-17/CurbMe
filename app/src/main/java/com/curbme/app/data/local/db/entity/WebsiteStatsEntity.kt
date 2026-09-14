package com.curbme.app.data.local.db.entity

import androidx.room.Entity

/**
 * Entity representing domain-level browser usage per day and browser package.
 */
@Entity(tableName = "website_stats", primaryKeys = ["date", "packageName", "urlIdentifier"])
data class WebsiteStatsEntity(
    val date: String,
    val packageName: String,
    val domain: String,
    val urlIdentifier: String = "",
    val totalTime: Long = 0,
    val hourlyUsage: String = "",
    val lastVisited: Long = System.currentTimeMillis()
)
