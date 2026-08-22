package com.deepseek.widget.data.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class ProviderConnectorRegistry private constructor(
    private val connectors: Map<ProviderId, ProviderConnector>
) {
    fun connector(id: String): ProviderConnector? = ProviderRegistry.canonicalId(id)?.let(connectors::get)

    companion object {
        fun create(client: OkHttpClient = defaultClient()): ProviderConnectorRegistry {
            val all = ProviderRegistry.presetDescriptors.associate { descriptor ->
                descriptor.id to when (descriptor.id) {
                    ProviderRegistry.DEEPSEEK -> DeepSeekConnector(client)
                    ProviderRegistry.SILICON_FLOW -> SiliconFlowConnector(client)
                    ProviderRegistry.MOONSHOT -> MoonshotConnector(client)
                    ProviderRegistry.BAILIAN -> AlibabaBailianConnector(client)
                    ProviderRegistry.TOKENHUB -> TencentTokenHubConnector(client)
                    ProviderRegistry.QIANFAN -> BaiduQianfanConnector(client)
                    ProviderRegistry.OPENAI -> OpenAiConnector(client)
                    else -> CapabilityHonestConnector(descriptor, client)
                }
            }
            return ProviderConnectorRegistry(all)
        }

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}

private abstract class BearerConnector(
    final override val descriptor: ProviderDescriptor,
    protected val client: OkHttpClient
) : ProviderConnector {
    protected val json = Json { ignoreUnknownKeys = true }

    protected fun get(url: String, key: String, extraHeaders: Map<String, String> = emptyMap()): ProviderResult<JsonObject> {
        if (key.isBlank()) return ProviderResult.PermissionRequired(ProviderCapability.CONNECTION, "缺少必需凭据")
        val request = Request.Builder().url(url).header("Authorization", "Bearer $key").apply {
            extraHeaders.filterValues { it.isNotBlank() }.forEach { (name, value) -> header(name, value) }
        }.build()
        return runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { response ->
                response.use {
                    if (!it.isSuccessful) return@use httpFailure(it.code)
                    val body = it.body?.string().orEmpty()
                    runCatching { json.parseToJsonElement(body).jsonObject }
                        .fold({ parsed -> ProviderResult.Supported(parsed) }, { ProviderResult.Failure("官方响应格式无效", errorType = SyncErrorType.INVALID_RESPONSE) })
                }
            },
            onFailure = { ProviderResult.Failure(it.message ?: "网络连接失败", retryable = true, errorType = SyncErrorType.NETWORK) }
        )
    }

    override suspend fun testConnection(credentials: Map<String, String>, config: CustomConnectorConfig?): ProviderResult<Unit> =
        when (val result = get(descriptor.testUrl ?: return ProviderResult.Unsupported(ProviderCapability.CONNECTION, "需要云签名凭据"), primaryKey(credentials))) {
            is ProviderResult.Supported -> ProviderResult.Supported(Unit)
            is ProviderResult.Failure -> result
            is ProviderResult.PermissionRequired -> result
            is ProviderResult.Unsupported -> result
            is ProviderResult.PartialFailure -> ProviderResult.PartialFailure(Unit, result.message, result.errorType)
        }

    protected open fun primaryKey(values: Map<String, String>) = values["api_key"].orEmpty().ifBlank { values["admin_key"].orEmpty() }
}

private class DeepSeekConnector(client: OkHttpClient) : BearerConnector(ProviderRegistry.descriptor(ProviderRegistry.DEEPSEEK.value)!!, client) {
    override suspend fun syncBalance(credentials: Map<String, String>): ProviderResult<List<ProviderBalance>> =
        when (val result = get("https://api.deepseek.com/user/balance", primaryKey(credentials))) {
            is ProviderResult.Supported -> {
                val values = result.value.arrayAt("balance_infos").mapNotNull { item ->
                    val obj = item as? JsonObject ?: return@mapNotNull null
                    val currency = obj.stringAt("currency") ?: return@mapNotNull null
                    val amount = obj.decimalAt("total_balance") ?: return@mapNotNull null
                    ProviderBalance(currency.uppercase(), amount)
                }
                if (values.isEmpty()) ProviderResult.Failure("余额响应缺少 balance_infos", errorType = SyncErrorType.INVALID_RESPONSE) else ProviderResult.Supported(values)
            }
            else -> result.cast()
        }
}

private class SiliconFlowConnector(client: OkHttpClient) : BearerConnector(ProviderRegistry.descriptor(ProviderRegistry.SILICON_FLOW.value)!!, client) {
    override suspend fun syncBalance(credentials: Map<String, String>): ProviderResult<List<ProviderBalance>> =
        when (val result = get("https://api.siliconflow.cn/v1/user/info", primaryKey(credentials))) {
            is ProviderResult.Supported -> {
                val data = result.value["data"] as? JsonObject ?: result.value
                val amount = data.decimalAt("balance") ?: data.decimalAt("totalBalance") ?: data.decimalAt("chargeBalance")
                if (amount == null) ProviderResult.Failure("余额响应缺少官方余额字段", errorType = SyncErrorType.INVALID_RESPONSE)
                else ProviderResult.Supported(listOf(ProviderBalance("CNY", amount)))
            }
            else -> result.cast()
        }
}

private class MoonshotConnector(client: OkHttpClient) : BearerConnector(ProviderRegistry.descriptor(ProviderRegistry.MOONSHOT.value)!!, client) {
    override suspend fun syncBalance(credentials: Map<String, String>): ProviderResult<List<ProviderBalance>> =
        when (val result = get("https://api.moonshot.cn/v1/users/me/balance", primaryKey(credentials))) {
            is ProviderResult.Supported -> {
                val data = result.value["data"] as? JsonObject ?: result.value
                val amount = data.decimalAt("available_balance") ?: data.decimalAt("balance")
                if (amount == null) ProviderResult.Failure("余额响应缺少 available_balance", errorType = SyncErrorType.INVALID_RESPONSE)
                else ProviderResult.Supported(listOf(ProviderBalance("CNY", amount)))
            }
            else -> result.cast()
        }
}

private class OpenAiConnector(client: OkHttpClient) : BearerConnector(ProviderRegistry.descriptor(ProviderRegistry.OPENAI.value)!!, client) {
    private fun headers(credentials: Map<String, String>) = buildMap {
        credentials["organization_id"]?.let { put("OpenAI-Organization", it) }
        credentials["project_id"]?.let { put("OpenAI-Project", it) }
    }

    override suspend fun testConnection(credentials: Map<String, String>, config: CustomConnectorConfig?): ProviderResult<Unit> =
        when (val result = get("https://api.openai.com/v1/models", primaryKey(credentials), headers(credentials))) {
            is ProviderResult.Supported -> ProviderResult.Supported(Unit)
            else -> result.cast()
        }

    override suspend fun syncActualCost(startDate: LocalDate, endDate: LocalDate, credentials: Map<String, String>, cursor: String?): ProviderResult<SyncPage<ActualCostPoint>> {
        val start = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val end = endDate.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val cursorPart = cursor?.takeIf { it.isNotBlank() }?.let { "&page=$it" }.orEmpty()
        return when (val result = get("https://api.openai.com/v1/organization/costs?start_time=$start&end_time=$end&bucket_width=1d$cursorPart", primaryKey(credentials), headers(credentials))) {
            is ProviderResult.Supported -> {
                val points = result.value.arrayAt("data").flatMap { bucketElement ->
                    val bucket = bucketElement as? JsonObject ?: return@flatMap emptyList()
                    val date = bucket.longAt("start_time")?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: return@flatMap emptyList()
                    bucket.arrayAt("results").mapNotNull { resultElement ->
                        val item = resultElement as? JsonObject ?: return@mapNotNull null
                        val amountObj = item["amount"] as? JsonObject
                        val amount = amountObj?.decimalAt("value") ?: item.decimalAt("amount") ?: return@mapNotNull null
                        val currency = amountObj?.stringAt("currency") ?: item.stringAt("currency") ?: "USD"
                        ActualCostPoint(date, currency.uppercase(), amount, item.stringAt("line_item") ?: "OpenAI")
                    }
                }
                ProviderResult.Supported(SyncPage(points, result.value.stringAt("next_page")))
            }
            else -> result.cast()
        }
    }

    override suspend fun syncDailyUsage(startDate: LocalDate, endDate: LocalDate, credentials: Map<String, String>, cursor: String?): ProviderResult<SyncPage<DailyUsagePoint>> {
        val start = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val end = endDate.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val cursorPart = cursor?.takeIf { it.isNotBlank() }?.let { "&page=$it" }.orEmpty()
        return when (val result = get("https://api.openai.com/v1/organization/usage/completions?start_time=$start&end_time=$end&bucket_width=1d&group_by=model$cursorPart", primaryKey(credentials), headers(credentials))) {
            is ProviderResult.Supported -> {
                val points = result.value.arrayAt("data").flatMap { bucketElement ->
                    val bucket = bucketElement as? JsonObject ?: return@flatMap emptyList()
                    val date = bucket.longAt("start_time")?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: return@flatMap emptyList()
                    bucket.arrayAt("results").mapNotNull { resultElement ->
                        val item = resultElement as? JsonObject ?: return@mapNotNull null
                        val input = item.longAt("input_tokens")
                        val output = item.longAt("output_tokens")
                        DailyUsagePoint(
                            date = date,
                            model = item.stringAt("model") ?: "unknown",
                            requests = item.longAt("num_model_requests"),
                            inputTokens = input,
                            outputTokens = output,
                            cachedTokens = item.longAt("input_cached_tokens"),
                            totalTokens = listOfNotNull(input, output).takeIf { it.isNotEmpty() }?.sum(),
                            sourceId = "openai-usage"
                        )
                    }
                }
                ProviderResult.Supported(SyncPage(points, result.value.stringAt("next_page")))
            }
            else -> result.cast()
        }
    }

    override suspend fun syncModelUsage(startDate: LocalDate, endDate: LocalDate, credentials: Map<String, String>, cursor: String?): ProviderResult<SyncPage<ModelUsagePoint>> =
        when (val daily = syncDailyUsage(startDate, endDate, credentials, cursor)) {
            is ProviderResult.Supported -> {
                val rows = daily.value.items.groupBy { it.model }.map { (model, items) ->
                    ModelUsagePoint(
                        model = model,
                        requests = items.mapNotNull { it.requests }.takeIf { it.isNotEmpty() }?.sum(),
                        inputTokens = items.mapNotNull { it.inputTokens }.takeIf { it.isNotEmpty() }?.sum(),
                        outputTokens = items.mapNotNull { it.outputTokens }.takeIf { it.isNotEmpty() }?.sum(),
                        cachedTokens = items.mapNotNull { it.cachedTokens }.takeIf { it.isNotEmpty() }?.sum(),
                        totalTokens = items.mapNotNull { it.totalTokens }.takeIf { it.isNotEmpty() }?.sum(),
                        sourceId = "openai-usage"
                    )
                }
                ProviderResult.Supported(SyncPage(rows, daily.value.nextCursor))
            }
            else -> daily.cast()
        }
}

/**
 * For cloud-signed and bill-import providers, do not pretend a generic bearer request proves
 * billing access. Connection can still be tested where an official inference endpoint exists;
 * billing methods remain explicitly permission-gated until the required AK/SK fields are present.
 */
private class CapabilityHonestConnector(descriptor: ProviderDescriptor, client: OkHttpClient) : BearerConnector(descriptor, client) {
    override suspend fun testConnection(credentials: Map<String, String>, config: CustomConnectorConfig?): ProviderResult<Unit> {
        val url = descriptor.testUrl
        return if (url.isNullOrBlank()) {
            ProviderResult.PermissionRequired(ProviderCapability.CONNECTION, "该平台需要官方云签名参数；配置已保存")
        } else super.testConnection(credentials, config)
    }
}

private fun httpFailure(code: Int): ProviderResult.Failure = when (code) {
    401 -> ProviderResult.Failure("鉴权失败，请检查凭据", code, false, SyncErrorType.AUTH)
    403 -> ProviderResult.Failure("凭据权限不足", code, false, SyncErrorType.PERMISSION)
    429 -> ProviderResult.Failure("请求频率受限", code, true, SyncErrorType.RATE_LIMIT)
    else -> ProviderResult.Failure("官方接口返回 HTTP $code", code, code >= 500, SyncErrorType.NETWORK)
}

@Suppress("UNCHECKED_CAST")
private fun <T> ProviderResult<*>.cast(): ProviderResult<T> = this as ProviderResult<T>
private fun JsonObject.arrayAt(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
private fun JsonObject.stringAt(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.decimalAt(key: String): BigDecimal? = stringAt(key)?.toBigDecimalOrNull()
private fun JsonObject.longAt(key: String): Long? = stringAt(key)?.toLongOrNull()
