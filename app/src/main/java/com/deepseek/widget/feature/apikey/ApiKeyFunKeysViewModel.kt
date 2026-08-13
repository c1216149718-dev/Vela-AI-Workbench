package com.deepseek.widget.feature.apikey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deepseek.widget.api.DeepSeekApiClient
import com.deepseek.widget.data.ApiKeyFunProfile
import com.deepseek.widget.data.ApiKeyFunProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ApiKeyFunKeysUiState(
    val profiles: List<ApiKeyFunProfile> = emptyList(),
    val busyIds: Set<String> = emptySet(),
    val testResults: Map<String, String> = emptyMap(),
    val message: String? = null
)

class ApiKeyFunKeysViewModel(
    private val store: ApiKeyFunProfileStore,
    private val apiClient: DeepSeekApiClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(ApiKeyFunKeysUiState())
    val uiState: StateFlow<ApiKeyFunKeysUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.observeProfiles().collect { profiles ->
                _uiState.value = _uiState.value.copy(profiles = profiles)
            }
        }
    }

    fun add(alias: String, key: String, makePrimary: Boolean) {
        viewModelScope.launch {
            val message = when (val result = store.addKey(key, alias, makePrimary)) {
                is ApiKeyFunProfileStore.AddKeyResult.Added -> "已添加 ${result.profile.alias}"
                is ApiKeyFunProfileStore.AddKeyResult.AlreadyExists -> "该密钥已存在"
                ApiKeyFunProfileStore.AddKeyResult.BlankKey -> "请输入 API Key"
            }
            _uiState.value = _uiState.value.copy(message = message)
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { store.setEnabled(id, enabled) }
    }

    fun setPrimary(id: String) {
        viewModelScope.launch {
            store.setPrimary(id)
            store.setEnabled(id, true)
            _uiState.value = _uiState.value.copy(message = "余额主 Key 已更新")
        }
    }

    fun test(id: String) {
        viewModelScope.launch {
            setBusy(id, true)
            val secret = store.getSecret(id)
            val result = if (secret.isNullOrBlank()) {
                "密钥不可用"
            } else {
                apiClient.fetchApiKeyFunBalance(secret).fold(
                    onSuccess = { response ->
                        val balance = response.balance_infos.firstOrNull()?.total_balance
                        if (balance.isNullOrBlank()) "连接成功" else "连接成功 · \$${balance}"
                    },
                    onFailure = { it.message ?: "连接失败" }
                )
            }
            _uiState.value = _uiState.value.copy(
                busyIds = _uiState.value.busyIds - id,
                testResults = _uiState.value.testResults + (id to result)
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val message = when (store.deleteProfile(id)) {
                ApiKeyFunProfileStore.DeleteProfileResult.Deleted -> "密钥已删除"
                ApiKeyFunProfileStore.DeleteProfileResult.PrimaryMustBeReassigned ->
                    "请先指定另一把余额主 Key"
                ApiKeyFunProfileStore.DeleteProfileResult.NotFound -> "密钥不存在"
            }
            _uiState.value = _uiState.value.copy(message = message)
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun setBusy(id: String, busy: Boolean) {
        _uiState.value = _uiState.value.copy(
            busyIds = if (busy) _uiState.value.busyIds + id else _uiState.value.busyIds - id
        )
    }

    companion object {
        fun factory(store: ApiKeyFunProfileStore, apiClient: DeepSeekApiClient) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ApiKeyFunKeysViewModel(store, apiClient) as T
            }
    }
}
