package com.curbme.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.curbme.app.data.local.db.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Update
    suspend fun updateSession(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    suspend fun getActiveSession(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    fun getActiveSessionFlow(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions ORDER BY id DESC")
    suspend fun getAllSessions(): List<FocusSessionEntity>

    @Query("DELETE FROM focus_sessions WHERE status != 'ACTIVE' AND id NOT IN (SELECT id FROM focus_sessions WHERE status != 'ACTIVE' ORDER BY lastUpdatedTimeMs DESC LIMIT :keepCount)")
    suspend fun pruneFinishedSessions(keepCount: Int = 20)

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAllSessions()
}
