package com.deepseek.widget

import android.app.Application
import com.deepseek.widget.worker.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DeepSeekWidgetApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
        applicationScope.launch {
            runCatching { container.apiKeyFunProfiles.migrateFromLegacy() }
        }
    }
}
