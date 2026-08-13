package com.deepseek.widget.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.deepseek.widget.data.local.WorkbenchDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object TaskReminderScheduler {
    const val ACTION_REMIND = "com.deepseek.widget.action.TASK_REMINDER"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_REMINDER_AT = "reminder_at"

    fun schedule(context: Context, taskId: Long, reminderAt: Long) {
        val appContext = context.applicationContext
        cancel(appContext, taskId)
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager?.canScheduleExactAlarms() == true
        if (alarmManager != null && exactAllowed) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderAt,
                    pendingIntent(appContext, taskId, reminderAt)
                )
                return
            } catch (_: SecurityException) {
                // 系统未授予精确闹钟能力时使用持久化后台任务。
            }
        }
        TaskReminderWorker.scheduleWork(appContext, taskId, reminderAt)
    }

    fun cancel(context: Context, taskId: Long) {
        val appContext = context.applicationContext
        appContext.getSystemService(AlarmManager::class.java)?.cancel(
            pendingIntent(appContext, taskId, 0L)
        )
        TaskReminderWorker.cancelWork(appContext, taskId)
    }

    private fun pendingIntent(context: Context, taskId: Long, reminderAt: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            Intent(context, TaskReminderReceiver::class.java).apply {
                action = ACTION_REMIND
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_REMINDER_AT, reminderAt)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskReminderScheduler.ACTION_REMIND) return
        val taskId = intent.getLongExtra(TaskReminderScheduler.EXTRA_TASK_ID, -1L)
        val reminderAt = intent.getLongExtra(TaskReminderScheduler.EXTRA_REMINDER_AT, -1L)
        if (taskId <= 0L || reminderAt <= 0L) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TaskReminderDelivery.deliver(context.applicationContext, taskId, reminderAt)
            } finally {
                pending.finish()
            }
        }
    }
}

class TaskReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WorkbenchDatabase.get(context).taskDao()
                    .getPendingReminders(System.currentTimeMillis())
                    .forEach { task ->
                        task.reminderAt?.let {
                            TaskReminderScheduler.schedule(context, task.id, it)
                        }
                    }
            } finally {
                pending.finish()
            }
        }
    }
}
