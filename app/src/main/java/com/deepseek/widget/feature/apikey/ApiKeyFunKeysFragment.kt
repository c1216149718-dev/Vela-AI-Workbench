package com.deepseek.widget.feature.apikey

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

class ApiKeyFunKeysFragment : Fragment() {
    private val viewModel: ApiKeyFunKeysViewModel by viewModels {
        val container = (requireActivity().application as DeepSeekWidgetApp).container
        ApiKeyFunKeysViewModel.factory(container.apiKeyFunProfiles, container.apiClient)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            WorkbenchTheme {
                ApiKeyFunKeysScreen(
                    state = viewModel.uiState.collectAsStateWithLifecycle().value,
                    onBack = { findNavController().navigateUp() },
                    onAdd = viewModel::add,
                    onEnabledChange = viewModel::setEnabled,
                    onSetPrimary = viewModel::setPrimary,
                    onTest = viewModel::test,
                    onDelete = viewModel::delete,
                    onMessageShown = viewModel::clearMessage
                )
            }
        }
    }
}
