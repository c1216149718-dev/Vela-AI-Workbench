package com.deepseek.widget.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("taskId")]
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val plannedMinutes: Int,
    val startedAt: Long,
    val expectedEndAt: Long,
    val endedAt: Long? = null,
    val pausedAt: Long? = null,
    val accumulatedPauseMillis: Long = 0,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
