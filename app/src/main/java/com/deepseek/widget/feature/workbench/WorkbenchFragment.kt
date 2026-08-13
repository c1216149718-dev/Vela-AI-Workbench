package com.deepseek.widget.feature.workbench

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
import com.deepseek.widget.MainActivity
import com.deepseek.widget.R
import com.deepseek.widget.feature.home.HomeScreen
import com.deepseek.widget.ui.theme.WorkbenchTheme

class WorkbenchFragment : Fragment() {

    private val viewModel: WorkbenchViewModel by viewModels {
        val container = (requireActivity().application as DeepSeekWidgetApp).container
        WorkbenchViewModel.factory(
            container.taskRepository,
            container.focusRepository,
            container.reviewRepository,
            container.appPreferences
        )
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
                HomeScreen(
                    state = state,
                    onAddTask = {
                        findNavController().navigate(R.id.taskEditFragment, Bundle())
                    },
                    onTaskToggle = viewModel::completeTask,
                    onTaskClick = { task ->
                        findNavController().navigate(
                            R.id.taskEditFragment,
                            Bundle().apply { putLong("taskId", task.id) }
                        )
                    },
                    onSeeAllTasks = { findNavController().navigate(R.id.taskListFragment) },
                    onFocusClick = { findNavController().navigate(R.id.focusFragment) },
                    onDeepSeekClick = { findNavController().navigate(R.id.deepSeekFragment) },
                    onApiKeyFunClick = { findNavController().navigate(R.id.apiKeyFunFragment) },
                    onReviewSave = { note ->
                        val rating = viewModel.uiState.value.todayReview?.rating ?: 3
                        viewModel.saveReview(rating, note)
                    },
                    onReviewArchive = {
                        findNavController().navigate(R.id.reviewArchiveFragment)
                    },
                    onWindowStateChanged = { open, fullscreen ->
                        (activity as? MainActivity)?.setBottomNavigationWindowState(open, fullscreen)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDate()
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.setBottomNavigationWindowState(open = false, fullscreen = false)
        super.onDestroyView()
    }
}
