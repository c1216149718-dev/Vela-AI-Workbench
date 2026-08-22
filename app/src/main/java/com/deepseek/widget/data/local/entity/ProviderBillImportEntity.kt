package com.deepseek.widget.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "provider_bill_imports",
    indices = [Index(value = ["providerId", "fileHash"], unique = true)]
)
data class ProviderBillImportEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val credentialId: String,
    val fileName: String,
    val fileHash: String,
    val startDate: String,
    val endDate: String,
    val recordCount: Int,
    val importedAt: Long
)
