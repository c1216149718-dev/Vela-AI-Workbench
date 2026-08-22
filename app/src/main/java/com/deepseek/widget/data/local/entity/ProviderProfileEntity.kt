package com.deepseek.widget.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "provider_profiles", indices = [Index("providerId")])
data class ProviderProfileEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val alias: String,
    val credentialRef: String,
    val capabilities: String,
    val configJson: String,
    val enabled: Boolean,
    val backgroundSync: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastTestedAt: Long?,
    val lastError: String
)
