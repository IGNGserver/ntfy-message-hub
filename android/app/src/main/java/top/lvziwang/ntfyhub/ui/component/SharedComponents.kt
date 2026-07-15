package top.lvziwang.ntfyhub.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.lvziwang.ntfyhub.model.MessageGroup
import top.lvziwang.ntfyhub.model.MessageItem
import top.lvziwang.ntfyhub.model.TagDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onBackVibrate: (() -> Unit)? = null,
    onSettingsVibrate: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = {
                            onBackVibrate?.invoke()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (onSettings != null) {
                        IconButton(onClick = {
                            onSettingsVibrate?.invoke()
                            onSettings()
                        }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "设置")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

@Composable
fun rememberHubSnackbar(): SnackbarHostState = remember { SnackbarHostState() }

@Composable
fun OfflineBanner(visible: Boolean, hasCachedData: Boolean) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = if (hasCachedData) {
                "服务器连接异常，当前显示本地缓存数据"
            } else {
                "服务器连接异常，暂时无法刷新云端数据"
            },
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MessageCard(
    message: MessageItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(message.topic) }
                )
                Text(
                    text = message.receivedAt,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                text = message.title,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = message.messageText.ifBlank { "（空消息）" },
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                message.sourceUser?.let {
                    Text(text = "用户：$it", style = MaterialTheme.typography.labelMedium)
                }
                Text(text = "ID：${message.ntfyMessageId}", style = MaterialTheme.typography.labelMedium)
                if (message.tags.isNotEmpty()) {
                    Text(text = "标签：${message.tags.joinToString()}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FilterDialog(
    allTags: List<TagDto>,
    selectedTags: Set<String>,
    initialQuery: String,
    onVibrate: () -> Unit,
    onDismiss: () -> Unit,
    onApply: (Set<String>, String) -> Unit
) {
    var localQuery by remember { mutableStateOf(initialQuery) }
    var localTags by remember(selectedTags) { mutableStateOf(selectedTags) }

    LaunchedEffect(selectedTags) {
        localTags = selectedTags
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筛选器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = localQuery,
                    onValueChange = { localQuery = it },
                    label = { Text("搜索") },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(allTags) { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onVibrate()
                                    localTags = if (localTags.contains(tag.tag)) {
                                        localTags - tag.tag
                                    } else {
                                        localTags + tag.tag
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = localTags.contains(tag.tag),
                                onCheckedChange = {
                                    onVibrate()
                                    localTags = if (it) {
                                        localTags + tag.tag
                                    } else {
                                        localTags - tag.tag
                                    }
                                }
                            )
                            Column {
                                Text(tag.tag)
                                Text("${tag.messageCount} 条", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onVibrate()
                onApply(localTags, localQuery)
            }) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onVibrate()
                onDismiss()
            }) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailSheet(message: MessageItem, onDismiss: () -> Unit, onVibrate: () -> Unit) {
    var selectedPage by remember { mutableStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            Text(
                text = message.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            TabRow(selectedTabIndex = selectedPage) {
                Tab(
                    selected = selectedPage == 0,
                    onClick = {
                        onVibrate()
                        selectedPage = 0
                    },
                    text = { Text("消息内容") }
                )
                Tab(
                    selected = selectedPage == 1,
                    onClick = {
                        onVibrate()
                        selectedPage = 1
                    },
                    text = { Text("详细信息") }
                )
            }
            if (selectedPage == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Text(message.title, style = MaterialTheme.typography.headlineSmall) }
                    item { Text(message.messageText.ifBlank { "（空消息）" }, style = MaterialTheme.typography.bodyLarge) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { Text("频道：${message.topic}") }
                    item { Text("消息 ID：${message.ntfyMessageId}") }
                    item { Text("记录 ID：${message.id}") }
                    item { Text("接收时间：${message.receivedAt}") }
                    message.ntfyTime?.let { item { Text("消息时间：$it") } }
                    message.priority?.let { item { Text("优先级：$it") } }
                    message.sourceUser?.let { item { Text("用户：$it") } }
                    if (message.tags.isNotEmpty()) item { Text("标签：${message.tags.joinToString()}") }
                    message.clickUrl?.let { item { Text("点击链接：$it") } }
                    message.rawJson?.let {
                        item { Text("原始 JSON") }
                        item { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupedMessageList(
    groups: List<MessageGroup>,
    expandedGroups: Set<String>,
    listState: LazyListState,
    onToggle: (String) -> Unit,
    onMessageClick: (MessageItem) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups) { group ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(group.title) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(group.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("${group.items.size} 条")
                    }
                    if (expandedGroups.contains(group.title)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            group.items.forEach { item ->
                                MessageCard(message = item, onClick = { onMessageClick(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}
