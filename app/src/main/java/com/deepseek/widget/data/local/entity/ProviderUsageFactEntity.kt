package com.deepseek.widget.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Provider-neutral usage fact. DAY rows drive trends; PERIOD rows preserve provider model
 * summaries without pretending that an interval aggregate belongs to one day.
 */
@Entity(
    tableName = "provider_usage_facts",
    primaryKeys = [
        "providerId", "credentialId", "bucketKind", "periodStart", "periodEnd",
        "bucketDate", "model", "currency", "provenance", "sourceId"
    ],
    indices = [
        Index(value = ["bucketKind", "bucketDate"]),
        Index(value = ["providerId", "periodStart", "periodEnd"]),
        Index(value = ["credentialId"])
    ]
)
data class ProviderUsageFactEntity(
    val providerId: String,
    val credentialId: String,
    val credentialLabel: String,
    val bucketKind: String,
    val periodStart: String,
    val periodEnd: String,
    val bucketDate: String,
    val model: String,
    val currency: String,
    val cost: String,
    val requests: Long? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cachedTokens: Long? = null,
    val totalTokens: Long? = null,
    val provenance: String,
    val sourceId: String,
    val updatedAt: Long
) {
    companion object {
        const val DAY = "DAY"
        const val PERIOD = "PERIOD"
        const val ALL_MODELS = "__all__"
    }
}
