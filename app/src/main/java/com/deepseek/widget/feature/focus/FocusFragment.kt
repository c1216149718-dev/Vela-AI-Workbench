package com.deepseek.widget.feature.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.deepseek.widget.DeepSeekWidgetApp
import com.deepseek.widget.MainActivity
import com.deepseek.widget.R
import com.deepseek.widget.ui.theme.WorkbenchTheme

class FocusFragment : Fragment() {
    private val viewModel: FocusViewModel by viewModels {
        val container = (requireActivity().application as DeepSeekWidgetApp).container
        FocusViewModel.factory(container.focusRepository, container.appPreferences)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startFocus() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            WorkbenchTheme {
                FocusScreen(
                    state = viewModel.uiState.collectAsStateWithLifecycle().value,
                    onBack = { findNavController().navigateUp() },
                    onHistory = { findNavController().navigate(R.id.focusHistoryFragment) },
                    onMinutesChange = viewModel::setMinutes,
                    onStyleChange = viewModel::setTimerStyle,
                    onStart = ::startWithNotificationPermission,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onComplete = viewModel::complete,
                    onCancel = viewModel::cancel,
                    onImmersiveChange = { active ->
                        (activity as? MainActivity)?.setFocusImmersive(active)
                    }
                )
            }
        }
    }

    private fun startWithNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startFocus()
        }
    }
}
