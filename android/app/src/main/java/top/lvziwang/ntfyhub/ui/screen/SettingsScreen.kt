package top.lvziwang.ntfyhub.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.lvziwang.ntfyhub.model.ServerConfig
import top.lvziwang.ntfyhub.model.ThemePreset
import top.lvziwang.ntfyhub.model.ThemeMode
import top.lvziwang.ntfyhub.ui.component.HubScaffold
import top.lvziwang.ntfyhub.ui.component.rememberHubSnackbar
import top.lvziwang.ntfyhub.ui.viewmodel.AppViewModel

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = rememberHubSnackbar()
    var baseUrl by remember(state.settings.serverConfig.baseUrl) { mutableStateOf(state.settings.serverConfig.baseUrl) }
    var accessKey by remember { mutableStateOf("") }

    HubScaffold(
        title = "设置",
        onBack = onBack,
        onBackVibrate = viewModel::vibrate,
        snackbarHostState = snackbarHostState
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("服务器连接信息", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("服务器地址") }
                    )
                    OutlinedTextField(
                        value = accessKey,
                        onValueChange = { accessKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("访问密钥") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    androidx.compose.material3.Button(onClick = {
                        viewModel.vibrate()
                        if (accessKey.isBlank()) {
                            viewModel.updateServerConfig(ServerConfig(baseUrl = baseUrl))
                        } else {
                            viewModel.login(ServerConfig(baseUrl = baseUrl, accessKey = accessKey))
                        }
                    }) {
                        Text(if (accessKey.isBlank()) "保存服务器地址" else "保存并重新登录")
                    }
                }
            }
            item {
                Text("主题色与外观", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
            }
            items(ThemeMode.entries) { mode ->
                ListItem(
                    headlineContent = { Text(mode.name) },
                    leadingContent = {
                        RadioButton(
                            selected = state.settings.themeMode == mode,
                            onClick = { viewModel.updateThemeMode(mode) }
                        )
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("使用系统主题色") },
                    supportingContent = { Text("开启后跟随系统动态配色，关闭后可选择下方预设主题") },
                    trailingContent = {
                        Switch(
                            checked = state.settings.useDynamicColor,
                            onCheckedChange = viewModel::updateDynamicColor
                        )
                    }
                )
            }
            if (!state.settings.useDynamicColor) {
                item {
                    Text("预设主题", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
                }
                items(ThemePreset.entries) { preset ->
                    ListItem(
                        headlineContent = { Text(preset.name) },
                        leadingContent = {
                            RadioButton(
                                selected = state.settings.themePreset == preset,
                                onClick = { viewModel.updateThemePreset(preset) }
                            )
                        }
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("振动反馈") },
                    trailingContent = {
                        Switch(
                            checked = state.settings.vibrationEnabled,
                            onCheckedChange = viewModel::updateVibration
                        )
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("开启消息本地存储") },
                    supportingContent = { Text("开启后自动缓存最新数据，关闭后仍可联网查看，但离线时无法查看历史消息") },
                    trailingContent = {
                        Switch(
                            checked = state.settings.localStorageEnabled,
                            onCheckedChange = viewModel::updateLocalStorage
                        )
                    }
                )
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("同步状态", style = MaterialTheme.typography.titleMedium)
                    Text("状态：${state.home.lastSyncStatus.kind}")
                    state.home.lastSyncStatus.durationMillis?.let {
                        Text("耗时：${it}ms")
                    }
                    state.home.lastSyncStatus.finishedAtMillis?.let {
                        Text("上次完成：$it")
                    }
                    state.home.lastSyncStatus.message?.let {
                        Text(it)
                    }
                }
            }
        }
    }
}
