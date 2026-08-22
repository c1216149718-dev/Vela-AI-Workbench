package com.deepseek.widget.data.provider

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

internal class TencentTokenHubConnector(
    private val client: OkHttpClient
) : ProviderConnector {
    override val descriptor: ProviderDescriptor = ProviderRegistry.descriptor(ProviderRegistry.TOKENHUB.value)!!
    private val json = Json { ignoreUnknownKeys = true }
    private val host = "tokenhub.tencentcloudapi.com"

    override suspend fun testConnection(credentials: Map<String, String>, config: CustomConnectorConfig?): ProviderResult<Unit> {
        val today = LocalDate.now()
        return when (val result = syncDailyUsage(today.minusDays(1), today, credentials)) {
            is ProviderResult.Supported -> ProviderResult.Supported(Unit)
            is ProviderResult.PartialFailure -> ProviderResult.PartialFailure(Unit, result.message, result.errorType)
            is ProviderResult.Failure -> result
            is ProviderResult.PermissionRequired -> result
            is ProviderResult.Unsupported -> result
        }
    }

    override suspend fun syncDailyUsage(
        startDate: LocalDate,
        endDate: LocalDate,
        credentials: Map<String, String>,
        cursor: String?
    ): ProviderResult<SyncPage<DailyUsagePoint>> {
        val secretId = credentials["secret_id"].orEmpty()
        val secretKey = credentials["secret_key"].orEmpty()
        if (secretId.isBlank() || secretKey.isBlank()) {
            return ProviderResult.PermissionRequired(ProviderCapability.HISTORICAL_USAGE, "TokenHub 历史用量需要 SecretId/SecretKey")
        }
        val region = credentials["region"].orEmpty().ifBlank { "ap-guangzhou" }
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val zone = ZoneOffset.ofHours(8)
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val payload = buildJsonObject {
            put("Dimension", "model")
            put("StartTime", formatter.format(startDate.atStartOfDay().atOffset(zone)))
            put("EndTime", formatter.format(endDate.plusDays(1).atStartOfDay().atOffset(zone)))
            put("MetricType", "tokens")
            put("Period", 86400)
            put("Offset", offset)
            put("ShowAll", false)
        }.toString()
        val signed = TencentTc3Signer.sign(secretId, secretKey, "tokenhub", host, "DescribeUsageRankList", "2026-03-22", region, payload)
        val request = Request.Builder().url("https://$host/")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Host", host)
            .header("Authorization", signed.authorization)
            .header("X-TC-Action", signed.action)
            .header("X-TC-Version", signed.version)
            .header("X-TC-Timestamp", signed.timestamp.toString())
            .header("X-TC-Region", signed.region)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return execute(request, offset)
    }

    override suspend fun syncModelUsage(
        startDate: LocalDate,
        endDate: LocalDate,
        credentials: Map<String, String>,
        cursor: String?
    ): ProviderResult<SyncPage<ModelUsagePoint>> = when (val daily = syncDailyUsage(startDate, endDate, credentials, cursor)) {
        is ProviderResult.Supported -> ProviderResult.Supported(
            SyncPage(
                daily.value.items.groupBy { it.model }.map { (model, rows) ->
                    ModelUsagePoint(
                        model = model,
                        inputTokens = rows.mapNotNull { it.inputTokens }.takeIf { it.isNotEmpty() }?.sum(),
                        outputTokens = rows.mapNotNull { it.outputTokens }.takeIf { it.isNotEmpty() }?.sum(),
                        totalTokens = rows.mapNotNull { it.totalTokens }.takeIf { it.isNotEmpty() }?.sum(),
                        sourceId = "tokenhub-usage"
                    )
                },
                daily.value.nextCursor
            )
        )
        is ProviderResult.PartialFailure -> ProviderResult.PartialFailure(
            SyncPage(
                daily.value.items.groupBy { it.model }.map { (model, rows) ->
                    ModelUsagePoint(
                        model = model,
                        inputTokens = rows.mapNotNull { it.inputTokens }.takeIf { it.isNotEmpty() }?.sum(),
                        outputTokens = rows.mapNotNull { it.outputTokens }.takeIf { it.isNotEmpty() }?.sum(),
                        cachedTokens = rows.mapNotNull { it.cachedTokens }.takeIf { it.isNotEmpty() }?.sum(),
                        totalTokens = rows.mapNotNull { it.totalTokens }.takeIf { it.isNotEmpty() }?.sum(),
                        sourceId = "tokenhub-usage"
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

    private fun execute(request: Request, offset: Int): ProviderResult<SyncPage<DailyUsagePoint>> = runCatching {
        client.newCall(request).execute()
    }.fold(
        onSuccess = { response ->
            response.use {
                if (!it.isSuccessful) return@use cloudHttpFailure(it.code)
                val body = it.body?.string().orEmpty()
                val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse {
                    return@use ProviderResult.Failure("TokenHub 响应格式无效", errorType = SyncErrorType.INVALID_RESPONSE)
                }
                val responseObject = root["Response"] as? JsonObject
                    ?: return@use ProviderResult.Failure("TokenHub 响应缺少 Response", errorType = SyncErrorType.INVALID_RESPONSE)
                val error = responseObject["Error"] as? JsonObject
                if (error != null) {
                    return@use ProviderResult.Failure(
                        (error["Message"] as? JsonPrimitive)?.contentOrNull ?: "TokenHub 返回错误",
                        errorType = SyncErrorType.PERMISSION
                    )
                }
                val timestamps = (responseObject["Timestamps"] as? JsonArray).orEmpty().mapNotNull { element ->
                    (element as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                }
                val rows = (responseObject["TopList"] as? JsonArray).orEmpty().flatMap { element ->
                    val item = element as? JsonObject ?: return@flatMap emptyList()
                    val model = (item["Name"] as? JsonPrimitive)?.contentOrNull
                        ?: (item["Key"] as? JsonPrimitive)?.contentOrNull
                        ?: "unknown"
                    val series = item["Series"] as? JsonObject ?: return@flatMap emptyList()
                    val totals = series.longSeries("TotalToken", json)
                    val inputs = series.longSeries("InputTotalToken", json)
                    val outputs = series.longSeries("OutputTotalToken", json)
                    val cached = series.longSeries("CacheTotalToken", json)
                    timestamps.mapIndexedNotNull { index, epoch ->
                        val total = totals.getOrNull(index)
                        val input = inputs.getOrNull(index)
                        val output = outputs.getOrNull(index)
                        val cache = cached.getOrNull(index)
                        if (total == null && input == null && output == null && cache == null) return@mapIndexedNotNull null
                        DailyUsagePoint(
                            date = java.time.Instant.ofEpochSecond(epoch).atZone(ZoneOffset.ofHours(8)).toLocalDate(),
                            model = model,
                            inputTokens = input,
                            outputTokens = output,
                            cachedTokens = cache,
                            totalTokens = total ?: listOfNotNull(input, output).sum(),
                            sourceId = "tokenhub-usage"
                        )
                    }
                }
                val total = (responseObject["Total"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: rows.size
                val limit = (responseObject["Limit"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 10
                val next = (offset + limit).takeIf { limit > 0 && it < total }?.toString()
                ProviderResult.Supported(SyncPage(rows, next))
            }
        },
        onFailure = { ProviderResult.Failure(it.message ?: "TokenHub 网络连接失败", retryable = true, errorType = SyncErrorType.NETWORK) }
    )
}

private fun JsonObject.longSeries(name: String, json: Json): List<Long?> {
    val encoded = (this[name] as? JsonPrimitive)?.contentOrNull ?: return emptyList()
    return runCatching { json.parseToJsonElement(encoded) as? JsonArray }.getOrNull().orEmpty().map { element ->
        (element as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
    }
}

private fun cloudHttpFailure(code: Int): ProviderResult.Failure = when (code) {
    401 -> ProviderResult.Failure("鉴权失败，请检查云凭据", code, false, SyncErrorType.AUTH)
    403 -> ProviderResult.Failure("云凭据权限不足", code, false, SyncErrorType.PERMISSION)
    429 -> ProviderResult.Failure("请求频率受限", code, true, SyncErrorType.RATE_LIMIT)
    else -> ProviderResult.Failure("官方接口返回 HTTP $code", code, code >= 500, SyncErrorType.NETWORK)
}
