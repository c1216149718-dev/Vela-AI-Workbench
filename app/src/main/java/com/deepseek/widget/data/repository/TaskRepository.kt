package com.deepseek.widget.data.repository

import android.content.Context
import com.deepseek.widget.data.local.dao.TaskDao
import com.deepseek.widget.data.local.dao.TaskOrderUpdate
import com.deepseek.widget.data.local.entity.TaskEntity
import com.deepseek.widget.domain.model.Task
import com.deepseek.widget.domain.model.TaskPriority
import com.deepseek.widget.domain.model.TaskScheduleRules
import com.deepseek.widget.domain.model.TaskSourceType
import com.deepseek.widget.domain.model.TaskStatus
import com.deepseek.widget.worker.TaskReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 任务筛选维度。 */
enum class TaskFilter { TODAY, PLANNED, ALL, COMPLETED }

interface TaskRepository {
    fun observeTasks(filter: TaskFilter, today: String): Flow<List<Task>>
    fun observeToday(today: String): Flow<List<Task>>
    fun observeTodayOverview(today: String): Flow<List<Task>>
    fun observeNextSteps(today: String): Flow<List<Task>>
    fun observeTask(id: Long): Flow<Task?>
    suspend fun getTask(id: Long): Task?
    suspend fun create(
        title: String,
        today: String?,
        notes: String = "",
        priority: TaskPriority = TaskPriority.NONE,
        startAt: Long? = null,
        dueAt: Long? = null,
        reminderOffsetMinutes: Int? = null,
        estimateMinutes: Int? = null,
        sourceType: TaskSourceType = TaskSourceType.MANUAL,
        sourceUrl: String? = null
    ): Long
    suspend fun update(task: Task)
    suspend fun delete(task: Task)
    suspend fun complete(id: Long)
    suspend fun restore(id: Long)
    suspend fun clearReminder(id: Long)
    suspend fun updateSortOrders(orders: List<TaskOrderUpdate>)
    suspend fun countPlanned(today: String): Int
    suspend fun countCompleted(today: String, start: Long, end: Long): Int
    suspend fun countOverdue(now: Long): Int
}

class TaskRepositoryImpl(
    private val dao: TaskDao,
    private val context: Context
) : TaskRepository {

    override fun observeTasks(filter: TaskFilter, today: String): Flow<List<Task>> = when (filter) {
        TaskFilter.TODAY -> dao.observeToday(today)
        TaskFilter.PLANNED -> dao.observePlanned()
        TaskFilter.COMPLETED -> dao.observeCompleted()
        TaskFilter.ALL -> dao.observeActive()
    }.map { list -> list.map { it.toDomain() } }

    override fun observeToday(today: String): Flow<List<Task>> =
        dao.observeToday(today).map { list -> list.map { it.toDomain() } }

    override fun observeTodayOverview(today: String): Flow<List<Task>> =
        dao.observeTodayOverview(today).map { list -> list.map { it.toDomain() } }

    override fun observeNextSteps(today: String): Flow<List<Task>> =
        dao.observeNextSteps(today).map { list -> list.map { it.toDomain() } }

    override fun observeTask(id: Long): Flow<Task?> =
        dao.observeTask(id).map { entity -> entity?.toDomain() }

    override suspend fun getTask(id: Long): Task? = dao.getById(id)?.toDomain()

    override suspend fun create(
        title: String,
        today: String?,
        notes: String,
        priority: TaskPriority,
        startAt: Long?,
        dueAt: Long?,
        reminderOffsetMinutes: Int?,
        estimateMinutes: Int?,
        sourceType: TaskSourceType,
        sourceUrl: String?
    ): Long {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "请输入标题" }
        require(cleanTitle.length <= 120) { "标题不能超过 120 个字符" }
        require(notes.length <= 4000) { "备注不能超过 4000 个字符" }
        require(estimateMinutes == null || estimateMinutes in 1..1440) { "预计时长需在 1 到 1440 分钟之间" }
        if (startAt != null || dueAt != null) TaskScheduleRules.validate(startAt, dueAt)
        val reminderAt = TaskScheduleRules.reminderAt(startAt, reminderOffsetMinutes)
        val now = System.currentTimeMillis()
        val status = if (today != null) TaskStatus.PLANNED.name else TaskStatus.BACKLOG.name
        val id = dao.insert(
            TaskEntity(
                title = cleanTitle,
                notes = notes.trim(),
                projectId = 1L,
                status = status,
                priority = priority.value,
                plannedDate = today,
                dueAt = dueAt,
                reminderAt = reminderAt,
                startAt = startAt,
                reminderOffsetMinutes = reminderOffsetMinutes,
                estimateMinutes = estimateMinutes,
                sortOrder = now,
                sourceType = sourceType.name,
                sourceUrl = sourceUrl,
                createdAt = now,
                updatedAt = now
            )
        )
        if (reminderAt != null && reminderAt > now) {
            TaskReminderScheduler.schedule(context, id, reminderAt)
        }
        return id
    }

    override suspend fun update(task: Task) {
        val cleanTitle = task.title.trim()
        require(cleanTitle.isNotEmpty()) { "请输入标题" }
        require(cleanTitle.length <= 120) { "标题不能超过 120 个字符" }
        require(task.notes.length <= 4000) { "备注不能超过 4000 个字符" }
        require(task.estimateMinutes == null || task.estimateMinutes in 1..1440) {
            "预计时长需在 1 到 1440 分钟之间"
        }
        if (task.startAt != null || task.dueAt != null) {
            TaskScheduleRules.validate(task.startAt, task.dueAt)
        }
        val normalizedStatus = when (task.status) {
            TaskStatus.DONE, TaskStatus.CANCELLED, TaskStatus.IN_PROGRESS -> task.status
            else -> if (task.plannedDate == null) TaskStatus.BACKLOG else TaskStatus.PLANNED
        }
        val normalized = task.copy(
            title = cleanTitle,
            status = normalizedStatus,
            reminderAt = TaskScheduleRules.reminderAt(task.startAt, task.reminderOffsetMinutes),
            updatedAt = System.currentTimeMillis()
        )
        dao.update(normalized.toEntity())
        syncReminder(normalized)
    }

    override suspend fun delete(task: Task) {
        dao.delete(task.toEntity())
        TaskReminderScheduler.cancel(context, task.id)
    }

    override suspend fun complete(id: Long) {
        val now = System.currentTimeMillis()
        dao.complete(id, now, now)
        TaskReminderScheduler.cancel(context, id)
    }

    override suspend fun restore(id: Long) {
        val now = System.currentTimeMillis()
        val task = dao.getById(id)
        val status = task?.statusBeforeDone
            ?.let(TaskStatus::fromName)
            ?.takeUnless { it == TaskStatus.DONE || it == TaskStatus.CANCELLED }
            ?: if (task?.plannedDate != null) TaskStatus.PLANNED else TaskStatus.BACKLOG
        dao.restore(id, status.name, now)
        dao.getById(id)?.toDomain()?.let(::syncReminder)
    }

    override suspend fun clearReminder(id: Long) {
        dao.clearReminder(id, System.currentTimeMillis())
        TaskReminderScheduler.cancel(context, id)
    }

    override suspend fun updateSortOrders(orders: List<TaskOrderUpdate>) {
        dao.updateSortOrders(orders)
    }

    override suspend fun countPlanned(today: String): Int = dao.countPlanned(today)

    override suspend fun countCompleted(today: String, start: Long, end: Long): Int =
        dao.countCompleted(today, start, end)

    override suspend fun countOverdue(now: Long): Int = dao.countOverdue(now)

    private fun syncReminder(task: Task) {
        val reminderAt = task.reminderAt
        if (reminderAt == null || reminderAt <= System.currentTimeMillis() ||
            task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED
        ) {
            TaskReminderScheduler.cancel(context, task.id)
            return
        }
        TaskReminderScheduler.schedule(
            context = context,
            taskId = task.id,
            reminderAt = reminderAt
        )
    }
}

internal fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    notes = notes,
    projectId = projectId,
    status = TaskStatus.fromName(status),
    priority = TaskPriority.fromValue(priority),
    plannedDate = plannedDate,
    dueAt = dueAt,
    reminderAt = reminderAt,
    estimateMinutes = estimateMinutes,
    sortOrder = sortOrder,
    sourceType = TaskSourceType.valueOf(sourceType),
    sourceUrl = sourceUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    startAt = startAt,
    reminderOffsetMinutes = reminderOffsetMinutes,
    statusBeforeDone = statusBeforeDone?.let(TaskStatus::fromName)
)

internal fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    projectId = projectId,
    status = status.name,
    priority = priority.value,
    plannedDate = plannedDate,
    dueAt = dueAt,
    reminderAt = reminderAt,
    estimateMinutes = estimateMinutes,
    sortOrder = sortOrder,
    sourceType = sourceType.name,
    sourceUrl = sourceUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    startAt = startAt,
    reminderOffsetMinutes = reminderOffsetMinutes,
    statusBeforeDone = statusBeforeDone?.name
)
