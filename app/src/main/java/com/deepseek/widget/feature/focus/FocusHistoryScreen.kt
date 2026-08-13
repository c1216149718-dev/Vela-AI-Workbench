package com.deepseek.widget.feature.focus

import android.text.format.DateFormat
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepseek.widget.domain.model.FocusSession
import com.deepseek.widget.domain.model.FocusStatus
import com.deepseek.widget.ui.components.GlassScreen
import com.deepseek.widget.ui.components.GlassSurface
import com.deepseek.widget.ui.theme.LocalWorkbenchColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
fun FocusHistoryScreen(sessions: List<FocusSession>, onBack: () -> Unit) {
    val completed = sessions.filter { it.status == FocusStatus.COMPLETED }
    GlassScreen(modifier = Modifier.testTag("focus_history_screen")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            "FOCUS ARCHIVE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text("专注记录", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth(), blurRadius = 24.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        ArchiveMetric(completed.size.toString(), "会话")
                        ArchiveMetric(completed.sumOf { it.actualMinutes() }.toString(), "分钟")
                        ArchiveMetric(sessions.count { it.status == FocusStatus.CANCELLED }.toString(), "取消")
                    }
                }
            }
            if (sessions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "暂无记录\n满 5 分钟后自动保存",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                val grouped = sessions.groupBy(::sessionDate)
                grouped.forEach { (date, entries) ->
                    item {
                        Text(
                            date,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    item {
                        GlassSurface(modifier = Modifier.fillMaxWidth(), blurRadius = 22.dp) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                                entries.forEachIndexed { index, session ->
                                    FocusArchiveRow(session)
                                    if (index != entries.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 44.dp),
                                            color = LocalWorkbenchColors.current.border
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusArchiveRow(session: FocusSession) {
    val complete = session.status == FocusStatus.COMPLETED
    val time = DateFormat.getTimeFormat(LocalContext.current).format(Date(session.startedAt))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color = if (complete) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (complete) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(time, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"))
            Text(
                if (complete) "COMPLETED" else "CANCELLED",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "${session.actualMinutes()} min",
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")
        )
    }
}

@Composable
private fun ArchiveMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun sessionDate(session: FocusSession): String {
    val date = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINESE))
}
