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
import com.deepseek.widget.api.ApiKeyFunUsageResponse
import com.deepseek.widget.api.ApiKeyFunUsageAggregator
import com.deepseek.widget.api.DeepSeekApiClient
import com.deepseek.widget.api.UsageComparison
import com.deepseek.widget.data.AccountCache
import com.deepseek.widget.data.AccountProvider
import com.deepseek.widget.data.ApiKeyFunProfileStore
import com.deepseek.widget.data.AppPreferences
import com.deepseek.widget.databinding.FragmentApikeyFunBinding
import com.deepseek.widget.ui.WidgetUiHelper
import com.deepseek.widget.worker.WidgetUpdateWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Date

class ApiKeyFunFragment : Fragment(R.layout.fragment_apikey_fun) {

    private var _binding: FragmentApikeyFunBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: AppPreferences
    private lateinit var profilesStore: ApiKeyFunProfileStore
    private val apiClient = DeepSeekApiClient()
    private var selectedDays = 7
    private var usageJob: Job? = null
    private var showingPartialUsageWarning = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentApikeyFunBinding.bind(view)
        prefs = AppPreferences(requireContext())
        profilesStore = ApiKeyFunProfileStore.create(requireContext())
        setupGlassSurfaces()
        binding.btnApikeyFunBack.setOnClickListener { findNavController().navigateUp() }

        binding.apikeyFunDashboard.configure(
            accentColorRes = R.color.apikey_amber,
            currency = "$",
            sourceText = getString(R.string.usage_dashboard_source),
            title = getString(R.string.usage_dashboard)
        )
        binding.btnTestApikeyFun.setOnClickListener {
            testConnection(binding.editApikeyFunApiKey.text?.toString().orEmpty())
        }
        binding.btnManageApikeyKeys.setOnClickListener {
            findNavController().navigate(R.id.apiKeyFunKeysFragment)
        }
        binding.apikeyFunDashboard.onRangeChanged = { days ->
            selectedDays = days
            lifecycleScope.launch { prefs.setUsageRangeDays(days) }
            loadUsage()
        }
        binding.apikeyFunDashboard.onRefresh = { loadUsage() }

        viewLifecycleOwner.lifecycleScope.launch {
            profilesStore.migrateFromLegacy()
            val key = profilesStore.getPrimarySecret().orEmpty()
            binding.editApikeyFunApiKey.setText(key)
            selectedDays = prefs.usageRangeDays.first()
            binding.apikeyFunDashboard.setRangeDays(selectedDays, silent = true)
            renderBalance(prefs.accountCache(AccountProvider.APIKEY_FUN).first(), key.isNotBlank())
            if (key.isNotBlank()) loadUsage(key)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                prefs.accountCache(AccountProvider.APIKEY_FUN).collect { cache ->
                    renderBalance(cache, binding.editApikeyFunApiKey.text?.toString().orEmpty().isNotBlank())
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                profilesStore.observeProfiles().distinctUntilChanged().collect { profiles ->
                    val enabled = profiles.count { it.enabled }
                    binding.btnManageApikeyKeys.text = getString(
                        R.string.apikey_keys_manage_count,
                        enabled,
                        profiles.size
                    )
                    val primarySecret = profiles.firstOrNull { it.isPrimaryForBalance }
                        ?.let { profilesStore.getSecret(it.id) }
                        .orEmpty()
                    if (primarySecret.isNotBlank() &&
                        binding.editApikeyFunApiKey.text?.toString() != primarySecret
                    ) {
                        binding.editApikeyFunApiKey.setText(primarySecret)
                    }
                    loadUsage()
                }
            }
        }
    }

    private fun setupGlassSurfaces() {
        val overlay = ContextCompat.getColor(requireContext(), R.color.provider_glass_overlay)
        val clearDrawable = requireActivity().window.decorView.background
        listOf(
            binding.apikeyFunBalanceGlass,
            binding.apikeyFunKeyGlass,
            binding.apikeyFunUsageGlass
        ).forEach { glass ->
            glass.setupWith(binding.apikeyFunBlurTarget)
                .setFrameClearDrawable(clearDrawable)
                .setBlurRadius(26f)
                .setOverlayColor(overlay)
                .setBlurEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        }
    }

    private fun renderBalance(cache: AccountCache, configured: Boolean) {
        binding.apikeyFunBalance.text = if (!configured || cache.totalBalance.isBlank()) {
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
        binding.apikeyFunStatus.setText(statusRes)
        binding.apikeyFunStatus.setTextColor(requireContext().getColor(colorRes))
        binding.apikeyFunUpdated.text = if (cache.lastUpdated > 0) {
            DateFormat.getTimeFormat(requireContext()).format(Date(cache.lastUpdated))
        } else getString(R.string.placeholder_value)
    }

    private fun loadUsage(apiKeyOverride: String? = null) {
        usageJob?.cancel()
        usageJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val enteredKey = apiKeyOverride?.trim()
                    ?: binding.editApikeyFunApiKey.text?.toString()?.trim().orEmpty()
                val stored = profilesStore.getEnabledSecrets()
                val apiKeys = when {
                    stored.isEmpty() -> listOf(enteredKey)
                    enteredKey.isBlank() -> stored.map { it.second }
                    else -> stored.map { (profile, secret) ->
                        if (profile.isPrimaryForBalance) enteredKey else secret
                    }
                }.filter { it.isNotBlank() }.distinct()
                if (apiKeys.isEmpty()) {
                    binding.apikeyFunDashboard.showKeyRequired(R.string.usage_key_required)
                    return@launch
                }
                binding.apikeyFunDashboard.showLoading()
                val requestedDays = selectedDays
                val results = apiKeys.chunked(3).flatMap { batch ->
                    batch.map { apiKey ->
                        async {
                            try {
                                val current = apiClient.fetchApiKeyFunUsage(apiKey, requestedDays).getOrThrow()
                                val comparison = apiClient.fetchApiKeyFunUsage(
                                    apiKey,
                                    (requestedDays * 2).coerceAtMost(90)
                                ).getOrThrow()
                                Result.success(current to comparison)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                Result.failure(error)
                            }
                        }
                    }.awaitAll()
                }
                val successful = results.mapNotNull { it.getOrNull() }
                if (successful.isEmpty()) {
                    val error = results.firstNotNullOfOrNull { it.exceptionOrNull()?.message }.orEmpty()
                    binding.apikeyFunDashboard.showError(
                        getString(R.string.usage_load_failed_detail, error)
                    )
                    return@launch
                }
                if (successful.size < apiKeys.size) {
                    showStatus(
                        R.string.usage_partial_keys,
                        successful.size.toString(),
                        apiKeys.size.toString(),
                        R.color.accent_orange
                    )
                    showingPartialUsageWarning = true
                } else if (showingPartialUsageWarning) {
                    binding.apikeyFunConnectionStatus.visibility = View.GONE
                    showingPartialUsageWarning = false
                }
                val current = ApiKeyFunUsageAggregator.aggregate(successful.map { it.first })
                val comparison = ApiKeyFunUsageAggregator.aggregate(successful.map { it.second })
                renderUsage(current, comparison.daily_usage, requestedDays)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                binding.apikeyFunDashboard.showError(
                    getString(R.string.usage_load_failed_detail, error.message.orEmpty())
                )
            }
        }
    }

    private fun renderUsage(
        usage: ApiKeyFunUsageResponse,
        comparisonSource: List<com.deepseek.widget.api.DailyUsagePoint>,
        days: Int
    ) {
        if (usage.daily_usage.isEmpty() && usage.model_stats.isEmpty()) {
            binding.apikeyFunDashboard.showEmpty()
            return
        }
        val currentEnd = LocalDate.now()
        val currentStart = currentEnd.minusDays((days - 1).toLong())
        val previousEnd = currentStart.minusDays(1)
        val previousStart = previousEnd.minusDays((days - 1).toLong())
        val currentPoints = UsageComparison.normalize(usage.daily_usage, currentStart, days)
        val previousPoints = UsageComparison.normalize(comparisonSource, previousStart, days)
        binding.apikeyFunDashboard.showUsage(currentPoints, previousPoints, usage.model_stats, days)
    }

    private fun testConnection(rawKey: String) {
        val apiKey = rawKey.trim()
        if (apiKey.isBlank()) {
            showStatus(R.string.at_least_one_key_required, R.color.accent_orange)
            return
        }
        showStatus(R.string.connection_testing, R.color.label_text)
        binding.btnTestApikeyFun.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = apiClient.fetchApiKeyFunBalance(apiKey)
                val balance = result.getOrNull()
                if (balance != null) {
                    val info = balance.balance_infos.firstOrNull()
                    if (info != null) {
                        profilesStore.savePrimaryKey(apiKey)
                        prefs.saveBalanceData(
                            AccountProvider.APIKEY_FUN,
                            AccountCache(
                                totalBalance = info.total_balance,
                                grantedBalance = info.granted_balance,
                                toppedUpBalance = info.topped_up_balance,
                                currency = info.currency,
                                isAvailable = balance.is_available
                            )
                        )
                        DeepSeekWidgetProvider.requestUpdate(requireContext())
                        WidgetUpdateWorker.schedulePeriodic(requireContext())
                        showStatus(
                            R.string.connection_success_with_balance,
                            WidgetUiHelper.getCurrencySymbol(info.currency),
                            WidgetUiHelper.formatBalance(info.total_balance),
                            if (balance.is_available) R.color.accent_green else R.color.accent_orange
                        )
                        loadUsage(apiKey)
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
            } catch (error: Exception) {
                showStatus(
                    R.string.connection_failed_detail,
                    error.message.orEmpty(),
                    "",
                    R.color.accent_red
                )
            } finally {
                _binding?.btnTestApikeyFun?.isEnabled = true
            }
        }
    }

    private fun showStatus(messageRes: Int, colorRes: Int) {
        showingPartialUsageWarning = false
        binding.apikeyFunConnectionStatus.setText(messageRes)
        binding.apikeyFunConnectionStatus.setTextColor(requireContext().getColor(colorRes))
        binding.apikeyFunConnectionStatus.visibility = View.VISIBLE
    }

    private fun showStatus(messageRes: Int, arg1: String, arg2: String, colorRes: Int) {
        showingPartialUsageWarning = false
        binding.apikeyFunConnectionStatus.text = getString(messageRes, arg1, arg2)
        binding.apikeyFunConnectionStatus.setTextColor(requireContext().getColor(colorRes))
        binding.apikeyFunConnectionStatus.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        usageJob?.cancel()
        showingPartialUsageWarning = false
        _binding = null
    }
}
