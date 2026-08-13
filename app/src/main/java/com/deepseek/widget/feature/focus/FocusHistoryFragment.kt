package com.deepseek.widget.feature.focus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.deepseek.widget.DeepSeekWidgetApp
import com.deepseek.widget.data.repository.FocusRepository
import com.deepseek.widget.domain.model.FocusSession
import com.deepseek.widget.ui.theme.WorkbenchTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class FocusHistoryViewModel(repository: FocusRepository) : ViewModel() {
    private val now = System.currentTimeMillis()
    private val start = now - 90L * 24 * 60 * 60 * 1000

    val history: StateFlow<List<FocusSession>> = repository.observeHistory(start, now)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun factory(repository: FocusRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FocusHistoryViewModel(repository) as T
        }
    }
}

class FocusHistoryFragment : Fragment() {
    private val viewModel: FocusHistoryViewModel by viewModels {
        val container = (requireActivity().application as DeepSeekWidgetApp).container
        FocusHistoryViewModel.factory(container.focusRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            WorkbenchTheme {
                FocusHistoryScreen(
                    sessions = viewModel.history.collectAsStateWithLifecycle().value,
                    onBack = { findNavController().navigateUp() }
                )
            }
        }
    }
}
