package com.deepseek.widget.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deepseek.widget.data.local.WorkbenchDatabase
import java.util.concurrent.TimeUnit

/**
 * 专注完成 Worker。在专注预计结束时间发送通知。
 * 唯一 Work 名称：focus_completion_{sessionId}。
 */
class FocusCompletionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId <= 0) return Result.success()

        val now = System.currentTimeMillis()
        val dao = WorkbenchDatabase.get(applicationContext).focusSessionDao()
        val session = dao.getById(sessionId) ?: return Result.success()
        if (dao.completeRunning(sessionId, now, now) > 0) {
            NotificationHelper.showFocusComplete(
                applicationContext,
                sessionId,
                session.plannedMinutes
            )
        }
        return Result.success()
    }

    companion object {
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_MINUTES = "minutes"

        fun schedule(context: Context, sessionId: Long, minutes: Int, delayMillis: Long) {
            val request = OneTimeWorkRequestBuilder<FocusCompletionWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_SESSION_ID to sessionId, KEY_MINUTES to minutes))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "focus_completion_$sessionId",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, sessionId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("focus_completion_$sessionId")
        }
    }
}
