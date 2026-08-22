package com.deepseek.widget.data.provider

import com.deepseek.widget.data.local.dao.ProviderProfileDao
import com.deepseek.widget.data.local.entity.ProviderProfileEntity
import com.deepseek.widget.data.security.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

class ProviderProfileRepository(
    private val dao: ProviderProfileDao,
    private val credentials: SecureCredentialStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()
) {
    private val connectorRegistry = ProviderConnectorRegistry.create(client)

    fun observeProfiles(): Flow<List<ProviderProfileEntity>> = dao.observeAll()

    suspend fun getEnabledProfiles(): List<ProviderProfileEntity> = dao.getEnabled()
    suspend fun getProfile(id: String): ProviderProfileEntity? = dao.get(id)

    suspend fun credentialsFor(profile: ProviderProfileEntity): Map<String, String> {
        val descriptor = ProviderRegistry.descriptor(profile.providerId) ?: return emptyMap()
        return descriptor.credentials.associate { field ->
            field.id to credentials.get("${profile.credentialRef}:${field.id}").orEmpty()
        }
    }

    suspend fun importLegacyIfMissing(providerId: String, alias: String, key: String, stableId: String) {
        if (key.isBlank() || dao.get(stableId) != null) return
        save(providerId, alias, mapOf("api_key" to key), id = stableId)
    }

    suspend fun save(
        providerId: String,
        alias: String,
        values: Map<String, String>,
        configJson: String = "",
        backgroundSync: Boolean = providerId != ProviderRegistry.CUSTOM.value,
        id: String = UUID.randomUUID().toString()
    ): String {
        val descriptor = ProviderRegistry.descriptor(providerId) ?: error("未知数据源")
        descriptor.credentials.filter { it.required }.forEach { field ->
            require(values[field.id].orEmpty().isNotBlank()) { "${field.label}不能为空" }
        }
        val reference = "provider:$id"
        values.filterValues { it.isNotBlank() }.forEach { (name, value) ->
            credentials.put("$reference:$name", value.trim())
        }
        val now = System.currentTimeMillis()
        dao.upsert(
            ProviderProfileEntity(
                id = id,
                providerId = providerId,
                alias = alias.trim().ifBlank { descriptor.displayName },
                credentialRef = reference,
                capabilities = descriptor.capabilities.joinToString(",") { it.name },
                configJson = configJson,
                enabled = true,
                backgroundSync = backgroundSync,
                createdAt = now,
                updatedAt = now,
                lastTestedAt = null,
                lastError = ""
            )
        )
        return id
    }

    suspend fun test(id: String): ProviderResult<Unit> = withContext(Dispatchers.IO) {
        val profile = dao.get(id) ?: return@withContext ProviderResult.Failure("数据源不存在")
        val descriptor = ProviderRegistry.descriptor(profile.providerId)
            ?: return@withContext ProviderResult.Failure("未知数据源")
        val values = descriptor.credentials.associate { field ->
            field.id to credentials.get("${profile.credentialRef}:${field.id}").orEmpty()
        }
        if (profile.providerId != ProviderRegistry.CUSTOM.value) {
            val connector = connectorRegistry.connector(profile.providerId)
                ?: return@withContext ProviderResult.Failure("尚未注册该供应商连接器")
            val result = connector.testConnection(values)
            val error = when (result) {
                is ProviderResult.Supported -> ""
                is ProviderResult.Unsupported -> result.reason
                is ProviderResult.PermissionRequired -> result.reason
                is ProviderResult.PartialFailure -> result.message
                is ProviderResult.Failure -> result.message
            }
            updateTest(profile, error)
            return@withContext result
        }
        val endpoint = if (profile.providerId == ProviderRegistry.CUSTOM.value) {
            parseConfigValue(profile.configJson, "testUrl")
        } else descriptor.testUrl
        if (endpoint.isNullOrBlank()) {
            updateTest(profile, "该平台需要额外签名参数，已保存配置但未执行远程测试")
            return@withContext ProviderResult.Unsupported(ProviderCapability.CONNECTION, "需平台签名凭据")
        }
        val method = parseConfigValue(profile.configJson, "method")?.uppercase() ?: "GET"
        val authHeader = parseConfigValue(profile.configJson, "authHeader") ?: "Authorization"
        val authPrefix = parseConfigValue(profile.configJson, "authPrefix") ?: "Bearer "
        val key = values["api_key"].orEmpty().ifBlank { values["admin_key"].orEmpty() }
        val builder = Request.Builder().url(endpoint)
        if (key.isNotBlank()) builder.header(authHeader, authPrefix + key)
        values["organization_id"]?.takeIf { it.isNotBlank() }?.let { builder.header("OpenAI-Organization", it) }
        values["project_id"]?.takeIf { it.isNotBlank() }?.let { builder.header("OpenAI-Project", it) }
        if (method == "POST") {
            val body = (parseConfigValue(profile.configJson, "body") ?: "{}")
                .toRequestBody("application/json".toMediaType())
            builder.post(body)
        } else builder.get()
        runCatching { client.newCall(builder.build()).execute() }
            .fold(
                onSuccess = { response ->
                    response.use {
                        if (it.isSuccessful) {
                            updateTest(profile, "")
                            ProviderResult.Supported(Unit)
                        } else {
                            val message = when (it.code) {
                                401, 403 -> "鉴权失败，请检查凭据"
                                429 -> "请求受限，请稍后重试"
                                else -> "连接失败（HTTP ${it.code}）"
                            }
                            updateTest(profile, message)
                            ProviderResult.Failure(message, it.code, it.code == 429 || it.code >= 500)
                        }
                    }
                },
                onFailure = {
                    val message = it.message ?: "连接超时"
                    updateTest(profile, message)
                    ProviderResult.Failure(message, retryable = true)
                }
            )
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val item = dao.get(id) ?: return
        dao.upsert(item.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) {
        val item = dao.get(id) ?: return
        ProviderRegistry.descriptor(item.providerId)?.credentials?.forEach {
            credentials.remove("${item.credentialRef}:${it.id}")
        }
        dao.delete(id)
    }

    private suspend fun updateTest(profile: ProviderProfileEntity, error: String) {
        dao.upsert(profile.copy(lastTestedAt = System.currentTimeMillis(), lastError = error, updatedAt = System.currentTimeMillis()))
    }

    private fun parseConfigValue(config: String, key: String): String? {
        if (config.isBlank()) return null
        val marker = "\"$key\":\""
        val start = config.indexOf(marker)
        if (start < 0) return null
        val from = start + marker.length
        val end = config.indexOf('"', from)
        return if (end > from) config.substring(from, end).replace("\\n", "\n").replace("\\\"", "\"") else null
    }
}
