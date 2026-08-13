package com.deepseek.widget.data.local.entity

import androidx.room.Entity

/** 按凭据保存某一完整统计窗口的模型分布，避免把区间汇总伪装成单日数据。 */
@Entity(
    tableName = "ai_usage_model_period",
    primaryKeys = ["provider", "credentialId", "periodStart", "periodEnd", "model"]
)
data class AiUsageModelPeriodEntity(
    val provider: String,
    val credentialId: String,
    val credentialLabel: String,
    val periodStart: String,
    val periodEnd: String,
    val model: String,
    val currency: String,
    val cost: String,
    val requests: Long? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val isEstimated: Boolean = false,
    val updatedAt: Long
)
