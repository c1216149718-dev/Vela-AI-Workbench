package com.deepseek.widget

import android.app.Application
import com.deepseek.widget.worker.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StartupState(
    val progress: Float = 0f,
    val stage: String = "准备 Vela",
    val ready: Boolean = false
)

class DeepSeekWidgetApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var container: AppContainer
        private set

    private var coldEntryAvailable = true

    private val _startupState = MutableStateFlow(StartupState(progress = 0.08f))
    val startupState: StateFlow<StartupState> = _startupState.asStateFlow()

    @Synchronized
    fun consumeColdEntry(): Boolean {
        val show = coldEntryAvailable
        coldEntryAvailable = false
        return show
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        _startupState.value = StartupState(0.22f, "打开本地工作区")
        NotificationHelper.createChannels(this)
        _startupState.value = StartupState(0.32f, "准备提醒")
        applicationScope.launch {
            runCatching { container.apiKeyFunProfiles.migrateFromLegacy() }
            _startupState.value = StartupState(0.46f, "整理本地账户")
            runCatching { container.apiKeyFunProfiles.migrateSecretsToSecure() }
            _startupState.value = StartupState(0.62f, "保护凭据")
            runCatching { container.appPreferences.migrateLegacyCredentials() }
            _startupState.value = StartupState(0.76f, "恢复偏好")
            runCatching {
                container.providerProfileRepository.importLegacyIfMissing(
                    providerId = com.deepseek.widget.data.provider.ProviderRegistry.DEEPSEEK.value,
                    alias = "DeepSeek",
                    key = container.appPreferences.deepSeekApiKey.first(),
                    stableId = "legacy-deepseek"
                )
                container.apiKeyFunProfiles.getEnabledSecrets().forEach { (profile, key) ->
                    container.providerProfileRepository.importLegacyIfMissing(
                        providerId = com.deepseek.widget.data.provider.ProviderRegistry.APIKEY_FUN.value,
                        alias = profile.alias,
                        key = key,
                        stableId = "legacy-apikeyfun-${profile.id}"
                    )
                }
            }
            _startupState.value = StartupState(1f, "准备就绪", ready = true)
        }
        applicationScope.launch {
            delay(3_000L)
            if (!_startupState.value.ready) {
                _startupState.value = StartupState(1f, "后台继续整理", ready = true)
            }
        }
    }
}
