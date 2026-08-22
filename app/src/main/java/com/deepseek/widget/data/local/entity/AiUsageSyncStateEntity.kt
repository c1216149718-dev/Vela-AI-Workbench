package com.deepseek.widget.data.local.entity

import androidx.room.Entity

/** 每个数据源/凭据最近一次刷新结果；失败时保留旧缓存并记录失败原因。 */
@Entity(
    tableName = "ai_usage_sync_state",
    primaryKeys = ["provider", "credentialId"]
)
data class AiUsageSyncStateEntity(
    val provider: String,
    val credentialId: String,
    val credentialLabel: String,
    val periodStart: String,
    val periodEnd: String,
    val lastSuccessAt: Long? = null,
    val lastAttemptAt: Long,
    val errorMessage: String = "",
    val status: String = if (errorMessage.isBlank()) "SUCCESS" else "FAILURE",
    val errorType: String = "",
    val lastCompletedAt: Long? = lastSuccessAt
)
