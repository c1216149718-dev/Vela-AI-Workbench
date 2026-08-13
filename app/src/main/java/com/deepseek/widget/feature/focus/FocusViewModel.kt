package com.deepseek.widget.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deepseek.widget.data.AppPreferences
import com.deepseek.widget.data.FocusTimerStyle
import com.deepseek.widget.data.repository.FocusRepository
import com.deepseek.widget.domain.model.FocusSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FocusUiState(
    val activeSession: FocusSession? = null,
    val selectedMinutes: Int = 25,
    val timerStyle: FocusTimerStyle = FocusTimerStyle.LUNA,
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val error: String? = null
)

class FocusViewModel(
    private val repository: FocusRepository,
    private val preferences: AppPreferences
) : ViewModel() {
    private val selectedMinutes = MutableStateFlow(25)
    private val actionState = MutableStateFlow(ActionState())

    val uiState: StateFlow<FocusUiState> = combine(
        repository.observeActive(),
        selectedMinutes,
        preferences.focusTimerStyle,
        actionState
    ) { session, minutes, style, action ->
        FocusUiState(
            activeSession = session,
            selectedMinutes = minutes,
            timerStyle = style,
            isLoading = false,
            isBusy = action.isBusy,
            error = action.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusUiState())

    fun setMinutes(minutes: Int) {
        selectedMinutes.value = minutes.coerceIn(5, 300)
    }

    fun setTimerStyle(style: FocusTimerStyle) {
        viewModelScope.launch { preferences.setFocusTimerStyle(style) }
    }

    fun startFocus() = runAction { repository.startSession(null, selectedMinutes.value) }

    fun pause() {
        val session = uiState.value.activeSession ?: return
        runAction { check(repository.pause(session.id)) { "专注状态已变化" } }
    }

    fun resume() {
        val session = uiState.value.activeSession ?: return
        runAction { check(repository.resume(session.id)) { "专注状态已变化" } }
    }

    fun complete() {
        val session = uiState.value.activeSession ?: return
        runAction { check(repository.complete(session.id)) { "专注状态已变化" } }
    }

    fun cancel() {
        val session = uiState.value.activeSession ?: return
        runAction { check(repository.cancel(session.id)) { "专注状态已变化" } }
    }

    private fun runAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            actionState.value = ActionState(isBusy = true)
            runCatching { action() }
                .onSuccess { actionState.value = ActionState() }
                .onFailure { actionState.value = ActionState(error = it.message ?: "操作失败") }
        }
    }

    companion object {
        fun factory(repository: FocusRepository, preferences: AppPreferences) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FocusViewModel(repository, preferences) as T
            }
    }
}

private data class ActionState(val isBusy: Boolean = false, val error: String? = null)
