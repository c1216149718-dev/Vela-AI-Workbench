package com.deepseek.widget.worker

import android.Manifest
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.deepseek.widget.MainActivity
import com.deepseek.widget.R

/**
 * 通知渠道初始化与通知发送助手。
 * 渠道：productivity_reminders（任务提醒）和 focus_sessions（专注会话）。
 */
object NotificationHelper {

    const val CHANNEL_REMINDERS = "productivity_reminders"
    const val CHANNEL_FOCUS = "focus_sessions"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOCUS,
                context.getString(R.string.channel_focus),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    fun showTaskReminder(context: Context, taskId: Long, title: String) {
        if (!canNotify(context)) return
        val contentIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_DESTINATION, R.id.taskEditFragment)
                putExtra(MainActivity.EXTRA_ENTITY_ID, taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_module_todo)
            .setContentTitle(context.getString(R.string.notification_task_reminder))
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
        } catch (_: SecurityException) {
            // 通知权限未授予，静默跳过
        }
    }

    fun showFocusComplete(context: Context, sessionId: Long, minutes: Int) {
        if (!canNotify(context)) return
        val contentIntent = PendingIntent.getActivity(
            context,
            (sessionId.hashCode() * 31) + 1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_DESTINATION, R.id.focusFragment)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_module_pomodoro)
            .setContentTitle(context.getString(R.string.notification_focus_complete))
            .setContentText(context.getString(R.string.notification_focus_complete_detail, minutes))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(sessionId.toInt(), notification)
        } catch (_: SecurityException) {
            // 通知权限未授予，静默跳过
        }
    }

    private fun canNotify(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
