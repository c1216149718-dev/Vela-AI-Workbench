package com.deepseek.widget.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room v2 新增：AI 用量每日明细缓存。
 *
 * 复合主键 (provider, credentialId, date, model) 保证每个供应商、每个凭据、
 * 每一天、每个模型只有一行。金额以十进制字符串保存，禁止用 Double 持久化；
 * Repository 层使用 BigDecimal 进行聚合。
 */
@Entity(
    tableName = "ai_usage_daily",
    primaryKeys = ["provider", "credentialId", "date", "model"]
)
data class AiUsageDailyEntity(
    val provider: String,
    val credentialId: String,
    val date: String,
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
