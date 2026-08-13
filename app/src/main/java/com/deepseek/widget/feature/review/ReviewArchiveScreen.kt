package com.deepseek.widget.feature.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepseek.widget.domain.model.DailyReview
import com.deepseek.widget.ui.components.GlassScreen
import com.deepseek.widget.ui.components.GlassSurface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReviewArchiveScreen(reviews: List<DailyReview>, onBack: () -> Unit) {
    var selected by remember { mutableStateOf<DailyReview?>(null) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(reviews, query) {
        reviews.filter { query.isBlank() || it.note.contains(query, ignoreCase = true) }
            .sortedByDescending { it.date }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { it.date.take(7) }.toSortedMap(compareByDescending { it })
    }

    GlassScreen(modifier = Modifier.testTag("review_archive_screen")) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(154.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 14.dp, 20.dp, 92.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 14.dp
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                    Column(Modifier.padding(start = 8.dp)) {
                        Text("MEMO WALL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("每日留言墙", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                GlassSurface(Modifier.fillMaxWidth(), blurRadius = 22.dp) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        ReviewMetric(reviews.size.toString(), "留言")
                        ReviewMetric(reviews.mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.average()?.let { "%.1f".format(it) } ?: "--", "均分")
                        ReviewMetric(grouped.size.toString(), "月份")
                    }
                }
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("搜索留言") },
                    shape = RoundedCornerShape(18.dp)
                )
            }
            if (filtered.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        Text(if (query.isBlank()) "还没有留言\n每天留一句，慢慢铺满这面墙" else "没有找到相关留言", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                grouped.forEach { (month, entries) ->
                    item(span = StaggeredGridItemSpan.FullLine, key = "month-$month") {
                        Text(formatMonth(month), Modifier.padding(top = 10.dp, start = 4.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(entries, key = { it.date }) { review ->
                        MemoCard(review) { selected = review }
                    }
                }
            }
        }
    }

    selected?.let { review ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(formatReviewDate(review.date)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    review.rating?.let { RatingDots(it) }
                    Text(review.note.ifBlank { "无文字记录" }, style = MaterialTheme.typography.bodyLarge)
                    Text("仅保存在本机", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("收好") } }
        )
    }
}

@Composable
private fun MemoCard(review: DailyReview, onClick: () -> Unit) {
    val rotation = remember(review.date) { ((review.date.hashCode() % 7) - 3) * .35f }
    val tint = when (review.rating ?: 3) {
        1 -> Color(0xFFF1E3DE)
        2 -> Color(0xFFF0E8D8)
        4 -> Color(0xFFE0EBE5)
        5 -> Color(0xFFD9E9E8)
        else -> Color(0xFFE9E5DD)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().graphicsLayer { rotationZ = rotation }.shadow(5.dp, RoundedCornerShape(4.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        color = tint,
        contentColor = Color(0xFF302E2B)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(formatMemoDate(review.date), style = MaterialTheme.typography.labelMedium, color = Color(0xFF6D6860))
            Text(
                review.note.ifBlank { "今天没有写下文字。" },
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = if (review.note.isBlank()) FontStyle.Italic else FontStyle.Normal),
                maxLines = 7,
                overflow = TextOverflow.Ellipsis
            )
            review.rating?.let { RatingDots(it, dark = true) }
        }
    }
}

@Composable
private fun RatingDots(rating: Int, dark: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { index ->
            Surface(shape = CircleShape, color = if (index < rating) if (dark) Color(0xFF6D7365) else MaterialTheme.colorScheme.primary else Color(0x334E4A44)) {
                Box(Modifier.padding(3.dp))
            }
        }
    }
}

@Composable
private fun ReviewMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatMonth(value: String): String = runCatching {
    LocalDate.parse("$value-01").format(DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINESE))
}.getOrDefault(value)

private fun formatMemoDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("M.d  EEE", Locale.CHINESE))
}.getOrDefault(value)

private fun formatReviewDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINESE))
}.getOrDefault(value)
