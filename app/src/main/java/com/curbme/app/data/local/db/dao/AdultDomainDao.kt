package com.curbme.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.curbme.app.data.local.db.entity.AdultDomainEntity

@Dao
interface AdultDomainDao {
    @Query("SELECT * FROM adult_domain_cache WHERE domain = :domain LIMIT 1")
    suspend fun get(domain: String): AdultDomainEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AdultDomainEntity)
}
