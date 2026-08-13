package com.deepseek.widget.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 余额快照：定期记录 DeepSeek 余额，通过差值计算消耗。
 */
@Serializable
data class BalanceSnapshot(
    val timestamp: Long,
    val balance: Double,
    val currency: String = "CNY"
)

/**
 * 快照序列化/反序列化助手。
 */
object BalanceSnapshotLedger {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(BalanceSnapshot.serializer())

    fun encode(snapshots: List<BalanceSnapshot>): String =
        json.encodeToString(serializer, snapshots)

    fun decode(raw: String): List<BalanceSnapshot> =
        runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
}
