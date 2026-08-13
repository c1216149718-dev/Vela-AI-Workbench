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
import com.deepseek.widget.R
import com.deepseek.widget.data.repository.TaskFilter
import com.deepseek.widget.ui.theme.WorkbenchTheme

class TaskListFragment : Fragment() {

    private val viewModel: TaskListViewModel by viewModels {
        val container = (requireActivity().application as DeepSeekWidgetApp).container
        val initialFilter = arguments?.getString("filter")
            ?.let { value -> TaskFilter.entries.firstOrNull { it.name == value } }
            ?: TaskFilter.ALL
        TaskListViewModel.factory(container.taskRepository, initialFilter)
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
                TaskListScreen(
                    state = state,
                    onQueryChange = viewModel::setQuery,
                    onFilterChange = viewModel::setFilter,
                    onTaskToggle = { task ->
                        if (task.status == com.deepseek.widget.domain.model.TaskStatus.DONE) {
                            viewModel.restoreTask(task)
                        } else {
                            viewModel.completeTask(task)
                        }
                    },
                    onTaskClick = { task ->
                        findNavController().navigate(
                            R.id.taskEditFragment,
                            Bundle().apply { putLong("taskId", task.id) }
                        )
                    },
                    onAddTask = {
                        findNavController().navigate(
                            R.id.taskEditFragment,
                            Bundle()
                        )
                    }
                )
            }
        }
    }
}
