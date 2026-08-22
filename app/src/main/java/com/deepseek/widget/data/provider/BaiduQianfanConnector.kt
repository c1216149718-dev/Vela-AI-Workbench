package com.deepseek.widget.data.provider

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class BaiduQianfanConnector(
    private val client: OkHttpClient
) : ProviderConnector {
    override val descriptor: ProviderDescriptor = ProviderRegistry.descriptor(ProviderRegistry.QIANFAN.value)!!
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun testConnection(
        credentials: Map<String, String>,
        config: CustomConnectorConfig?
    ): ProviderResult<Unit> {
        val today = LocalDate.now(ZoneOffset.UTC)
        return when (val result = syncDailyUsage(today.minusDays(1), today.minusDays(1), credentials)) {
            is ProviderResult.Supported -> ProviderResult.Supported(Unit)
            is ProviderResult.PartialFailure -> ProviderResult.PartialFailure(Unit, result.message, result.errorType)
            is ProviderResult.Failure -> result
            is ProviderResult.PermissionRequired -> result
            is ProviderResult.Unsupported -> result
        }
    }

    override suspend fun syncBalance(credentials: Map<String, String>): ProviderResult<List<ProviderBalance>> {
        val keys = keys(credentials, ProviderCapability.BALANCE) ?: return missingKeys(ProviderCapability.BALANCE)
        val host = "billing.baidubce.com"
        val path = "/v1/finance/cash/balance"
        val signed = BceV1Signer.sign(keys.first, keys.second, "POST", path, emptyMap(), host)
        val request = Request.Builder()
            .url("https://$host$path")
            .header("Host", host)
            .header("x-bce-date", signed.timestamp)
            .header("Authorization", signed.authorization)
            .header("Content-Type", "application/json; charset=utf-8")
            .post(ByteArray(0).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return when (val result = executeJson(request, "百度云余额")) {
            is ProviderResult.Supported -> {
                val amount = result.value.primitive("cashBalance")?.contentOrNull?.toBigDecimalOrNull()
                    ?: return ProviderResult.Failure("余额响应缺少 cashBalance", errorType = SyncErrorType.INVALID_RESPONSE)
                ProviderResult.Supported(
                    listOf(
                        ProviderBalance(
                            currency = "CNY",
                            amount = amount,
                            accountFingerprint = sha256Hex(keys.first).take(12),
                            cloudAccount = true
                        )
                    )
                )
            }
            else -> result.castResult()
        }
    }

    override suspend fun syncDailyUsage(
        startDate: LocalDate,
        endDate: LocalDate,
        credentials: Map<String, String>,
        cursor: String?
    ): ProviderResult<SyncPage<DailyUsagePoint>> {
        val keys = keys(credentials, ProviderCapability.HISTORICAL_USAGE)
            ?: return missingKeys(ProviderCapability.HISTORICAL_USAGE)
        val day = cursor?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() } ?: startDate
        if (day.isAfter(endDate)) return ProviderResult.Supported(SyncPage(emptyList()))

        val host = "qianfan.baidubce.com"
        val path = "/v2/service"
        val query = mapOf("Action" to "DescribeServiceMetric")
        val body = buildJsonObject {
            put("startTime", day.atStartOfDay().toInstant(ZoneOffset.UTC).formatIso())
            put("endTime", day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusSeconds(1).formatIso())
        }.toString()
        val signed = BceV1Signer.sign(keys.first, keys.second, "POST", path, query, host)
        val request = Request.Builder()
            .url("https://$host$path?Action=DescribeServiceMetric")
            .header("Host", host)
            .header("x-bce-date", signed.timestamp)
            .header("Authorization", signed.authorization)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return when (val result = executeJson(request, "千帆服务指标")) {
            is ProviderResult.Supported -> {
                val serviceList = ((result.value["result"] as? JsonObject)?.get("serviceList") as? JsonArray).orEmpty()
                val rows = serviceList.mapNotNull { element ->
                    val service = element as? JsonObject ?: return@mapNotNull null
                    val model = service.primitive("serviceName")?.contentOrNull
                        ?: service.primitive("serviceId")?.contentOrNull
                        ?: "unknown"
                    val metrics = (service["appList"] as? JsonArray).orEmpty().mapNotNull { app ->
                        val appObject = app as? JsonObject ?: return@mapNotNull null
                        (appObject["metric"] as? JsonObject) ?: (appObject["metrics"] as? JsonObject)
                    }
                    if (metrics.isEmpty()) return@mapNotNull null
                    DailyUsagePoint(
                        date = day,
                        model = model,
                        requests = metrics.sumNullable("callTotal"),
                        inputTokens = metrics.sumNullable("inputTokensTotal"),
                        outputTokens = metrics.sumNullable("outputTokensTotal"),
                        totalTokens = metrics.sumNullable("tokensTotal"),
                        sourceId = "qianfan-service-metric"
                    )
                }
                val next = day.plusDays(1).takeIf { !it.isAfter(endDate) }?.toString()
                ProviderResult.Supported(SyncPage(rows, next))
            }
            else -> result.castResult()
        }
    }

    override suspend fun syncModelUsage(
        startDate: LocalDate,
        endDate: LocalDate,
        credentials: Map<String, String>,
        cursor: String?
    ): ProviderResult<SyncPage<ModelUsagePoint>> = when (
        val daily = syncDailyUsage(startDate, endDate, credentials, cursor)
    ) {
        is ProviderResult.Supported -> ProviderResult.Supported(
            SyncPage(
                items = daily.value.items.map { point ->
                    ModelUsagePoint(
                        model = point.model,
                        requests = point.requests,
                        inputTokens = point.inputTokens,
                        outputTokens = point.outputTokens,
                        totalTokens = point.totalTokens,
                        sourceId = point.sourceId
                    )
                },
                nextCursor = daily.value.nextCursor,
                refreshedAt = daily.value.refreshedAt
            )
        )
        is ProviderResult.PartialFailure -> ProviderResult.PartialFailure(
            SyncPage(
                daily.value.items.map { point ->
                    ModelUsagePoint(
                        model = point.model,
                        requests = point.requests,
                        inputTokens = point.inputTokens,
                        outputTokens = point.outputTokens,
                        totalTokens = point.totalTokens,
                        sourceId = point.sourceId
                    )
                },
                daily.value.nextCursor,
                daily.value.refreshedAt
            ),
            daily.message,
            daily.errorType
        )
        is ProviderResult.Failure -> daily
        is ProviderResult.PermissionRequired -> daily
        is ProviderResult.Unsupported -> daily
    }

    private fun keys(credentials: Map<String, String>, capability: ProviderCapability): Pair<String, String>? {
        val accessKey = credentials["access_key"].orEmpty()
        val secretKey = credentials["secret_key"].orEmpty()
        return if (accessKey.isBlank() || secretKey.isBlank()) null else accessKey to secretKey
    }

    private fun missingKeys(capability: ProviderCapability): ProviderResult.PermissionRequired =
        ProviderResult.PermissionRequired(capability, "百度千帆该能力需要 BCE Access Key/Secret Key")

    private fun executeJson(request: Request, label: String): ProviderResult<JsonObject> = runCatching {
        client.newCall(request).execute()
    }.fold(
        onSuccess = { response ->
            response.use {
                if (!it.isSuccessful) return@use baiduHttpFailure(it.code)
                val root = runCatching { json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject }.getOrElse {
                    return@use ProviderResult.Failure("$label 响应格式无效", errorType = SyncErrorType.INVALID_RESPONSE)
                }
                val code = root.primitive("code")?.contentOrNull
                if (!code.isNullOrBlank()) {
                    return@use ProviderResult.Failure(
                        root.primitive("message")?.contentOrNull ?: "$label 返回 $code",
                        errorType = if (code.contains("Access", true)) SyncErrorType.PERMISSION else SyncErrorType.INVALID_RESPONSE
                    )
                }
                ProviderResult.Supported(root)
            }
        },
        onFailure = {
            ProviderResult.Failure(it.message ?: "$label 网络连接失败", retryable = true, errorType = SyncErrorType.NETWORK)
        }
    )
}

private fun Instant.formatIso(): String = DateTimeFormatter.ISO_INSTANT.format(this)

private fun JsonObject.primitive(name: String): JsonPrimitive? = this[name] as? JsonPrimitive

private fun List<JsonObject>.sumNullable(name: String): Long? {
    val values = mapNotNull { metric -> metric.primitive(name)?.contentOrNull?.toBigDecimalOrNull()?.toLong() }
    return values.takeIf { it.isNotEmpty() }?.sum()
}

private fun baiduHttpFailure(code: Int): ProviderResult.Failure = when (code) {
    401 -> ProviderResult.Failure("百度云鉴权失败，请检查 BCE 凭据", code, false, SyncErrorType.AUTH)
    403 -> ProviderResult.Failure("百度云凭据权限不足", code, false, SyncErrorType.PERMISSION)
    429 -> ProviderResult.Failure("百度云请求频率受限", code, true, SyncErrorType.RATE_LIMIT)
    else -> ProviderResult.Failure("百度云官方接口返回 HTTP $code", code, code >= 500, SyncErrorType.NETWORK)
}

@Suppress("UNCHECKED_CAST")
private fun <T> ProviderResult<*>.castResult(): ProviderResult<T> = this as ProviderResult<T>
