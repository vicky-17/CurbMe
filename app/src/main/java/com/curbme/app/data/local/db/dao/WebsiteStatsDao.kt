package com.curbme.app.data.local.db.dao

import androidx.room.*
import com.curbme.app.data.local.db.WebsiteHourlyUsageCodec
import com.curbme.app.data.local.db.entity.WebsiteStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebsiteStatsDao {
    @Query("SELECT * FROM website_stats WHERE date = :date")
    suspend fun getForDate(date: String): List<WebsiteStatsEntity>

    @Query("SELECT * FROM website_stats WHERE date = :date")
    fun getForDateFlow(date: String): Flow<List<WebsiteStatsEntity>>

    @Query("SELECT * FROM website_stats WHERE date = :date")
    suspend fun getStatsForDate(date: String): List<WebsiteStatsEntity>

    @Query("SELECT * FROM website_stats WHERE date IN (:dates)")
    suspend fun getStatsForDates(dates: List<String>): List<WebsiteStatsEntity>

    @Query("SELECT * FROM website_stats WHERE date = :date AND packageName = :packageName")
    suspend fun getStatsForPackage(date: String, packageName: String): List<WebsiteStatsEntity>

    @Query("SELECT * FROM website_stats WHERE date = :date AND packageName = :packageName AND urlIdentifier = :urlIdentifier LIMIT 1")
    suspend fun getSingleStat(date: String, packageName: String, urlIdentifier: String): WebsiteStatsEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: WebsiteStatsEntity): Long

    @Upsert
    suspend fun upsert(entity: WebsiteStatsEntity)

    @Query("UPDATE website_stats SET lastVisited = :lastVisited WHERE date = :date AND packageName = :packageName AND urlIdentifier = :urlIdentifier")
    suspend fun touch(date: String, packageName: String, urlIdentifier: String, lastVisited: Long): Int

    @Query("UPDATE website_stats SET totalTime = totalTime + :durationMs, hourlyUsage = :hourlyUsage, lastVisited = :lastVisited WHERE date = :date AND packageName = :packageName AND urlIdentifier = :urlIdentifier")
    suspend fun updateHourlyTime(date: String, packageName: String, urlIdentifier: String, durationMs: Long, hourlyUsage: String, lastVisited: Long): Int

    @Transaction
    suspend fun addTime(date: String, packageName: String, urlIdentifier: String, hour: Int, durationMs: Long, lastVisited: Long) {
        val existing = getSingleStat(date, packageName, urlIdentifier)
        val newHourly = WebsiteHourlyUsageCodec.addTimeToHour(existing?.hourlyUsage, hour, durationMs)
        updateHourlyTime(date, packageName, urlIdentifier, durationMs, newHourly, lastVisited)
    }

    @Query("UPDATE website_stats SET totalTime = totalTime + :deltaMs, lastVisited = :lastVisited WHERE date = :date AND packageName = :packageName AND domain = :domain")
    suspend fun addTimeLegacy(date: String, packageName: String, domain: String, deltaMs: Long, lastVisited: Long): Int

    @Query("DELETE FROM website_stats WHERE date < :beforeDate")
    suspend fun purgeOlderThan(beforeDate: String)
}
