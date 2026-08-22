package com.deepseek.widget.feature.providers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.deepseek.widget.data.provider.ProviderCapability
import com.deepseek.widget.data.repository.AiUsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.deepseek.widget.DeepSeekWidgetApp
import com.deepseek.widget.data.local.entity.ProviderProfileEntity
import com.deepseek.widget.data.provider.CustomConnectorMode
import com.deepseek.widget.data.provider.ProviderDescriptor
import com.deepseek.widget.data.provider.ProviderProfileRepository
import com.deepseek.widget.data.provider.ProviderRegistry
import com.deepseek.widget.data.provider.ProviderResult
import com.deepseek.widget.ui.components.EditorialDivider
import com.deepseek.widget.ui.components.GlassScreen
import com.deepseek.widget.ui.components.GlassSurface
import com.deepseek.widget.ui.components.VelaEditorialHeader
import com.deepseek.widget.ui.components.VelaMotif
import com.deepseek.widget.ui.components.VelaSectionOrnament
import com.deepseek.widget.ui.components.VelaTitle
import com.deepseek.widget.ui.components.ProviderLogo
import com.deepseek.widget.ui.theme.LocalWorkbenchColors
import com.deepseek.widget.ui.theme.WorkbenchTheme
import kotlinx.coroutines.launch

class DataSourceCenterFragment : Fragment() {
    private var pendingImportProfile: ProviderProfileEntity? = null
    private lateinit var usageRepository: AiUsageRepository
    private val billPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val profile = pendingImportProfile ?: return@registerForActivityResult
        pendingImportProfile = null
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { requireContext().contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "official-bill"
                val preview = usageRepository.previewOfficialBill(profile.providerId, name, bytes)
                require(!preview.duplicate) { "该账单已导入" }
                require(preview.records.isNotEmpty()) { preview.warnings.joinToString("；").ifBlank { "账单没有可识别记录" } }
                Triple(bytes, name, preview)
            }.onSuccess { (bytes, name, preview) ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("确认导入官方账单")
                    .setMessage("${preview.records.size} 条记录\n${preview.startDate} — ${preview.endDate}\n币种：${preview.currency.joinToString()}${preview.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "\n") ?: ""}")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("导入") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatching { usageRepository.commitOfficialBill(profile.id, name, bytes, preview) }
                                .onSuccess { Toast.makeText(requireContext(), "已导入 ${preview.records.size} 条账单记录", Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(requireContext(), it.message ?: "导入失败", Toast.LENGTH_LONG).show() }
                        }
                    }.show()
            }.onFailure { Toast.makeText(requireContext(), it.message ?: "账单读取失败", Toast.LENGTH_LONG).show() }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val appContainer = (requireActivity().application as DeepSeekWidgetApp).container
            val repository = appContainer.providerProfileRepository
            usageRepository = appContainer.aiUsageRepository
            setContent { WorkbenchTheme { DataSourceCenterScreen(repository) { profile -> pendingImportProfile = profile; billPicker.launch(arrayOf("text/csv", "application/zip", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) } } }
        }
}

@Composable
private fun DataSourceCenterScreen(repository: ProviderProfileRepository, onImportBill: (ProviderProfileEntity) -> Unit) {
    val profiles by repository.observeProfiles().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var addDescriptor by remember { mutableStateOf<ProviderDescriptor?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val connectedIds = profiles.mapTo(hashSetOf()) { it.providerId }

    GlassScreen(motif = VelaMotif.SOURCE_CENTER) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("provider_center_list"),
            contentPadding = PaddingValues(start = 20.dp, top = 26.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "section:header") {
                VelaEditorialHeader(VelaTitle.CONNECTIONS_CREDENTIALS)
                Spacer(Modifier.height(6.dp))
                Text("集中管理连接与同步；缺失值不计为 0。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                EditorialDivider(Modifier.padding(top = 14.dp))
            }
            message?.let { value ->
                item(key = "section:message") { Text(value, color = if (value.startsWith("已")) LocalWorkbenchColors.current.success else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
            if (profiles.isNotEmpty()) {
                item(key = "section:connected") { SectionTitle("已连接", "${profiles.size}") }
                items(profiles, key = { "profile:${it.id}" }) { profile ->
                    val descriptor = ProviderRegistry.descriptor(profile.providerId) ?: return@items
                    ProfileCard(
                        profile = profile,
                        descriptor = descriptor,
                        busy = busyId == profile.id,
                        onTest = {
                            busyId = profile.id
                            scope.launch {
                                message = when (val result = repository.test(profile.id)) {
                                    is ProviderResult.Supported -> "已连接 ${descriptor.displayName}"
                                    is ProviderResult.Unsupported -> result.reason
                                    is ProviderResult.PermissionRequired -> result.reason
                                    is ProviderResult.PartialFailure -> result.message
                                    is ProviderResult.Failure -> result.message
                                }
                                busyId = null
                            }
                        },
                        onEnabled = { enabled -> scope.launch { repository.setEnabled(profile.id, enabled) } },
                        onDelete = { scope.launch { repository.delete(profile.id) } },
                        onImport = { onImportBill(profile) }
                    )
                }
            }
            item(key = "section:presets") { SectionTitle("可添加", "10 PLATFORMS") }
            items(ProviderRegistry.descriptors.filter { it.id != ProviderRegistry.CUSTOM }, key = { "preset:${it.id.value}" }) { descriptor ->
                AddProviderCard(descriptor, connectedIds.contains(descriptor.id.value)) { addDescriptor = descriptor }
            }
            item(key = "section:custom") { SectionTitle("自定义", "SIMPLE · ADVANCED") }
            item(key = "preset:custom") { AddProviderCard(ProviderRegistry.descriptor(ProviderRegistry.CUSTOM.value)!!, connectedIds.contains(ProviderRegistry.CUSTOM.value)) { addDescriptor = ProviderRegistry.descriptor(ProviderRegistry.CUSTOM.value) } }
            item(key = "section:ornament") { VelaSectionOrnament(VelaMotif.SOURCE_CENTER) }
        }
    }

    addDescriptor?.let { descriptor ->
        AddProviderDialog(
            descriptor = descriptor,
            onDismiss = { addDescriptor = null },
            onSave = { alias, credentials, config, background ->
                scope.launch {
                    runCatching { repository.save(descriptor.id.value, alias, credentials, config, background) }
                        .onSuccess { message = "已保存 ${descriptor.displayName}"; addDescriptor = null }
                        .onFailure { message = it.message ?: "保存失败" }
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String, meta: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProviderMark(descriptor: ProviderDescriptor) {
    ProviderLogo(descriptor.id, descriptor.displayName)
}

@Composable
private fun AddProviderCard(descriptor: ProviderDescriptor, connected: Boolean, onClick: () -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProviderMark(descriptor)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(descriptor.displayName, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    descriptor.capabilities.take(4).forEach { Text(it.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (connected) Icon(Icons.Rounded.CheckCircle, contentDescription = "已连接", tint = LocalWorkbenchColors.current.success)
            else Icon(Icons.Rounded.Add, contentDescription = "添加")
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ProviderProfileEntity,
    descriptor: ProviderDescriptor,
    busy: Boolean,
    onTest: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onImport: () -> Unit
) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderMark(descriptor)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.alias, style = MaterialTheme.typography.titleMedium)
                    Text(descriptor.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = profile.enabled, onCheckedChange = onEnabled)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                descriptor.capabilities.forEach { AssistChip(onClick = {}, label = { Text(it.label) }) }
            }
            descriptor.limitation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            profile.lastError.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (ProviderCapability.BILL_IMPORT in descriptor.capabilities) {
                    TextButton(onClick = onImport) { Text("导入账单") }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除") }
                OutlinedButton(onClick = onTest, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Sync, contentDescription = null)
                    Spacer(Modifier.size(7.dp))
                    Text("测试")
                }
            }
        }
    }
}

@Composable
private fun AddProviderDialog(
    descriptor: ProviderDescriptor,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, String>, String, Boolean) -> Unit
) {
    var alias by remember(descriptor.id) { mutableStateOf(descriptor.displayName) }
    val values = remember(descriptor.id) { mutableStateMapOf<String, String>() }
    var mode by remember(descriptor.id) { mutableStateOf(CustomConnectorMode.SIMPLE) }
    var baseUrl by remember(descriptor.id) { mutableStateOf("") }
    var path by remember(descriptor.id) { mutableStateOf("/models") }
    var method by remember(descriptor.id) { mutableStateOf("GET") }
    var authHeader by remember(descriptor.id) { mutableStateOf("Authorization") }
    var authPrefix by remember(descriptor.id) { mutableStateOf("Bearer ") }
    var body by remember(descriptor.id) { mutableStateOf("") }
    var mapping by remember(descriptor.id) { mutableStateOf("") }
    var balanceUrl by remember(descriptor.id) { mutableStateOf("") }
    var dailyUsageUrl by remember(descriptor.id) { mutableStateOf("") }
    var modelUsageUrl by remember(descriptor.id) { mutableStateOf("") }
    var actualCostUrl by remember(descriptor.id) { mutableStateOf("") }
    var backgroundSync by remember(descriptor.id) { mutableStateOf(descriptor.id != ProviderRegistry.CUSTOM) }
    val requiredReady = descriptor.credentials.filter { it.required }.all { values[it.id].orEmpty().isNotBlank() }
    val customReady = descriptor.id != ProviderRegistry.CUSTOM || baseUrl.startsWith("https://")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 ${descriptor.displayName}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "field:alias") { OutlinedTextField(alias, { alias = it.take(48) }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth()) }
                items(descriptor.credentials, key = { "credential:${descriptor.id.value}:${it.id}" }) { field ->
                    OutlinedTextField(
                        value = values[field.id].orEmpty(),
                        onValueChange = { values[field.id] = it },
                        label = { Text(field.label) },
                        visualTransformation = if (field.secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (descriptor.id == ProviderRegistry.CUSTOM) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = mode == CustomConnectorMode.SIMPLE, onClick = { mode = CustomConnectorMode.SIMPLE }, label = { Text("简易") })
                            FilterChip(selected = mode == CustomConnectorMode.ADVANCED, onClick = { mode = CustomConnectorMode.ADVANCED }, label = { Text("高级") })
                        }
                    }
                    item { OutlinedTextField(baseUrl, { baseUrl = it.trim() }, label = { Text("HTTPS Base URL") }, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(path, { path = it }, label = { Text("测试路径") }, modifier = Modifier.fillMaxWidth()) }
                    if (mode == CustomConnectorMode.ADVANCED) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = method == "GET", onClick = { method = "GET" }, label = { Text("GET") })
                                FilterChip(selected = method == "POST", onClick = { method = "POST" }, label = { Text("POST") })
                            }
                        }
                        item { OutlinedTextField(authHeader, { authHeader = it }, label = { Text("鉴权 Header") }, modifier = Modifier.fillMaxWidth()) }
                        item { OutlinedTextField(authPrefix, { authPrefix = it }, label = { Text("鉴权前缀") }, modifier = Modifier.fillMaxWidth()) }
                        item { OutlinedTextField(balanceUrl, { balanceUrl = it.trim() }, label = { Text("余额端点 URL（可选）") }, modifier = Modifier.fillMaxWidth()) }
                        item { OutlinedTextField(dailyUsageUrl, { dailyUsageUrl = it.trim() }, label = { Text("日用量端点 URL（可选）") }, modifier = Modifier.fillMaxWidth()) }
                        item { OutlinedTextField(modelUsageUrl, { modelUsageUrl = it.trim() }, label = { Text("模型用量端点 URL（可选）") }, modifier = Modifier.fillMaxWidth()) }
                        item { OutlinedTextField(actualCostUrl, { actualCostUrl = it.trim() }, label = { Text("实际费用端点 URL（可选）") }, modifier = Modifier.fillMaxWidth()) }
                        item { OutlinedTextField(body, { body = it.take(4000) }, label = { Text("静态 JSON 请求体（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                        item { OutlinedTextField(mapping, { mapping = it.take(1000) }, label = { Text("字段映射：list=data;date=date;model=model;cost=cost;currency=currency;requests=requests;tokens=total_tokens") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("后台同步")
                            Text(if (descriptor.id == ProviderRegistry.CUSTOM) "测试成功后建议开启" else "使用系统周期任务刷新", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(backgroundSync, { backgroundSync = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = requiredReady && customReady,
                onClick = {
                    val config = if (descriptor.id == ProviderRegistry.CUSTOM) customConfigJson(baseUrl, path, method, authHeader, authPrefix, body, mapping, balanceUrl, dailyUsageUrl, modelUsageUrl, actualCostUrl) else ""
                    onSave(alias, values.toMap(), config, backgroundSync)
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun customConfigJson(baseUrl: String, path: String, method: String, authHeader: String, authPrefix: String, body: String, mapping: String, balanceUrl: String, dailyUsageUrl: String, modelUsageUrl: String, actualCostUrl: String): String {
    fun esc(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    val url = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    return "{\"testUrl\":\"${esc(url)}\",\"method\":\"${esc(method)}\",\"authHeader\":\"${esc(authHeader)}\",\"authPrefix\":\"${esc(authPrefix)}\",\"body\":\"${esc(body)}\",\"mapping\":\"${esc(mapping)}\",\"balanceUrl\":\"${esc(balanceUrl)}\",\"dailyUsageUrl\":\"${esc(dailyUsageUrl)}\",\"modelUsageUrl\":\"${esc(modelUsageUrl)}\",\"actualCostUrl\":\"${esc(actualCostUrl)}\"}"
}
