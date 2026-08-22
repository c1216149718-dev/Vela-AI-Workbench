package com.deepseek.widget.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepseek.widget.domain.model.TaskPriority
import com.deepseek.widget.domain.model.TaskScheduleRules
import com.deepseek.widget.ui.components.GlassScreen
import com.deepseek.widget.ui.components.GlassSurface
import com.deepseek.widget.ui.components.VelaMotif
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TaskEditScreen(
    state: TaskEditUiState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onPriorityChange: (TaskPriority) -> Unit,
    onScheduleEnabledChange: (Boolean) -> Unit,
    onReminderChange: (Int?) -> Unit,
    onDateChange: (String) -> Unit,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onFinished: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<SchedulePickerTarget?>(null) }
    LaunchedEffect(state.isSaved, state.isDeleted) {
        if (state.isSaved || state.isDeleted) onFinished()
    }

    GlassScreen(modifier = Modifier.testTag("task_editor"), motif = VelaMotif.TASKS) {
        if (!state.isLoaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
            }
            return@GlassScreen
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item { EditorHeader(state, onBack) }
            if (state.isReadOnly) {
                item { ReadOnlyTaskCard(state) }
            } else {
                item { TextEntryCard(state, onTitleChange, onNotesChange) }
                item { PriorityCard(state.priority, onPriorityChange) }
                item {
                    ScheduleCard(
                        state = state,
                        onEnabledChange = onScheduleEnabledChange,
                        onPickStart = { pickerTarget = SchedulePickerTarget.START },
                        onPickEnd = { pickerTarget = SchedulePickerTarget.END }
                    )
                }
                if (state.scheduleEnabled) {
                    item { ReminderCard(state.reminderOffsetMinutes, onReminderChange) }
                }
                state.error?.let { message ->
                    item {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                item {
                    Button(
                        onClick = onSave,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else Text("保存", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (state.taskId > 0) {
                    item {
                        TextButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("删除任务")
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除任务") },
            text = { Text("此操作无法撤销。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }

    pickerTarget?.let { target ->
        TaskSchedulePicker(
            state = state,
            target = target,
            onDismiss = { pickerTarget = null },
            onDateChange = onDateChange,
            onStartChange = onStartChange,
            onEndChange = onEndChange
        )
    }
}

@Composable
private fun EditorHeader(state: TaskEditUiState, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = if (state.isReadOnly) "TASK DETAIL" else "NEW TASK",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (state.isReadOnly) "任务详情" else if (state.taskId > 0) "编辑任务" else "新建任务",
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

@Composable
private fun TextEntryCard(
    state: TaskEditUiState,
    onTitleChange: (String) -> Unit,
    onNotesChange: (String) -> Unit
) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), blurRadius = 28.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题") },
                placeholder = { Text("准备下一件重要的事") },
                singleLine = true,
                shape = RoundedCornerShape(17.dp)
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth().height(112.dp),
                label = { Text("备注") },
                placeholder = { Text("可选") },
                shape = RoundedCornerShape(17.dp)
            )
        }
    }
}

@Composable
private fun PriorityCard(selected: TaskPriority, onSelected: (TaskPriority) -> Unit) {
    SectionCard("PRIORITY", "优先级") {
        SegmentedOptions(
            options = listOf(
                TaskPriority.NONE to "无",
                TaskPriority.LOW to "低",
                TaskPriority.MEDIUM to "中",
                TaskPriority.HIGH to "高"
            ),
            selected = selected,
            onSelected = onSelected
        )
    }
}

@Composable
private fun ScheduleCard(
    state: TaskEditUiState,
    onEnabledChange: (Boolean) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit
) {
    SectionCard("SCHEDULE", "时间") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (state.scheduleEnabled) "已安排" else "不设时间",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(checked = state.scheduleEnabled, onCheckedChange = onEnabledChange)
        }
        if (state.scheduleEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EndpointField("开始", state.startAt, onPickStart, Modifier.weight(1f))
                EndpointField("结束", state.dueAt, onPickEnd, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EndpointField(label: String, timestamp: Long?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dateTime = timestamp?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val date = dateTime?.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE)) ?: "选择日期"
    val time = dateTime?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINESE)) ?: "--:--"
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        onClick = onClick
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(time, style = MaterialTheme.typography.headlineSmall)
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReminderCard(selected: Int?, onSelected: (Int?) -> Unit) {
    SectionCard("REMINDER", "提醒") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ChoicePill("无", selected == null) { onSelected(null) } }
            items(TaskScheduleRules.reminderOffsetsMinutes.size) { index ->
                val value = TaskScheduleRules.reminderOffsetsMinutes[index]
                ChoicePill(reminderLabel(value), selected == value) { onSelected(value) }
            }
        }
    }
}

@Composable
private fun ReadOnlyTaskCard(state: TaskEditUiState) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), blurRadius = 28.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Text(state.title, style = MaterialTheme.typography.headlineSmall)
            if (state.notes.isNotBlank()) {
                Text(state.notes, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.scheduleEnabled) {
                DetailLine("开始", formatDateTime(state.startAt))
                DetailLine("结束", formatDateTime(state.dueAt))
            }
            DetailLine("优先级", priorityLabel(state.priority))
            DetailLine("提醒", state.reminderOffsetMinutes?.let(::reminderLabel) ?: "无")
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "已完成 · 只读",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), blurRadius = 28.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun <T> SegmentedOptions(options: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (value, label) ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                    color = if (value == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    onClick = { onSelected(value) }
                ) {
                    Box(Modifier.height(42.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        onClick = onClick
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatDateTime(value: Long?): String {
    if (value == null) return "未设置"
    return Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINESE))
}

private fun priorityLabel(value: TaskPriority): String = when (value) {
    TaskPriority.NONE -> "无"
    TaskPriority.LOW -> "低"
    TaskPriority.MEDIUM -> "中"
    TaskPriority.HIGH -> "高"
}

private fun reminderLabel(minutes: Int): String = when (minutes) {
    0 -> "事件发生时"
    60 -> "1 小时前"
    120 -> "2 小时前"
    180 -> "3 小时前"
    300 -> "5 小时前"
    else -> "$minutes 分钟前"
}
