package com.deepseek.widget.feature.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.deepseek.widget.DeepSeekWidgetApp
import com.deepseek.widget.ui.theme.WorkbenchTheme

class TaskEditFragment : Fragment() {

    private val entryMode: TaskEntryMode
        get() = arguments?.getString("entryMode")
            ?.let { runCatching { TaskEntryMode.valueOf(it) }.getOrNull() }
            ?: TaskEntryMode.TODAY

    private val viewModel: TaskEditViewModel by viewModels {
        val container = (requireActivity().application as DeepSeekWidgetApp).container
        val taskId = arguments?.getLong("taskId", -1L) ?: -1L
        TaskEditViewModel.factory(container.taskRepository, taskId, entryMode)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            WorkbenchTheme {
                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                TaskEditScreen(
                    state = state,
                    onBack = { findNavController().navigateUp() },
                    onTitleChange = viewModel::updateTitle,
                    onNotesChange = viewModel::updateNotes,
                    onPriorityChange = viewModel::updatePriority,
                    onScheduleEnabledChange = viewModel::updateScheduleEnabled,
                    onReminderChange = viewModel::updateReminderOffset,
                    onDateChange = viewModel::updatePlannedDate,
                    onStartChange = viewModel::updateStartAt,
                    onEndChange = viewModel::updateDueAt,
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
                    onFinished = { findNavController().popBackStack() }
                )
            }
        }
    }

}
