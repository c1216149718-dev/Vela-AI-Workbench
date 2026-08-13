package com.deepseek.widget.feature.review

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
import com.deepseek.widget.data.repository.ReviewRepository
import com.deepseek.widget.domain.model.DailyReview
import com.deepseek.widget.ui.theme.WorkbenchTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class ReviewArchiveViewModel(repository: ReviewRepository) : ViewModel() {
    val reviews: StateFlow<List<DailyReview>> = repository.observeRange(
        LocalDate.now().minusYears(5).toString(),
        LocalDate.now().toString()
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun factory(repository: ReviewRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReviewArchiveViewModel(repository) as T
        }
    }
}

class ReviewArchiveFragment : Fragment() {
    private val viewModel: ReviewArchiveViewModel by viewModels {
        val container = (requireActivity().application as DeepSeekWidgetApp).container
        ReviewArchiveViewModel.factory(container.reviewRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            WorkbenchTheme {
                ReviewArchiveScreen(
                    reviews = viewModel.reviews.collectAsStateWithLifecycle().value,
                    onBack = { findNavController().navigateUp() }
                )
            }
        }
    }
}
