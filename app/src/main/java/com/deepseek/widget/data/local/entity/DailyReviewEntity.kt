package com.deepseek.widget.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_reviews")
data class DailyReviewEntity(
    @PrimaryKey val date: String,
    val rating: Int? = null,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long
)
