package com.deepseek.widget.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("projectId"),
        Index("plannedDate"),
        Index("status"),
        Index("dueAt")
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val projectId: Long? = null,
    val status: String,
    val priority: Int = 0,
    val plannedDate: String? = null,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val estimateMinutes: Int? = null,
    val sortOrder: Long = 0,
    val sourceType: String = "MANUAL",
    val sourceUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val startAt: Long? = null,
    val reminderOffsetMinutes: Int? = null,
    val statusBeforeDone: String? = null
)
