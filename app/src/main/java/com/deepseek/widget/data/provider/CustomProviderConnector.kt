package com.deepseek.widget.data.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Script-free runtime for user-mapped JSON endpoints. Only dot-separated object paths run. */
class CustomProviderConnector private constructor(
    private val raw: JsonObject,
    private val client: OkHttpClient
) : ProviderConnector {
    override val descriptor = ProviderRegistry.descriptor(ProviderRegistry.CUSTOM.value)!!
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun testConnection(credentials: Map<String, String>, config: CustomConnectorConfig?): ProviderResult<Unit> =
        execute("testUrl", credentials).mapUnit()

    override suspend fun syncBalance(credentials: Map<String, String>): ProviderResult<List<ProviderBalance>> =
        when (val result = execute("balanceUrl", credentials)) {
            is ProviderResult.Supported -> {
                val mapping = mappings()
                val amount = result.value.path(mapping["value"] ?: "balance").text()?.toBigDecimalOrNull()
                if (amount == null) ProviderResult.Failure("自定义余额字段映射无效", errorType = SyncErrorType.INVALID_RESPONSE)
                else ProviderResult.Supported(listOf(ProviderBalance(result.value.path(mapping["currency"] ?: "currency").text() ?: "USD", amount)))
            }
            else -> result.castCustom()
        }

    override suspend fun syncDailyUsage(startDate: LocalDate, endDate: LocalDate, credentials: Map<String, String>, cursor: String?): ProviderResult<SyncPage<DailyUsagePoint>> =
        mapUsage("dailyUsageUrl", credentials, modelMode = false)

    override suspend fun syncModelUsage(startDate: LocalDate, endDate: LocalDate, credentials: Map<String, String>, cursor: String?): ProviderResult<SyncPage<ModelUsagePoint>> =
        when (val result = mapRows("modelUsageUrl", credentials)) {
            is ProviderResult.Supported -> {
                val m = mappings()
                ProviderResult.Supported(SyncPage(result.value.mapNotNull { row ->
                    val model = row.path(m["model"] ?: "model").text() ?: return@mapNotNull null
                    ModelUsagePoint(model, row.path(m["currency"] ?: "currency").text() ?: "", row.decimal(m["cost"] ?: "cost"), row.long(m["requests"] ?: "requests"), totalTokens = row.long(m["tokens"] ?: "total_tokens"))
                }))
            }
            else -> result.castCustom()
        }

    override suspend fun syncActualCost(startDate: LocalDate, endDate: LocalDate, credentials: Map<String, String>, cursor: String?): ProviderResult<SyncPage<ActualCostPoint>> =
        when (val result = mapRows("actualCostUrl", credentials)) {
            is ProviderResult.Supported -> {
                val m = mappings()
                ProviderResult.Supported(SyncPage(result.value.mapNotNull { row ->
                    val date = row.path(m["date"] ?: "date").text()?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return@mapNotNull null
                    ActualCostPoint(date, row.path(m["currency"] ?: "currency").text() ?: "USD", row.decimal(m["cost"] ?: "cost"))
                }))
            }
            else -> result.castCustom()
        }

    private fun mapUsage(key: String, credentials: Map<String, String>, modelMode: Boolean): ProviderResult<SyncPage<DailyUsagePoint>> =
        when (val result = mapRows(key, credentials)) {
            is ProviderResult.Supported -> {
                val m = mappings()
                ProviderResult.Supported(SyncPage(result.value.mapNotNull { row ->
                    val date = row.path(m["date"] ?: "date").text()?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return@mapNotNull null
                    DailyUsagePoint(date, row.path(m["model"] ?: "model").text() ?: "__all__", row.path(m["currency"] ?: "currency").text() ?: "", row.decimal(m["cost"] ?: "cost"), row.long(m["requests"] ?: "requests"), totalTokens = row.long(m["tokens"] ?: "total_tokens"))
                }))
            }
            else -> result.castCustom()
        }

    private fun mapRows(key: String, credentials: Map<String, String>): ProviderResult<List<JsonElement>> = when (val result = execute(key, credentials)) {
        is ProviderResult.Supported -> {
            val list = result.value.path(mappings()["list"] ?: "data")
            ProviderResult.Supported((list as? JsonArray)?.toList() ?: listOf(list))
        }
        else -> result.castCustom()
    }

    private fun execute(key: String, credentials: Map<String, String>): ProviderResult<JsonElement> {
        val url = raw.text(key).orEmpty()
        if (url.isBlank()) return ProviderResult.Unsupported(ProviderCapability.HISTORICAL_USAGE, "自定义端点未配置")
        if (!url.startsWith("https://")) return ProviderResult.Failure("自定义端点必须使用 HTTPS")
        val method = raw.text("method")?.uppercase() ?: "GET"
        val builder = Request.Builder().url(url)
        val secret = credentials["api_key"].orEmpty()
        if (secret.isNotBlank()) builder.header(raw.text("authHeader") ?: "Authorization", (raw.text("authPrefix") ?: "Bearer ") + secret)
        if (method == "POST") builder.post(raw.text("body").orEmpty().ifBlank { "{}" }.toRequestBody("application/json".toMediaType())) else builder.get()
        return runCatching { client.newCall(builder.build()).execute() }.fold(
            { response -> response.use {
                if (!it.isSuccessful) ProviderResult.Failure("自定义接口 HTTP ${it.code}", it.code, it.code == 429 || it.code >= 500)
                else runCatching { json.parseToJsonElement(it.body?.string().orEmpty()) }.fold({ value -> ProviderResult.Supported(value) }, { ProviderResult.Failure("自定义响应不是有效 JSON", errorType = SyncErrorType.INVALID_RESPONSE) })
            } },
            { ProviderResult.Failure(it.message ?: "自定义接口网络错误", retryable = true, errorType = SyncErrorType.NETWORK) }
        )
    }

    private fun mappings(): Map<String, String> = raw.text("mapping").orEmpty().split(';', '\n').mapNotNull { token ->
        val parts = token.split('=', limit = 2); if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
    }.toMap()

    companion object {
        fun from(configJson: String): CustomProviderConnector? = runCatching {
            val raw = Json.parseToJsonElement(configJson).let { it as JsonObject }
            CustomProviderConnector(raw, OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build())
        }.getOrNull()
    }
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonElement.path(path: String): JsonElement {
    if (path.isBlank()) return this
    return path.split('.').fold(this) { current, part -> (current as? JsonObject)?.get(part) ?: JsonPrimitive("") }
}
private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.contentOrNull
private fun JsonElement.decimal(path: String): BigDecimal = path(path).text()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
private fun JsonElement.long(path: String): Long? = path(path).text()?.toLongOrNull()
private fun ProviderResult<JsonElement>.mapUnit(): ProviderResult<Unit> = when (this) {
    is ProviderResult.Supported -> ProviderResult.Supported(Unit)
    is ProviderResult.PartialFailure -> ProviderResult.PartialFailure(Unit, message, errorType)
    else -> castCustom()
}
@Suppress("UNCHECKED_CAST") private fun <T> ProviderResult<*>.castCustom(): ProviderResult<T> = this as ProviderResult<T>
