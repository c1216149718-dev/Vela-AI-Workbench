package com.deepseek.widget.feature.apikey

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepseek.widget.data.ApiKeyFunProfile
import com.deepseek.widget.ui.components.GlassScreen
import com.deepseek.widget.ui.components.GlassSurface

@Composable
fun ApiKeyFunKeysScreen(
    state: ApiKeyFunKeysUiState,
    onBack: () -> Unit,
    onAdd: (String, String, Boolean) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onSetPrimary: (String) -> Unit,
    onTest: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMessageShown: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ApiKeyFunProfile?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    GlassScreen(modifier = Modifier.testTag("apikey_keys_screen")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                    Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                        Text(
                            "KEY RING",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text("密钥管理", style = MaterialTheme.typography.headlineSmall)
                    }
                    Surface(
                        onClick = { showAdd = true },
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, contentDescription = "添加密钥")
                        }
                    }
                }
            }
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth(), blurRadius = 24.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CountMetric("启用", state.profiles.count { it.enabled }.toString())
                        CountMetric("全部", state.profiles.size.toString())
                        CountMetric("主 Key", if (state.profiles.any { it.isPrimaryForBalance }) "1" else "0")
                    }
                }
            }
            if (state.profiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("尚未添加密钥", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(state.profiles, key = { it.id }) { profile ->
                    KeyProfileCard(
                        profile = profile,
                        busy = profile.id in state.busyIds,
                        result = state.testResults[profile.id],
                        onEnabledChange = { onEnabledChange(profile.id, it) },
                        onSetPrimary = { onSetPrimary(profile.id) },
                        onTest = { onTest(profile.id) },
                        onDelete = { deleteTarget = profile }
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )
    }

    if (showAdd) {
        AddKeyDialog(
            onDismiss = { showAdd = false },
            onConfirm = { alias, key, primary ->
                onAdd(alias, key, primary)
                if (key.isNotBlank()) showAdd = false
            }
        )
    }
    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除 ${profile.alias}") },
            text = { Text("该密钥将从本机移除。") },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; onDelete(profile.id) }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun KeyProfileCard(
    profile: ApiKeyFunProfile,
    busy: Boolean,
    result: String?,
    onEnabledChange: (Boolean) -> Unit,
    onSetPrimary: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), blurRadius = 24.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            profile.alias,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (profile.isPrimaryForBalance) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(
                                    "PRIMARY",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Text(
                        profile.fingerprint.take(10).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = profile.enabled, onCheckedChange = onEnabledChange)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    IconButton(onClick = onSetPrimary, enabled = !profile.isPrimaryForBalance) {
                        Icon(
                            if (profile.isPrimaryForBalance) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "设为余额主 Key",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onTest, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.ElectricalServices, contentDescription = "测试连接")
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            result?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.startsWith("连接成功")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CountMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddKeyDialog(onDismiss: () -> Unit, onConfirm: (String, String, Boolean) -> Unit) {
    var alias by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var primary by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加密钥") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("别名") },
                    placeholder = { Text("例如 Claude") },
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp)
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        autoCorrectEnabled = false
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = primary, onCheckedChange = { primary = it })
                    Text("设为余额主 Key")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(alias, key, primary) }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
