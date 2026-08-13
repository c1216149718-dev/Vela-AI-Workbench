package com.deepseek.widget.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deepseek.widget.data.repository.TaskRepository
import com.deepseek.widget.domain.model.TaskPriority
import com.deepseek.widget.domain.model.TaskScheduleRules
import com.deepseek.widget.domain.model.TaskSourceType
import com.deepseek.widget.domain.model.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Retained only so older navigation arguments continue to open safely. */
enum class TaskEntryMode { TODAY, PLANNED }

data class TaskEditUiState(
    val taskId: Long = -1L,
    val title: String = "",
    val notes: String = "",
    val priority: TaskPriority = TaskPriority.NONE,
    val status: TaskStatus = TaskStatus.BACKLOG,
    val scheduleEnabled: Boolean = false,
    val plannedDate: String? = null,
    val startAt: Long? = null,
    val dueAt: Long? = null,
    val reminderOffsetMinutes: Int? = null,
    val isLoaded: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null
) {
    val isReadOnly: Boolean get() = status == TaskStatus.DONE
}

class TaskEditViewModel(
    private val repository: TaskRepository,
    private val taskId: Long,
    legacyEntryMode: TaskEntryMode
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TaskEditUiState(taskId = taskId).withDefaultSchedule(legacyEntryMode == TaskEntryMode.PLANNED)
    )
    val uiState: StateFlow<TaskEditUiState> = _uiState.asStateFlow()

    init {
        if (taskId > 0) loadTask() else _uiState.value = _uiState.value.copy(isLoaded = true)
    }

    private fun loadTask() {
        viewModelScope.launch {
            val task = repository.getTask(taskId)
            _uiState.value = if (task != null) {
                TaskEditUiState(
                    taskId = task.id,
                    title = task.title,
                    notes = task.notes,
                    priority = task.priority,
                    status = task.status,
                    scheduleEnabled = task.startAt != null && task.dueAt != null,
                    plannedDate = task.plannedDate,
                    startAt = task.startAt,
                    dueAt = task.dueAt,
                    reminderOffsetMinutes = task.reminderOffsetMinutes,
                    isLoaded = true
                )
            } else {
                _uiState.value.copy(isLoaded = true, error = "任务不存在")
            }
        }
    }

    fun updateTitle(value: String) = update { copy(title = value, error = null) }
    fun updateNotes(value: String) = update { copy(notes = value, error = null) }
    fun updatePriority(value: TaskPriority) = update { copy(priority = value, error = null) }
    fun updateReminderOffset(value: Int?) = update { copy(reminderOffsetMinutes = value, error = null) }

    fun updateScheduleEnabled(enabled: Boolean) = update {
        if (enabled) withDefaultSchedule(true).copy(error = null)
        else copy(
            scheduleEnabled = false,
            plannedDate = null,
            startAt = null,
            dueAt = null,
            reminderOffsetMinutes = null,
            error = null
        )
    }

    fun updatePlannedDate(value: String) = update {
        val duration = if (startAt != null && dueAt != null) dueAt - startAt else HOUR_MILLIS
        val movedStart = startAt?.let { moveToDate(it, value) }
        copy(
            scheduleEnabled = true,
            plannedDate = value,
            startAt = movedStart,
            dueAt = movedStart?.plus(duration),
            error = null
        )
    }

    fun updateStartAt(value: Long) = update {
        val duration = if (startAt != null && dueAt != null && dueAt > startAt) dueAt - startAt else HOUR_MILLIS
        copy(
            scheduleEnabled = true,
            plannedDate = TaskScheduleRules.dateOf(value),
            startAt = value,
            dueAt = value + duration,
            error = null
        )
    }

    fun updateDueAt(value: Long) = update {
        copy(scheduleEnabled = true, dueAt = value, error = null)
    }

    fun save() {
        val state = _uiState.value
        if (state.isReadOnly) return
        val validationError = runCatching {
            require(state.title.isNotBlank()) { "请输入标题" }
            require(state.title.trim().length <= 120) { "标题不能超过 120 个字符" }
            require(state.notes.length <= 4000) { "备注不能超过 4000 个字符" }
            if (state.scheduleEnabled) TaskScheduleRules.validate(state.startAt, state.dueAt)
        }.exceptionOrNull()?.message
        if (validationError != null) {
            _uiState.value = state.copy(error = validationError)
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, error = null)
            runCatching {
                val plannedDate = if (state.scheduleEnabled) state.startAt?.let(TaskScheduleRules::dateOf) else null
                val startAt = state.startAt.takeIf { state.scheduleEnabled }
                val dueAt = state.dueAt.takeIf { state.scheduleEnabled }
                val reminder = state.reminderOffsetMinutes.takeIf { state.scheduleEnabled }
                if (taskId > 0) {
                    val existing = repository.getTask(taskId) ?: error("任务不存在")
                    repository.update(
                        existing.copy(
                            title = state.title.trim(),
                            notes = state.notes.trim(),
                            priority = state.priority,
                            plannedDate = plannedDate,
                            startAt = startAt,
                            dueAt = dueAt,
                            reminderOffsetMinutes = reminder
                        )
                    )
                } else {
                    repository.create(
                        title = state.title.trim(),
                        today = plannedDate,
                        notes = state.notes.trim(),
                        priority = state.priority,
                        startAt = startAt,
                        dueAt = dueAt,
                        reminderOffsetMinutes = reminder,
                        sourceType = TaskSourceType.MANUAL
                    )
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isSaving = false, error = error.message ?: "保存失败")
            }
        }
    }

    fun delete() {
        if (_uiState.value.isReadOnly) return
        viewModelScope.launch {
            runCatching { if (taskId > 0) repository.getTask(taskId)?.let { repository.delete(it) } }
                .onSuccess { _uiState.value = _uiState.value.copy(isDeleted = true) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "删除失败") }
        }
    }

    private fun update(block: TaskEditUiState.() -> TaskEditUiState) {
        if (!_uiState.value.isReadOnly) _uiState.value = _uiState.value.block()
    }

    private fun moveToDate(timestamp: Long, date: String): Long {
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime()
        return LocalDate.parse(date).atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    companion object {
        fun factory(repository: TaskRepository, taskId: Long, entryMode: TaskEntryMode) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TaskEditViewModel(repository, taskId, entryMode) as T
            }

        private const val HOUR_MILLIS = 60 * 60_000L
    }
}

private fun TaskEditUiState.withDefaultSchedule(enabled: Boolean): TaskEditUiState {
    if (!enabled || (startAt != null && dueAt != null)) return copy(scheduleEnabled = enabled)
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.of(LocalDate.now(), LocalTime.now().withSecond(0).withNano(0))
    val rounded = now.plusMinutes((5 - now.minute % 5).toLong() % 5)
    val start = rounded.atZone(zone).toInstant().toEpochMilli()
    return copy(
        scheduleEnabled = true,
        plannedDate = rounded.toLocalDate().toString(),
        startAt = start,
        dueAt = start + 60 * 60_000L
    )
}
