package com.deepseek.widget.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deepseek.widget.data.local.dao.TaskOrderUpdate
import com.deepseek.widget.data.repository.TaskFilter
import com.deepseek.widget.data.repository.TaskRepository
import com.deepseek.widget.domain.model.Task
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TaskListUiState(
    val tasks: List<Task> = emptyList(),
    val filter: TaskFilter = TaskFilter.ALL,
    val query: String = "",
    val today: String = LocalDate.now().toString(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModel(
    private val repository: TaskRepository,
    initialFilter: TaskFilter = TaskFilter.ALL
) : ViewModel() {

    private val _filter = MutableStateFlow(initialFilter)
    val filter: StateFlow<TaskFilter> = _filter.asStateFlow()
    private val _query = MutableStateFlow("")

    val uiState: StateFlow<TaskListUiState> = combine(_filter, _query) { filter, query ->
        filter to query.trim()
    }.flatMapLatest { (filter, query) ->
            val today = LocalDate.now().toString()
            repository.observeTasks(filter, today).map { tasks ->
                val filtered = if (query.isEmpty()) tasks else tasks.filter { task ->
                    task.title.contains(query, ignoreCase = true) ||
                        task.notes.contains(query, ignoreCase = true)
                }
                TaskListUiState(tasks = filtered, filter = filter, query = query, today = today, isLoading = false)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskListUiState())

    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun completeTask(task: Task) {
        viewModelScope.launch { runCatching { repository.complete(task.id) } }
    }

    fun restoreTask(task: Task) {
        viewModelScope.launch { runCatching { repository.restore(task.id) } }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { runCatching { repository.delete(task) } }
    }

    fun quickAddTask(title: String, addToToday: Boolean) {
        if (title.isBlank()) return
        val today = if (addToToday) LocalDate.now().toString() else null
        viewModelScope.launch { runCatching { repository.create(title.take(120), today) } }
    }

    fun updateSortOrders(orders: List<Pair<Long, Long>>) {
        viewModelScope.launch {
            repository.updateSortOrders(orders.map { TaskOrderUpdate(it.first, it.second) })
        }
    }

    companion object {
        fun factory(repository: TaskRepository, initialFilter: TaskFilter) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TaskListViewModel(repository, initialFilter) as T
        }
    }
}
