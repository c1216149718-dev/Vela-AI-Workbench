package com.deepseek.widget.domain.model

/** 每日复盘领域模型。 */
data class DailyReview(
    val date: String,
    val rating: Int?,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long
)
