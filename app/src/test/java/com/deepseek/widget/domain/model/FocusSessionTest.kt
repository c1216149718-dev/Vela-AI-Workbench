package com.deepseek.widget.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusSessionTest {

    @Test
    fun runningRemainingIsExpectedEndMinusNow() {
        val session = FocusSession(
            id = 1,
            taskId = null,
            plannedMinutes = 25,
            startedAt = 0L,
            expectedEndAt = 1_500_000L,
            endedAt = null,
            pausedAt = null,
            accumulatedPauseMillis = 0L,
            status = FocusStatus.RUNNING,
            createdAt = 0L,
            updatedAt = 0L
        )
        assertEquals(500_000L, session.remainingMillis(1_000_000L))
    }

    @Test
    fun runningRemainingClampsToZero() {
        val session = FocusSession(
            id = 1, taskId = null, plannedMinutes = 25, startedAt = 0L,
            expectedEndAt = 1_000_000L, endedAt = null, pausedAt = null,
            accumulatedPauseMillis = 0L, status = FocusStatus.RUNNING,
            createdAt = 0L, updatedAt = 0L
        )
        assertEquals(0L, session.remainingMillis(2_000_000L))
    }

    @Test
    fun pausedRemainingIsExpectedEndMinusPausedAt() {
        val session = FocusSession(
            id = 1, taskId = null, plannedMinutes = 25, startedAt = 0L,
            expectedEndAt = 1_500_000L, endedAt = null, pausedAt = 1_000_000L,
            accumulatedPauseMillis = 0L, status = FocusStatus.PAUSED,
            createdAt = 0L, updatedAt = 0L
        )
        // PAUSED 时用 expectedEndAt - pausedAt，不受 now 影响
        assertEquals(500_000L, session.remainingMillis(9_999_999L))
    }

    @Test
    fun completedRemainingIsZero() {
        val session = FocusSession(
            id = 1, taskId = null, plannedMinutes = 25, startedAt = 0L,
            expectedEndAt = 1_500_000L, endedAt = 1_500_000L, pausedAt = null,
            accumulatedPauseMillis = 0L, status = FocusStatus.COMPLETED,
            createdAt = 0L, updatedAt = 0L
        )
        assertEquals(0L, session.remainingMillis(1_000_000L))
    }

    @Test
    fun actualMinutesExcludesPauseThatWasNeverResumed() {
        val session = FocusSession(
            id = 1,
            taskId = null,
            plannedMinutes = 25,
            startedAt = 0L,
            expectedEndAt = 1_500_000L,
            endedAt = 10 * 60_000L,
            pausedAt = 6 * 60_000L,
            accumulatedPauseMillis = 60_000L,
            status = FocusStatus.COMPLETED,
            createdAt = 0L,
            updatedAt = 10 * 60_000L
        )

        assertEquals(5, session.actualMinutes())
    }
}
