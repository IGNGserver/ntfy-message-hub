package top.lvziwang.ntfyhub.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import top.lvziwang.ntfyhub.ui.component.HubScaffold
import top.lvziwang.ntfyhub.ui.component.rememberHubSnackbar
import top.lvziwang.ntfyhub.ui.viewmodel.AppViewModel

@Composable
fun ServerSetupScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var baseUrl by remember(state.settings.serverConfig.baseUrl) { mutableStateOf(state.settings.serverConfig.baseUrl) }
    var accessKey by remember { mutableStateOf("") }

    HubScaffold(title = "服务器配置", snackbarHostState = rememberHubSnackbar()) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("初次打开请先配置网站服务器地址和访问密钥。", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("服务器地址") },
                placeholder = { Text("例如 https://example.com 或 http://example.net:47183") }
            )
            OutlinedTextField(
                value = accessKey,
                onValueChange = { accessKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("访问密钥") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            state.setupError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    viewModel.vibrate()
                    viewModel.login(ServerConfig(baseUrl = baseUrl, accessKey = accessKey))
                }
            ) {
                Text("保存并连接")
            }
        }
    }
}
