package com.deepseek.widget.domain.model

/** 专注会话状态机。 */
enum class FocusStatus {
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED;

    companion object {
        fun fromName(name: String): FocusStatus =
            entries.firstOrNull { it.name == name } ?: RUNNING
    }
}

/** 专注会话领域模型。 */
data class FocusSession(
    val id: Long,
    val taskId: Long?,
    val plannedMinutes: Int,
    val startedAt: Long,
    val expectedEndAt: Long,
    val endedAt: Long?,
    val pausedAt: Long?,
    val accumulatedPauseMillis: Long,
    val status: FocusStatus,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** 计算当前剩余毫秒。RUNNING/PAUSED 用真实时间；COMPLETED/CANCELLED 为 0。 */
    fun remainingMillis(now: Long): Long = when (status) {
        FocusStatus.RUNNING -> (expectedEndAt - now).coerceAtLeast(0L)
        FocusStatus.PAUSED -> ((expectedEndAt - (pausedAt ?: now))).coerceAtLeast(0L)
        else -> 0L
    }

    /** 已结束会话的实际专注分钟数，排除暂停时长。 */
    fun actualMinutes(): Int {
        val end = endedAt ?: return 0
        val unfinishedPause = pausedAt?.let { (end - it).coerceAtLeast(0L) } ?: 0L
        return ((end - startedAt - accumulatedPauseMillis - unfinishedPause).coerceAtLeast(0L) / 60_000L).toInt()
    }
}
