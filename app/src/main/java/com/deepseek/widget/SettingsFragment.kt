package com.deepseek.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.deepseek.widget.data.AppPreferences
import com.deepseek.widget.feature.settings.SettingsScreen
import com.deepseek.widget.ui.theme.WorkbenchTheme
import com.deepseek.widget.worker.WidgetUpdateWorker
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(requireContext().applicationContext)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val version = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0).versionName.orEmpty()
        setContent {
            WorkbenchTheme {
                SettingsScreen(
                    themeMode = preferences.themeMode.collectAsStateWithLifecycle(initialValue = com.deepseek.widget.data.ThemeMode.SYSTEM).value,
                    refreshIntervalMinutes = preferences.refreshIntervalMinutes.collectAsStateWithLifecycle(initialValue = 30).value,
                    versionName = version,
                    onThemeModeChange = { mode ->
                        lifecycleScope.launch { preferences.setThemeMode(mode) }
                    },
                    onRefreshIntervalChange = { minutes ->
                        lifecycleScope.launch { preferences.setRefreshIntervalMinutes(minutes) }
                    },
                    onApplyRefreshInterval = {
                        WidgetUpdateWorker.schedulePeriodic(requireContext())
                        Toast.makeText(requireContext(), R.string.refresh_interval_applied, Toast.LENGTH_SHORT).show()
                    },
                    onDeepSeekClick = { findNavController().navigate(R.id.deepSeekFragment) },
                    onApiKeyFunClick = { findNavController().navigate(R.id.apiKeyFunFragment) }
                )
            }
        }
    }
}
