package com.deepseek.widget.data.repository

import com.deepseek.widget.data.local.entity.FocusSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusRecordPolicyTest {
    private val startedAt = 1_000_000L

    @Test
    fun runningSessionCountsElapsedFocusTime() {
        val session = session(status = "RUNNING")

        assertEquals(5 * 60_000L, focusedDurationMillis(session, startedAt + 5 * 60_000L))
    }

    @Test
    fun pausedSessionExcludesCurrentAndPreviousPauses() {
        val session = session(
            status = "PAUSED",
            pausedAt = startedAt + 6 * 60_000L,
            accumulatedPauseMillis = 60_000L
        )

        assertEquals(5 * 60_000L, focusedDurationMillis(session, startedAt + 10 * 60_000L))
    }

    @Test
    fun durationNeverBecomesNegative() {
        val session = session(status = "RUNNING", accumulatedPauseMillis = 10 * 60_000L)

        assertEquals(0L, focusedDurationMillis(session, startedAt + 60_000L))
    }

    private fun session(
        status: String,
        pausedAt: Long? = null,
        accumulatedPauseMillis: Long = 0L
    ) = FocusSessionEntity(
        plannedMinutes = 25,
        startedAt = startedAt,
        expectedEndAt = startedAt + 25 * 60_000L,
        pausedAt = pausedAt,
        accumulatedPauseMillis = accumulatedPauseMillis,
        status = status,
        createdAt = startedAt,
        updatedAt = startedAt
    )
}
