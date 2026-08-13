package com.deepseek.widget.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deepseek.widget.data.local.WorkbenchDatabase
import com.deepseek.widget.domain.model.TaskStatus
import java.util.concurrent.TimeUnit

/**
 * 任务提醒 Worker。在指定时间后发送通知。
 * 唯一 Work 名称：task_reminder_{taskId}，replace 策略确保修改提醒时间时替换旧任务。
 */
class TaskReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        val expectedReminderAt = inputData.getLong(KEY_REMINDER_AT, -1L)
        if (taskId <= 0 || expectedReminderAt <= 0) return Result.success()

        TaskReminderDelivery.deliver(applicationContext, taskId, expectedReminderAt)
        return Result.success()
    }

    companion object {
        private const val KEY_TASK_ID = "task_id"
        private const val KEY_REMINDER_AT = "reminder_at"

        internal fun scheduleWork(context: Context, taskId: Long, reminderAt: Long) {
            val delayMillis = (reminderAt - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_TASK_ID to taskId,
                        KEY_REMINDER_AT to reminderAt
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "task_reminder_$taskId",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }

        internal fun cancelWork(context: Context, taskId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("task_reminder_$taskId")
        }
    }
}

internal object TaskReminderDelivery {
    suspend fun deliver(context: Context, taskId: Long, expectedReminderAt: Long) {
        val dao = WorkbenchDatabase.get(context).taskDao()
        val task = dao.getById(taskId) ?: return
        if (task.reminderAt != expectedReminderAt ||
            task.status == TaskStatus.DONE.name ||
            task.status == TaskStatus.CANCELLED.name
        ) return
        val consumed = dao.consumeReminder(taskId, expectedReminderAt, System.currentTimeMillis())
        if (consumed > 0) NotificationHelper.showTaskReminder(context, taskId, task.title)
    }
}
