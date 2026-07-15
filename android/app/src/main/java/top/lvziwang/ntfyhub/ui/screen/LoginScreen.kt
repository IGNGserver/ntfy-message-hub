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
fun LoginScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var accessKey by remember { mutableStateOf("") }

    HubScaffold(title = "登录", snackbarHostState = rememberHubSnackbar()) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("请输入访问密钥后继续。", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = accessKey,
                onValueChange = { accessKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("访问密钥") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            state.loginError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    viewModel.vibrate()
                    viewModel.login(
                        ServerConfig(
                            baseUrl = state.settings.serverConfig.baseUrl,
                            accessKey = accessKey
                        )
                    )
                }
            ) {
                Text("登录")
            }
        }
    }
}
