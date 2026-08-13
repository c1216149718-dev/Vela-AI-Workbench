package com.deepseek.widget.feature.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deepseek.widget.data.repository.FocusRepository
import com.deepseek.widget.data.repository.ReviewRepository
import com.deepseek.widget.data.repository.TaskRepository
import com.deepseek.widget.domain.model.DailyReview
import com.deepseek.widget.domain.model.FocusSession
import com.deepseek.widget.domain.model.Task
import com.deepseek.widget.domain.model.TaskStatus
import com.deepseek.widget.data.AccountCache
import com.deepseek.widget.data.AccountProvider
import com.deepseek.widget.data.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WorkbenchUiState(
    val dateText: String = "",
    val greeting: String = "",
    val todayTasks: List<Task> = emptyList(),
    val activeFocus: FocusSession? = null,
    val todayReview: DailyReview? = null,
    val deepSeekAccount: AccountCache = AccountCache(),
    val apiKeyFunAccount: AccountCache = AccountCache(),
    val todayTaskCount: Int = 0,
    val completedTaskCount: Int = 0,
    val todayFocusMinutes: Int = 0,
    val todayRecordedAiCost: Double = 0.0,
    val hasRecordedAiUsage: Boolean = false,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkbenchViewModel(
    private val taskRepository: TaskRepository,
    private val focusRepository: FocusRepository,
    private val reviewRepository: ReviewRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val currentDate = MutableStateFlow(LocalDate.now())
    private val refreshToken = MutableStateFlow(0L)

    private val productivity = combine(currentDate, refreshToken) { date, _ -> date }
        .flatMapLatest { date ->
            val dateKey = date.toString()
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli() - 1L
            combine(
                taskRepository.observeTodayOverview(dateKey),
                focusRepository.observeActive(),
                focusRepository.observeHistory(startOfDay, endOfDay),
                reviewRepository.observeByDate(dateKey)
            ) { tasks, focus, history, review ->
                ProductivitySnapshot(date, tasks, focus, history, review)
            }
        }

    private val accounts = combine(
        appPreferences.accountCache(AccountProvider.DEEPSEEK),
        appPreferences.accountCache(AccountProvider.APIKEY_FUN),
        appPreferences.deepSeekUsageEntries
    ) { deepSeek, apiKeyFun, usageEntries ->
        AccountSnapshot(deepSeek, apiKeyFun, usageEntries)
    }

    val uiState: StateFlow<WorkbenchUiState> = combine(productivity, accounts) { data, accountCaches ->
        val completedCount = data.tasks.count { it.status == TaskStatus.DONE }
        val focusMinutes = data.history
            .filter { it.status == com.deepseek.widget.domain.model.FocusStatus.COMPLETED }
            .sumOf { it.actualMinutes() }
        WorkbenchUiState(
            dateText = formatDate(data.date),
            greeting = greeting(),
            todayTasks = data.tasks.take(4),
            activeFocus = data.focus,
            todayReview = data.review,
            deepSeekAccount = accountCaches.deepSeek,
            apiKeyFunAccount = accountCaches.apiKeyFun,
            todayTaskCount = data.tasks.size,
            completedTaskCount = completedCount,
            todayFocusMinutes = focusMinutes,
            todayRecordedAiCost = accountCaches.usageEntries
                .filter { it.date == data.date.toString() }
                .sumOf { it.cost },
            hasRecordedAiUsage = accountCaches.usageEntries.any { it.date == data.date.toString() },
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkbenchUiState())

    fun quickAddTask(title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty() || cleanTitle.length > 120) return
        viewModelScope.launch {
            runCatching { taskRepository.create(cleanTitle, currentDate.value.toString()) }
        }
    }

    fun completeTask(task: Task) {
        viewModelScope.launch {
            runCatching {
                if (task.status == TaskStatus.DONE) taskRepository.restore(task.id)
                else taskRepository.complete(task.id)
            }
        }
    }

    fun saveReview(rating: Int, note: String) {
        viewModelScope.launch {
            runCatching { reviewRepository.upsert(currentDate.value.toString(), rating, note.take(4000)) }
        }
    }

    fun refreshDate() {
        currentDate.value = LocalDate.now()
        refreshToken.value = System.currentTimeMillis()
    }

    private fun formatDate(date: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("M月d日 · E", Locale.CHINESE)
        return date.format(formatter)
    }

    private fun greeting(): String {
        val hour = LocalTime.now().hour
        return when (hour) {
            in 5..11 -> "早上好"
            in 12..17 -> "下午好"
            else -> "晚上好"
        }
    }

    companion object {
        fun factory(
            taskRepo: TaskRepository,
            focusRepo: FocusRepository,
            reviewRepo: ReviewRepository,
            appPreferences: AppPreferences
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WorkbenchViewModel(taskRepo, focusRepo, reviewRepo, appPreferences) as T
        }
    }
}

private data class ProductivitySnapshot(
    val date: LocalDate,
    val tasks: List<Task>,
    val focus: FocusSession?,
    val history: List<FocusSession>,
    val review: DailyReview?
)

private data class AccountSnapshot(
    val deepSeek: AccountCache,
    val apiKeyFun: AccountCache,
    val usageEntries: List<com.deepseek.widget.data.DeepSeekUsageEntry>
)
