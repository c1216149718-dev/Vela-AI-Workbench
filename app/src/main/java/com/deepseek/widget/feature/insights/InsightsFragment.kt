package com.deepseek.widget.feature.insights

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.deepseek.widget.DeepSeekWidgetApp
import com.deepseek.widget.R
import com.deepseek.widget.ui.theme.WorkbenchTheme

class InsightsFragment : Fragment() {

    private val viewModel: InsightsViewModel by activityViewModels {
        val container = (requireActivity().application as DeepSeekWidgetApp).container
        InsightsViewModel.factory(container.aiUsageRepository, container.appPreferences, container.apiKeyFunProfiles, container.providerProfileRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            WorkbenchTheme {
                InsightsScreen(
                    state = viewModel.uiState.collectAsStateWithLifecycle().value,
                    onUsageClick = { findNavController().navigate(R.id.usageDetailFragment) },
                    onDataSourcesClick = { findNavController().navigate(R.id.dataSourceCenterFragment) },
                    onProviderClick = { providerId ->
                        findNavController().navigate(R.id.providerDetailFragment, Bundle().apply { putString("providerId", providerId) })
                    },
                    onRangeChange = viewModel::selectDays,
                    onRefresh = viewModel::refresh
                )
            }
        }
    }
}
