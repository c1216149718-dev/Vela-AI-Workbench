package com.deepseek.widget.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 一条 DeepSeek 本地用量记录（本地记账，不依赖官方用量 API）。
 *
 * @param id 记录唯一 id（时间戳生成）
 * @param date 发生日期 YYYY-MM-DD
 * @param model 模型名，如 deepseek-v4-flash
 * @param inputTokens 输入 token
 * @param outputTokens 输出 token
 * @param totalTokens 合计 token（缺省时由 input+output 推导）
 * @param cost 本次费用（CNY）
 */
@Serializable
data class DeepSeekUsageEntry(
    val id: Long,
    val date: String,
    val model: String,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val cost: Double = 0.0
) {
    val resolvedTotalTokens: Long
        get() = if (totalTokens > 0) totalTokens else inputTokens + outputTokens
}

/**
 * DeepSeek 本地账本的序列化助手。账本以 JSON 字符串形式存入 DataStore。
 */
object DeepSeekUsageLedger {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(DeepSeekUsageEntry.serializer())

    fun encode(entries: List<DeepSeekUsageEntry>): String = json.encodeToString(serializer, entries)

    fun decode(raw: String): List<DeepSeekUsageEntry> =
        if (raw.isBlank()) emptyList()
        else runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
}
