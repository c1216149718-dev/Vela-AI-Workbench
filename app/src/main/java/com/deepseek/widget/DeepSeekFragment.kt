package com.deepseek.widget

import android.os.Bundle
import android.os.Build
import android.text.format.DateFormat
import android.view.View
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.deepseek.widget.api.BalanceDeltaAggregator
import com.deepseek.widget.api.DeepSeekApiClient
import com.deepseek.widget.data.AccountCache
import com.deepseek.widget.data.AccountProvider
import com.deepseek.widget.data.AppPreferences
import com.deepseek.widget.data.BalanceSnapshot
import com.deepseek.widget.databinding.FragmentDeepseekBinding
import com.deepseek.widget.ui.WidgetUiHelper
import com.deepseek.widget.worker.WidgetUpdateWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Date

class DeepSeekFragment : Fragment(R.layout.fragment_deepseek) {

    private var _binding: FragmentDeepseekBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: AppPreferences
    private val apiClient = DeepSeekApiClient()
    private var selectedDays = 7
    private var snapshots: List<BalanceSnapshot> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeepseekBinding.bind(view)
        prefs = AppPreferences(requireContext())
        setupGlassSurfaces()
        binding.btnDeepseekBack.setOnClickListener { findNavController().navigateUp() }

        binding.deepseekDashboard.configure(
            accentColorRes = R.color.deepseek_blue,
            currency = "¥",
            sourceText = getString(R.string.balance_delta_source),
            title = getString(R.string.spending_tracker)
        )

        binding.btnTestDeepseek.setOnClickListener {
            testConnection(binding.editDeepseekApiKey.text?.toString().orEmpty())
        }
        binding.deepseekDashboard.onRangeChanged = { days ->
            selectedDays = days
            lifecycleScope.launch { prefs.setUsageRangeDays(days) }
            renderDashboard()
        }
        binding.deepseekDashboard.onRefresh = { refreshBalance() }

        viewLifecycleOwner.lifecycleScope.launch {
            val key = prefs.deepSeekApiKey.first()
            binding.editDeepseekApiKey.setText(key)
            selectedDays = prefs.usageRangeDays.first()
            binding.deepseekDashboard.setRangeDays(selectedDays, silent = true)
            renderBalance(prefs.accountCache(AccountProvider.DEEPSEEK).first(), key.isNotBlank())
            if (key.isNotBlank()) refreshBalance()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                prefs.accountCache(AccountProvider.DEEPSEEK).collect { cache ->
                    renderBalance(cache, binding.editDeepseekApiKey.text?.toString().orEmpty().isNotBlank())
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                prefs.deepSeekBalanceSnapshots.collect { list ->
                    snapshots = list
                    renderDashboard()
                }
            }
        }
    }

    private fun setupGlassSurfaces() {
        val overlay = ContextCompat.getColor(requireContext(), R.color.provider_glass_overlay)
        val clearDrawable = requireActivity().window.decorView.background
        listOf(
            binding.deepseekBalanceGlass,
            binding.deepseekKeyGlass,
            binding.deepseekUsageGlass
        ).forEach { glass ->
            glass.setupWith(binding.deepseekBlurTarget)
                .setFrameClearDrawable(clearDrawable)
                .setBlurRadius(26f)
                .setOverlayColor(overlay)
                .setBlurEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        }
    }

    private fun renderBalance(cache: AccountCache, configured: Boolean) {
        binding.deepseekBalance.text = if (!configured || cache.totalBalance.isBlank()) {
            getString(R.string.placeholder_value)
        } else {
            getString(
                R.string.currency_amount_value,
                WidgetUiHelper.getCurrencySymbol(cache.currency),
                WidgetUiHelper.formatBalance(cache.totalBalance)
            )
        }
        val (statusRes, colorRes) = when {
            !configured -> R.string.not_configured to R.color.label_text
            cache.errorMessage.isNotBlank() -> R.string.status_error to R.color.accent_red
            cache.totalBalance.isBlank() -> R.string.loading to R.color.label_text
            cache.isAvailable -> R.string.status_available to R.color.accent_green
            else -> R.string.status_unavailable to R.color.accent_orange
        }
        binding.deepseekStatus.setText(statusRes)
        binding.deepseekStatus.setTextColor(requireContext().getColor(colorRes))
        val symbol = WidgetUiHelper.getCurrencySymbol(cache.currency)
        binding.deepseekGranted.text = if (!configured || cache.grantedBalance.isBlank()) {
            getString(R.string.placeholder_value)
        } else {
            getString(R.string.currency_amount_value, symbol, WidgetUiHelper.formatBalance(cache.grantedBalance))
        }
        binding.deepseekToppedUp.text = if (!configured || cache.toppedUpBalance.isBlank()) {
            getString(R.string.placeholder_value)
        } else {
            getString(R.string.currency_amount_value, symbol, WidgetUiHelper.formatBalance(cache.toppedUpBalance))
        }
        binding.deepseekUpdated.text = if (cache.lastUpdated > 0) {
            DateFormat.getTimeFormat(requireContext()).format(Date(cache.lastUpdated))
        } else getString(R.string.placeholder_value)
    }

    private fun renderDashboard() {
        val days = selectedDays
        val today = LocalDate.now()
        val currentStart = today.minusDays((days - 1).toLong())
        val previousEnd = currentStart.minusDays(1)
        val previousStart = previousEnd.minusDays((days - 1).toLong())

        if (snapshots.size < 2) {
            binding.deepseekDashboard.showKeyRequired(R.string.balance_delta_empty)
            return
        }

        // 使用完整快照列表，让区间前的最近余额成为首日扣减基线。
        val currentPoints = BalanceDeltaAggregator.dailyPoints(snapshots, currentStart, days)
        val previousPoints = BalanceDeltaAggregator.dailyPoints(snapshots, previousStart, days)
        val modelStats = BalanceDeltaAggregator.modelStats(currentPoints.sumOf { it.actual_cost })

        if (currentPoints.all { it.actual_cost == 0.0 } && modelStats.isEmpty()) {
            binding.deepseekDashboard.showKeyRequired(R.string.balance_delta_no_spending)
            return
        }

        binding.deepseekDashboard.showUsage(currentPoints, previousPoints, modelStats, days)
    }

    private fun refreshBalance() {
        val apiKey = binding.editDeepseekApiKey.text?.toString()?.trim().orEmpty()
        if (apiKey.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = apiClient.fetchBalance(apiKey)
            val balance = result.getOrNull()
            if (balance != null) {
                val info = balance.balance_infos.firstOrNull()
                if (info != null) {
                    prefs.saveBalanceData(
                        AccountProvider.DEEPSEEK,
                        AccountCache(
                            totalBalance = info.total_balance,
                            grantedBalance = info.granted_balance,
                            toppedUpBalance = info.topped_up_balance,
                            currency = info.currency,
                            isAvailable = balance.is_available
                        )
                    )
                    // 保存余额快照用于差值计算
                    val numericBalance = info.total_balance.toDoubleOrNull()
                    if (numericBalance != null) {
                        prefs.addBalanceSnapshot(
                            BalanceSnapshot(
                                timestamp = System.currentTimeMillis(),
                                balance = numericBalance,
                                currency = info.currency
                            )
                        )
                    }
                    DeepSeekWidgetProvider.requestUpdate(requireContext())
                }
            }
        }
    }

    private fun testConnection(rawKey: String) {
        val apiKey = rawKey.trim()
        if (apiKey.isBlank()) {
            showStatus(R.string.at_least_one_key_required, R.color.accent_orange)
            return
        }
        showStatus(R.string.connection_testing, R.color.label_text)
        binding.btnTestDeepseek.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = apiClient.fetchBalance(apiKey)
            val balance = result.getOrNull()
            if (balance != null) {
                val info = balance.balance_infos.firstOrNull()
                if (info != null) {
                    prefs.setDeepSeekApiKey(apiKey)
                    prefs.saveBalanceData(
                        AccountProvider.DEEPSEEK,
                        AccountCache(
                            totalBalance = info.total_balance,
                            grantedBalance = info.granted_balance,
                            toppedUpBalance = info.topped_up_balance,
                            currency = info.currency,
                            isAvailable = balance.is_available
                        )
                    )
                    // 保存余额快照
                    val numericBalance = info.total_balance.toDoubleOrNull()
                    if (numericBalance != null) {
                        prefs.addBalanceSnapshot(
                            BalanceSnapshot(
                                timestamp = System.currentTimeMillis(),
                                balance = numericBalance,
                                currency = info.currency
                            )
                        )
                    }
                    DeepSeekWidgetProvider.requestUpdate(requireContext())
                    WidgetUpdateWorker.schedulePeriodic(requireContext())
                    showStatus(
                        R.string.connection_success_with_balance,
                        WidgetUiHelper.getCurrencySymbol(info.currency),
                        WidgetUiHelper.formatBalance(info.total_balance),
                        if (balance.is_available) R.color.accent_green else R.color.accent_orange
                    )
                } else {
                    showStatus(R.string.no_balance_data, R.color.accent_orange)
                }
            } else {
                showStatus(
                    R.string.connection_failed_detail,
                    result.exceptionOrNull()?.message.orEmpty(),
                    "",
                    R.color.accent_red
                )
            }
            binding.btnTestDeepseek.isEnabled = true
        }
    }

    private fun showStatus(messageRes: Int, colorRes: Int) {
        binding.deepseekConnectionStatus.setText(messageRes)
        binding.deepseekConnectionStatus.setTextColor(requireContext().getColor(colorRes))
        binding.deepseekConnectionStatus.visibility = View.VISIBLE
    }

    private fun showStatus(messageRes: Int, arg1: String, arg2: String, colorRes: Int) {
        binding.deepseekConnectionStatus.text = getString(messageRes, arg1, arg2)
        binding.deepseekConnectionStatus.setTextColor(requireContext().getColor(colorRes))
        binding.deepseekConnectionStatus.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
