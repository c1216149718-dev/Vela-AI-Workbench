package com.deepseek.widget.domain.model

/** 任务领域模型。 */
data class Task(
    val id: Long,
    val title: String,
    val notes: String,
    val projectId: Long?,
    val status: TaskStatus,
    val priority: TaskPriority,
    val plannedDate: String?,
    val dueAt: Long?,
    val reminderAt: Long?,
    val estimateMinutes: Int?,
    val sortOrder: Long,
    val sourceType: TaskSourceType,
    val sourceUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val startAt: Long? = null,
    val reminderOffsetMinutes: Int? = null,
    val statusBeforeDone: TaskStatus? = null
)
