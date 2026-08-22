package com.deepseek.widget.data.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

internal class AlibabaBailianConnector(
    private val client: OkHttpClient
) : ProviderConnector {
    override val descriptor: ProviderDescriptor = ProviderRegistry.descriptor(ProviderRegistry.BAILIAN.value)!!
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun testConnection(
        credentials: Map<String, String>,
        config: CustomConnectorConfig?
    ): ProviderResult<Unit> {
        val accessKey = credentials["access_key"].orEmpty()
        val secretKey = credentials["secret_key"].orEmpty()
        if (accessKey.isNotBlank() && secretKey.isNotBlank()) {
            return when (val balance = syncBalance(credentials)) {
                is ProviderResult.Supported -> ProviderResult.Supported(Unit)
                is ProviderResult.PartialFailure -> ProviderResult.PartialFailure(Unit, balance.message, balance.errorType)
                is ProviderResult.Failure -> balance
                is ProviderResult.PermissionRequired -> balance
                is ProviderResult.Unsupported -> balance
            }
        }
        val apiKey = credentials["api_key"].orEmpty()
        if (apiKey.isBlank()) return ProviderResult.PermissionRequired(ProviderCapability.CONNECTION, "缺少百炼 API Key")
        val request = Request.Builder()
            .url("https://dashscope.aliyuncs.com/api/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        return execute(request, "百炼模型连接").mapUnit()
    }

    override suspend fun syncBalance(credentials: Map<String, String>): ProviderResult<List<ProviderBalance>> {
        val accessKey = credentials["access_key"].orEmpty()
        val secretKey = credentials["secret_key"].orEmpty()
        if (accessKey.isBlank() || secretKey.isBlank()) {
            return ProviderResult.PermissionRequired(ProviderCapability.BALANCE, "阿里云账户余额需要 Access Key/Secret Key")
        }
        val signed = AliyunRpcSigner.sign(accessKey, secretKey, "QueryAccountBalance")
        val request = Request.Builder()
            .url("https://business.aliyuncs.com/?${signed.query}")
            .get()
            .build()
        return when (val result = execute(request, "阿里云账户余额")) {
            is ProviderResult.Supported -> {
                val success = result.value.primitive("Success")?.contentOrNull?.toBooleanStrictOrNull()
                if (success == false) {
                    return ProviderResult.Failure(
                        result.value.primitive("Message")?.contentOrNull ?: "阿里云余额查询失败",
                        errorType = SyncErrorType.PERMISSION
                    )
                }
                val data = result.value["Data"] as? JsonObject
                    ?: return ProviderResult.Failure("阿里云余额响应缺少 Data", errorType = SyncErrorType.INVALID_RESPONSE)
                val amount = data.primitive("AvailableAmount")?.contentOrNull?.toBigDecimalOrNull()
                    ?: return ProviderResult.Failure("阿里云余额响应缺少 AvailableAmount", errorType = SyncErrorType.INVALID_RESPONSE)
                val currency = data.primitive("Currency")?.contentOrNull.orEmpty().ifBlank { "CNY" }
                ProviderResult.Supported(
                    listOf(
                        ProviderBalance(
                            currency = currency.uppercase(),
                            amount = amount,
                            accountFingerprint = sha256Hex(accessKey).take(12),
                            cloudAccount = true
                        )
                    )
                )
            }
            else -> result.castAliyun()
        }
    }

    private fun execute(request: Request, label: String): ProviderResult<JsonObject> = runCatching {
        client.newCall(request).execute()
    }.fold(
        onSuccess = { response ->
            response.use {
                if (!it.isSuccessful) return@use aliyunHttpFailure(it.code)
                runCatching { json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject }
                    .fold(
                        onSuccess = { body -> ProviderResult.Supported(body) },
                        onFailure = { ProviderResult.Failure("$label 响应格式无效", errorType = SyncErrorType.INVALID_RESPONSE) }
                    )
            }
        },
        onFailure = {
            ProviderResult.Failure(it.message ?: "$label 网络连接失败", retryable = true, errorType = SyncErrorType.NETWORK)
        }
    )
}

private fun JsonObject.primitive(name: String): JsonPrimitive? = this[name] as? JsonPrimitive

private fun ProviderResult<JsonObject>.mapUnit(): ProviderResult<Unit> = when (this) {
    is ProviderResult.Supported -> ProviderResult.Supported(Unit)
    is ProviderResult.PartialFailure -> ProviderResult.PartialFailure(Unit, message, errorType)
    is ProviderResult.Failure -> this
    is ProviderResult.PermissionRequired -> this
    is ProviderResult.Unsupported -> this
}

private fun aliyunHttpFailure(code: Int): ProviderResult.Failure = when (code) {
    401 -> ProviderResult.Failure("阿里云鉴权失败，请检查 Access Key", code, false, SyncErrorType.AUTH)
    403 -> ProviderResult.Failure("阿里云凭据缺少 BSS 只读权限", code, false, SyncErrorType.PERMISSION)
    429 -> ProviderResult.Failure("阿里云请求频率受限", code, true, SyncErrorType.RATE_LIMIT)
    else -> ProviderResult.Failure("阿里云官方接口返回 HTTP $code", code, code >= 500, SyncErrorType.NETWORK)
}

@Suppress("UNCHECKED_CAST")
private fun <T> ProviderResult<*>.castAliyun(): ProviderResult<T> = this as ProviderResult<T>
