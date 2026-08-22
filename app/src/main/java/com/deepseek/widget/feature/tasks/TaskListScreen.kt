package com.deepseek.widget.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepseek.widget.data.repository.TaskFilter
import com.deepseek.widget.R
import com.deepseek.widget.domain.model.Task
import com.deepseek.widget.domain.model.TaskPriority
import com.deepseek.widget.domain.model.TaskStatus
import com.deepseek.widget.ui.components.GlassScreen
import com.deepseek.widget.ui.components.GlassSurface
import com.deepseek.widget.ui.components.VelaEditorialHeader
import com.deepseek.widget.ui.components.VelaMotif
import com.deepseek.widget.ui.components.VelaSectionOrnament
import com.deepseek.widget.ui.components.VelaTitle
import com.deepseek.widget.ui.theme.LocalWorkbenchColors

@Composable
fun TaskListScreen(
    state: TaskListUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (TaskFilter) -> Unit,
    onTaskToggle: (Task) -> Unit,
    onTaskClick: (Task) -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassScreen(modifier = modifier.testTag("tasks_screen"), motif = VelaMotif.TASKS) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 148.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                VelaEditorialHeader(VelaTitle.TASKS)
            }
            item { TaskSearch(query = state.query, onQueryChange = onQueryChange) }
            item { TaskFilterControl(selected = state.filter, onSelected = onFilterChange) }
            item {
                when {
                    state.isLoading -> Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                    }

                    state.tasks.isEmpty() -> EmptyTaskState(state.filter)
                    else -> TaskGroup(state.tasks, onTaskToggle, onTaskClick)
                }
            }
            item { VelaSectionOrnament(VelaMotif.TASKS) }
        }

        FloatingActionButton(
            onClick = onAddTask,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 116.dp)
                .testTag("add_task"),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "新建任务")
        }
    }
}

@Composable
private fun TaskSearch(query: String, onQueryChange: (String) -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), blurRadius = 20.dp) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索任务") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun TaskFilterControl(selected: TaskFilter, onSelected: (TaskFilter) -> Unit) {
    val options = listOf(
        TaskFilter.ALL to "待办",
        TaskFilter.COMPLETED to "已完成"
    )
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), blurRadius = 20.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (filter, label) ->
                val active = selected == filter
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onSelected(filter) }
                ) {
                    Box(modifier = Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTaskState(filter: TaskFilter) {
    val message = when (filter) {
        TaskFilter.TODAY -> "这里暂时没有任务"
        TaskFilter.COMPLETED -> "还没有完成的任务"
        else -> "这里暂时没有任务"
    }
    Box(modifier = Modifier.fillMaxWidth().height(310.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
                painter = painterResource(R.drawable.vela_empty_tasks),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(190.dp).height(128.dp)
            )
            Text("Clear space.", style = MaterialTheme.typography.headlineSmall)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TaskGroup(tasks: List<Task>, onTaskToggle: (Task) -> Unit, onTaskClick: (Task) -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), blurRadius = 24.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            tasks.forEachIndexed { index, task ->
                TaskRow(task, onToggle = { onTaskToggle(task) }, onClick = { onTaskClick(task) })
                if (index != tasks.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 44.dp), color = LocalWorkbenchColors.current.border)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, onToggle: () -> Unit, onClick: () -> Unit) {
    val complete = task.status == TaskStatus.DONE
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (complete) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(1.dp, if (complete) MaterialTheme.colorScheme.primary else LocalWorkbenchColors.current.tertiaryText, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (complete) Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (complete) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (task.notes.isNotBlank()) {
                Text(
                    text = task.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            task.startAt?.let { start ->
                val context = androidx.compose.ui.platform.LocalContext.current
                val formatter = android.text.format.DateFormat.getTimeFormat(context)
                Text(
                    text = buildString {
                        append(formatter.format(java.util.Date(start)))
                        task.dueAt?.let { append(" – ").append(formatter.format(java.util.Date(it))) }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalWorkbenchColors.current.tertiaryText
                )
            }
        }
        val priority = when (task.priority) {
            TaskPriority.HIGH -> "HIGH"
            TaskPriority.MEDIUM -> "MID"
            TaskPriority.LOW -> "LOW"
            TaskPriority.NONE -> null
        }
        priority?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = if (task.priority == TaskPriority.HIGH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
