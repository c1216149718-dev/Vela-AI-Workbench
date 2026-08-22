package com.deepseek.widget

import android.content.Context
import com.deepseek.widget.api.DeepSeekApiClient
import com.deepseek.widget.data.local.WorkbenchDatabase
import com.deepseek.widget.data.repository.FocusRepository
import com.deepseek.widget.data.repository.FocusRepositoryImpl
import com.deepseek.widget.data.repository.ReviewRepository
import com.deepseek.widget.data.repository.ReviewRepositoryImpl
import com.deepseek.widget.data.repository.TaskRepository
import com.deepseek.widget.data.repository.TaskRepositoryImpl
import com.deepseek.widget.data.repository.AiUsageRepository
import com.deepseek.widget.data.ApiKeyFunProfileStore
import com.deepseek.widget.data.AppPreferences
import com.deepseek.widget.data.provider.ProviderProfileRepository
import com.deepseek.widget.data.security.SecureCredentialStore

/**
 * 手动 DI 容器。进程内单例，由 [DeepSeekWidgetApp] 创建。
 * 持有数据库、DataStore、网络客户端和 Repository 实现。
 */
class AppContainer(context: Context) {

    val appPreferences: AppPreferences = AppPreferences(context)

    val apiKeyFunProfiles: ApiKeyFunProfileStore = ApiKeyFunProfileStore.create(context)

    val apiClient: DeepSeekApiClient = DeepSeekApiClient()

    private val database: WorkbenchDatabase = WorkbenchDatabase.get(context)

    val secureCredentialStore = SecureCredentialStore(context)

    val providerProfileRepository = ProviderProfileRepository(
        database.providerProfileDao(),
        secureCredentialStore
    )

    val taskRepository: TaskRepository = TaskRepositoryImpl(database.taskDao(), context)

    val focusRepository: FocusRepository = FocusRepositoryImpl(
        database.focusSessionDao(),
        database.taskDao(),
        context
    )

    val reviewRepository: ReviewRepository = ReviewRepositoryImpl(database.dailyReviewDao())

    val aiUsageRepository: AiUsageRepository = AiUsageRepository(
        database = database,
        preferences = appPreferences,
        profiles = apiKeyFunProfiles,
        apiClient = apiClient,
        providerProfiles = providerProfileRepository
    )
}
