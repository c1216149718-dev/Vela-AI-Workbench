package com.deepseek.widget.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class DeepSeekApiClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchBalance(apiKey: String, balanceUrl: String = DEEPSEEK_BALANCE_URL): Result<BalanceResponse> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(balanceUrl)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Result.success(json.decodeFromString<BalanceResponse>(body))
                    } else {
                        Result.failure(IOException(errorMessageForCode(response.code, body)))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchApiKeyFunBalance(apiKey: String): Result<BalanceResponse> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(APIKEY_FUN_USAGE_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Result.success(parseApiKeyFunUsage(body))
                    } else {
                        Result.failure(IOException(errorMessageForCode(response.code, body)))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchApiKeyFunUsage(apiKey: String, days: Int): Result<ApiKeyFunUsageResponse> =
        withContext(Dispatchers.IO) {
            try {
                require(days in 1..90) { "统计天数必须在 1 到 90 天之间" }
                val endDate = LocalDate.now()
                val startDate = endDate.minusDays((days - 1).toLong())
                val url = APIKEY_FUN_USAGE_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("days", days.toString())
                    .addQueryParameter("start_date", startDate.toString())
                    .addQueryParameter("end_date", endDate.toString())
                    .addQueryParameter("timezone", ZoneId.systemDefault().id)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Result.success(parseApiKeyFunUsageDetails(body))
                    } else {
                        Result.failure(IOException(errorMessageForCode(response.code, body)))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    internal fun parseApiKeyFunUsageDetails(body: String): ApiKeyFunUsageResponse {
        val root = json.parseToJsonElement(body).let { it as? JsonObject } ?: return ApiKeyFunUsageResponse()
        val payloads = usagePayloads(root)
        val daily = bestParsedList(
            payloads,
            listOf("daily_usage", "daily", "records", "history"),
            ::parseDailyUsage
        )
        val models = bestParsedList(
            payloads,
            listOf("model_stats", "models", "by_model", "model_usage"),
            ::parseModelStats
        )
        return ApiKeyFunUsageResponse(daily_usage = daily, model_stats = models)
    }

    /**
     * APIKEY.FUN 的真实响应在顶层同时放置 usage 摘要、daily_usage 和 model_stats。
     * usage 摘要不能当作包装层，否则会丢掉顶层的每日与模型统计。
     */
    private fun usagePayloads(root: JsonObject): List<JsonObject> {
        val payloads = mutableListOf(root)
        sequenceOf("data", "result", "response", "body").forEach { key ->
            (root[key] as? JsonObject)?.let(payloads::add)
        }

        val usage = root["usage"] as? JsonObject
        if (usage != null && usage.keys.any { it in USAGE_DETAIL_KEYS }) {
            payloads.add(usage)
        }
        return payloads
    }

    /**
     * 同一响应可能同时提供摘要和完整明细。选择条目最多的候选，避免先遇到
     * 只有单一模型的摘要后丢掉后续完整 model_stats。
     */
    private fun <T> bestParsedList(
        payloads: List<JsonObject>,
        keys: List<String>,
        parser: (JsonElement) -> List<T>
    ): List<T> {
        var best: List<T> = emptyList()
        for (payload in payloads) {
            for (key in keys) {
                val element = payload[key] ?: continue
                val parsed = parser(element)
                if (parsed.size > best.size) best = parsed
            }
        }
        return best
    }

    /**
     * daily_usage 兼容数组与 map 两种形态。
     */
    private fun parseDailyUsage(element: JsonElement): List<DailyUsagePoint> = when (element) {
        is JsonArray -> element.mapNotNull { it as? JsonObject }.map { parseDailyPoint(it) }
        is JsonObject -> element.entries.map { (date, value) ->
            val obj = value as? JsonObject ?: return@map null
            parseDailyPoint(obj, fallbackDate = date)
        }.filterNotNull()
        else -> emptyList()
    }

    private fun parseDailyPoint(obj: JsonObject, fallbackDate: String = ""): DailyUsagePoint {
        val date = obj["date"]?.jsonPrimitive?.contentOrNull?.ifBlank { fallbackDate }
            ?: obj["day"]?.jsonPrimitive?.contentOrNull?.ifBlank { fallbackDate }
            ?: obj["time"]?.jsonPrimitive?.contentOrNull?.ifBlank { fallbackDate }
            ?: fallbackDate
        val input = obj["input_tokens"].longOrZero()
            .let { if (it > 0) it else obj["prompt_tokens"].longOrZero() }
        val output = obj["output_tokens"].longOrZero()
            .let { if (it > 0) it else obj["completion_tokens"].longOrZero() }
        val total = obj["total_tokens"].longOrZero().let { if (it > 0) it else input + output }
        val requests = obj["requests"].longOrZero()
            .let { if (it > 0) it else obj["count"].longOrZero() }
            .let { if (it > 0) it else obj["calls"].longOrZero() }
        return DailyUsagePoint(
            date = date,
            requests = requests,
            input_tokens = input,
            output_tokens = output,
            cache_read_tokens = obj["cache_read_tokens"].longOrZero(),
            cache_write_tokens = obj["cache_write_tokens"].longOrZero(),
            total_tokens = total,
            cost = obj["cost"].doubleOrZero(),
            actual_cost = resolveCost(obj, prefer = "actual_cost")
        )
    }

    /**
     * model_stats 兼容数组与 map 两种形态（map 时 key 为模型名）。
     */
    private fun parseModelStats(element: JsonElement): List<ModelUsageStat> = when (element) {
        is JsonArray -> element.mapNotNull { it as? JsonObject }.map { parseModelStat(it) }
        is JsonObject -> element.entries.map { (model, value) ->
            val obj = value as? JsonObject ?: return@map null
            parseModelStat(obj, fallbackModel = model)
        }.filterNotNull()
        else -> emptyList()
    }

    private fun parseModelStat(obj: JsonObject, fallbackModel: String = ""): ModelUsageStat {
        val model = obj["model"]?.jsonPrimitive?.contentOrNull?.ifBlank { fallbackModel }
            ?: obj["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { fallbackModel }
            ?: obj["model_name"]?.jsonPrimitive?.contentOrNull?.ifBlank { fallbackModel }
            ?: fallbackModel
        val input = obj["input_tokens"].longOrZero()
            .let { if (it > 0) it else obj["prompt_tokens"].longOrZero() }
        val output = obj["output_tokens"].longOrZero()
            .let { if (it > 0) it else obj["completion_tokens"].longOrZero() }
        val total = obj["total_tokens"].longOrZero().let { if (it > 0) it else input + output }
        val requests = obj["requests"].longOrZero()
            .let { if (it > 0) it else obj["count"].longOrZero() }
            .let { if (it > 0) it else obj["calls"].longOrZero() }
        return ModelUsageStat(
            model = model,
            requests = requests,
            input_tokens = input,
            output_tokens = output,
            cache_creation_tokens = obj["cache_creation_tokens"].longOrZero(),
            cache_read_tokens = obj["cache_read_tokens"].longOrZero(),
            total_tokens = total,
            cost = obj["cost"].doubleOrZero(),
            actual_cost = resolveCost(obj, prefer = "actual_cost"),
            account_cost = obj["account_cost"].doubleOrZero()
        )
    }

    /** 依次尝试 prefer / cost / account_cost / total_cost / amount / fee，避免 actual_cost 缺失时费用归零。 */
    private fun resolveCost(obj: JsonObject, prefer: String): Double {
        sequenceOf(prefer, "cost", "account_cost", "total_cost", "amount", "fee", "spend").forEach { key ->
            val v = obj.doubleField(key)
            if (v > 0.0) return v
        }
        return 0.0
    }

    private fun JsonObject.doubleField(key: String): Double =
        (this[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0

    private fun JsonElement?.longOrZero(): Long = (this as? JsonPrimitive)?.longOrNull ?: 0L
    private fun JsonElement?.doubleOrZero(): Double = (this as? JsonPrimitive)?.doubleOrNull ?: 0.0

    internal fun parseApiKeyFunUsage(body: String): BalanceResponse {
        val root = json.parseToJsonElement(body).jsonObject
        val payload = (root["data"] as? JsonObject) ?: root
        val quota = payload["quota"] as? JsonObject
        val balance = sequenceOf(
            payload["balance"],
            payload["remaining"],
            quota?.get("remaining")
        ).mapNotNull { it?.jsonPrimitive?.contentOrNull }
            .firstOrNull()
            ?: throw IOException("APIKEY.FUN 接口未返回余额字段")
        val unit = sequenceOf(
            payload["unit"],
            payload["currency"],
            quota?.get("unit")
        ).mapNotNull { it?.jsonPrimitive?.contentOrNull }
            .firstOrNull()
            .orEmpty()
            .ifBlank { "USD" }
        val numericBalance = balance.toDoubleOrNull()

        return BalanceResponse(
            is_available = numericBalance == null || numericBalance > 0,
            balance_infos = listOf(
                BalanceInfo(
                    currency = unit,
                    total_balance = balance,
                    granted_balance = "0",
                    topped_up_balance = "0"
                )
            )
        )
    }

    private fun errorMessageForCode(code: Int, body: String): String {
        val summary = when (code) {
            401 -> "API Key 无效或未授权"
            402 -> "账户余额不足"
            429 -> "请求过于频繁，请稍后再试"
            in 500..599 -> "服务端错误"
            else -> "请求失败 ($code)"
        }
        val detail = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val error = root["error"] as? JsonObject
            error?.get("message")?.jsonPrimitive?.contentOrNull
                ?: root["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull().orEmpty().replace(Regex("\\s+"), " ").take(160)
        return if (detail.isBlank() || summary.contains(detail, ignoreCase = true)) summary
        else "$summary：$detail"
    }

    companion object {
        private val USAGE_DETAIL_KEYS = setOf(
            "daily_usage", "daily", "records", "history",
            "model_stats", "models", "by_model", "model_usage"
        )
        private const val APIKEY_FUN_USAGE_URL = "https://apikey.fun/v1/usage"
        private const val DEEPSEEK_BALANCE_URL = "https://api.deepseek.com/user/balance"
    }
}
