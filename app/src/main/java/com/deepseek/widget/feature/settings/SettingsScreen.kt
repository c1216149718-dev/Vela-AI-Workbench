package com.deepseek.widget.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.deepseek.widget.data.ThemeMode
import com.deepseek.widget.ui.components.GlassScreen
import com.deepseek.widget.ui.components.GlassSurface
import com.deepseek.widget.ui.components.VelaEditorialHeader
import com.deepseek.widget.ui.components.VelaMotif
import com.deepseek.widget.ui.components.VelaSectionOrnament
import com.deepseek.widget.ui.components.VelaTitle
import com.deepseek.widget.ui.components.ProviderBrand
import com.deepseek.widget.ui.components.ProviderIdentity
import com.deepseek.widget.ui.theme.LocalWorkbenchColors

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    refreshIntervalMinutes: Int,
    versionName: String,
    onThemeModeChange: (ThemeMode) -> Unit,
    onRefreshIntervalChange: (Int) -> Unit,
    onApplyRefreshInterval: () -> Unit,
    onConnectionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassScreen(modifier, motif = VelaMotif.SETTINGS) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 30.dp, end = 20.dp, bottom = 126.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item {
                VelaEditorialHeader(VelaTitle.SETTINGS)
            }
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        SettingsHeading(Icons.Rounded.Palette, "外观", "APPEARANCE")
                        ThemeSegmentedControl(themeMode, onThemeModeChange)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VelaEditorialHeader(VelaTitle.CONNECTIONS_CREDENTIALS)
                    GlassSurface(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onConnectionsClick).padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("统一管理供应商连接", style = MaterialTheme.typography.titleMedium)
                                Text("添加、测试、导入账单与同步设置", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "进入连接与凭据")
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VelaEditorialHeader(VelaTitle.WIDGET)
                    GlassSurface(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SettingsHeading(Icons.Rounded.Refresh, "刷新间隔", "BACKGROUND UPDATE")
                            RefreshIntervals(refreshIntervalMinutes, onRefreshIntervalChange)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clip(CircleShape)
                                    .clickable(onClick = onApplyRefreshInterval)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                                Text("应用", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vela", style = MaterialTheme.typography.titleMedium)
                            Text("Version $versionName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { VelaSectionOrnament(VelaMotif.SETTINGS) }
        }
    }
}

@Composable
private fun ThemeSegmentedControl(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to "系统",
        ThemeMode.LIGHT to "浅色",
        ThemeMode.DARK to "深色"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .padding(3.dp)
    ) {
        options.forEach { (mode, label) ->
            val selectedColor by animateColorAsState(
                targetValue = if (selected == mode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                animationSpec = tween(180),
                label = "theme-segment"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(selectedColor)
                    .clickable { onSelected(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected == mode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RefreshIntervals(selected: Int, onSelected: (Int) -> Unit) {
    val options = listOf(15 to "15 分", 30 to "30 分", 60 to "1 小时", 180 to "3 小时")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (minutes, label) ->
            val active = selected == minutes
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onSelected(minutes) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProviderSettingsRow(provider: ProviderBrand, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProviderIdentity(provider)
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = LocalWorkbenchColors.current.tertiaryText)
    }
}

@Composable
private fun SettingsHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(13.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
