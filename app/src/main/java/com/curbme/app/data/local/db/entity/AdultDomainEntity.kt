package com.curbme.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "adult_domain_cache")
data class AdultDomainEntity(
    @PrimaryKey val domain: String,
    val isAdult: Boolean,
    val cachedAt: Long = System.currentTimeMillis()
)
