package com.deepseek.widget.data.repository

import android.content.Context
import com.deepseek.widget.data.local.dao.FocusSessionDao
import com.deepseek.widget.data.local.dao.TaskDao
import com.deepseek.widget.data.local.entity.FocusSessionEntity
import com.deepseek.widget.domain.model.FocusSession
import com.deepseek.widget.domain.model.FocusStatus
import com.deepseek.widget.worker.FocusCompletionWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface FocusRepository {
    fun observeActive(): Flow<FocusSession?>
    fun observeHistory(start: Long, end: Long): Flow<List<FocusSession>>
    suspend fun startSession(taskId: Long?, plannedMinutes: Int): Long
    suspend fun pause(id: Long): Boolean
    suspend fun resume(id: Long): Boolean
    suspend fun complete(id: Long): Boolean
    suspend fun cancel(id: Long): Boolean
    suspend fun sumCompletedMinutes(start: Long, end: Long): Int
}

class FocusRepositoryImpl(
    private val dao: FocusSessionDao,
    private val taskDao: TaskDao,
    private val context: Context
) : FocusRepository {

    override fun observeActive(): Flow<FocusSession?> =
        dao.observeActive().map { it?.toDomain() }

    override fun observeHistory(start: Long, end: Long): Flow<List<FocusSession>> =
        dao.observeHistory(start, end).map { list -> list.map { it.toDomain() } }

    override suspend fun startSession(taskId: Long?, plannedMinutes: Int): Long {
        require(plannedMinutes in 5..300) { "专注时长需在 5 到 300 分钟之间" }
        val now = System.currentTimeMillis()
        val expectedEnd = now + plannedMinutes.toLong() * 60_000L
        val session = FocusSessionEntity(
            taskId = taskId,
            plannedMinutes = plannedMinutes,
            startedAt = now,
            expectedEndAt = expectedEnd,
            status = FocusStatus.RUNNING.name,
            createdAt = now,
            updatedAt = now
        )
        val id = dao.insertIfNoActive(session)
        check(id > 0) { "已有进行中的专注" }
        try {
            FocusCompletionWorker.schedule(
                context = context.applicationContext,
                sessionId = id,
                minutes = plannedMinutes,
                delayMillis = plannedMinutes.toLong() * 60_000L
            )
        } catch (error: Throwable) {
            dao.cancel(id, now, now)
            throw error
        }
        taskId?.let { runCatching { taskDao.markInProgress(it, now) } }
        return id
    }

    override suspend fun pause(id: Long): Boolean {
        val now = System.currentTimeMillis()
        val ok = dao.pause(id, now, now) > 0
        if (ok) FocusCompletionWorker.cancel(context, id)
        return ok
    }

    override suspend fun resume(id: Long): Boolean {
        val now = System.currentTimeMillis()
        val session = dao.getById(id) ?: return false
        val pausedAt = session.pausedAt ?: return false
        val pauseDuration = now - pausedAt
        val newAccumulated = session.accumulatedPauseMillis + pauseDuration
        val newExpectedEnd = session.expectedEndAt + pauseDuration
        val ok = dao.resume(id, newAccumulated, newExpectedEnd, now) > 0
        if (ok) {
            FocusCompletionWorker.schedule(
                context = context.applicationContext,
                sessionId = id,
                minutes = session.plannedMinutes,
                delayMillis = (newExpectedEnd - now).coerceAtLeast(0L)
            )
        }
        return ok
    }

    override suspend fun complete(id: Long): Boolean {
        val now = System.currentTimeMillis()
        val session = dao.getById(id)?.takeIf { it.status == FocusStatus.RUNNING.name || it.status == FocusStatus.PAUSED.name }
            ?: return false
        val ok = if (focusedDurationMillis(session, now) < MIN_FOCUS_RECORD_MILLIS) {
            dao.deleteActive(id) > 0
        } else {
            dao.complete(id, now, now) > 0
        }
        if (ok) FocusCompletionWorker.cancel(context, id)
        return ok
    }

    override suspend fun cancel(id: Long): Boolean {
        val now = System.currentTimeMillis()
        val session = dao.getById(id)?.takeIf { it.status == FocusStatus.RUNNING.name || it.status == FocusStatus.PAUSED.name }
            ?: return false
        val ok = if (focusedDurationMillis(session, now) < MIN_FOCUS_RECORD_MILLIS) {
            dao.deleteActive(id) > 0
        } else {
            dao.cancel(id, now, now) > 0
        }
        if (ok) FocusCompletionWorker.cancel(context, id)
        return ok
    }

    override suspend fun sumCompletedMinutes(start: Long, end: Long): Int =
        dao.sumCompletedMinutes(start, end)
}

internal const val MIN_FOCUS_RECORD_MILLIS = 5 * 60_000L

internal fun focusedDurationMillis(session: FocusSessionEntity, now: Long): Long {
    val currentPauseMillis = if (session.status == FocusStatus.PAUSED.name) {
        (now - (session.pausedAt ?: now)).coerceAtLeast(0L)
    } else {
        0L
    }
    return (now - session.startedAt - session.accumulatedPauseMillis - currentPauseMillis).coerceAtLeast(0L)
}

internal fun FocusSessionEntity.toDomain(): FocusSession = FocusSession(
    id = id,
    taskId = taskId,
    plannedMinutes = plannedMinutes,
    startedAt = startedAt,
    expectedEndAt = expectedEndAt,
    endedAt = endedAt,
    pausedAt = pausedAt,
    accumulatedPauseMillis = accumulatedPauseMillis,
    status = FocusStatus.fromName(status),
    createdAt = createdAt,
    updatedAt = updatedAt
)
