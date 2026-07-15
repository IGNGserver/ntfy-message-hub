package top.lvziwang.ntfyhub.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.lvziwang.ntfyhub.model.ViewMode
import top.lvziwang.ntfyhub.ui.component.FilterDialog
import top.lvziwang.ntfyhub.ui.component.GroupedMessageList
import top.lvziwang.ntfyhub.ui.component.HubScaffold
import top.lvziwang.ntfyhub.ui.component.MessageCard
import top.lvziwang.ntfyhub.ui.component.MessageDetailSheet
import top.lvziwang.ntfyhub.ui.component.OfflineBanner
import top.lvziwang.ntfyhub.ui.viewmodel.AppViewModel

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HomeScreen(
    viewModel: AppViewModel,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val home = state.home
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showFilters by remember { mutableStateOf(false) }
    var pullDistance by remember { mutableStateOf(0f) }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHome()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    HubScaffold(
        title = "讯笺",
        onSettings = onOpenSettings,
        onSettingsVibrate = viewModel::vibrate,
        snackbarHostState = snackbarHostState
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            OfflineBanner(
                visible = home.offline,
                hasCachedData = home.canLoadCachedData && home.cachedMessages.isNotEmpty()
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = home.selectedTopic == null,
                    onClick = { viewModel.selectTopic(null) },
                    label = { Text("全部") }
                )
                home.channels.forEach { channel ->
                    FilterChip(
                        selected = home.selectedTopic == channel.name,
                        onClick = { viewModel.selectTopic(channel.name) },
                        label = { Text("${channel.name} (${channel.messageCount})") }
                    )
                }
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = {
                    viewModel.vibrate()
                    showFilters = true
                }) { Text("筛选器") }
                OutlinedButton(
                    onClick = {
                        viewModel.changeViewMode(
                            if (home.viewMode == ViewMode.TIMELINE) ViewMode.GROUPED else ViewMode.TIMELINE
                        )
                    }
                ) {
                    Text(if (home.viewMode == ViewMode.TIMELINE) "切到分组" else "切到记录")
                }
            }
            Text(
                text = when {
                    home.loading -> "正在刷新云端数据..."
                    home.lastRemoteRefreshAtMillis != null -> "上次刷新成功于 ${home.lastRemoteRefreshAtMillis}"
                    home.visibleMessages.isNotEmpty() -> "当前显示已加载的数据"
                    home.offline -> "网络不可用，暂无可展示的历史消息"
                    home.lastSyncStatus.kind.name == "RUNNING" -> {
                        val startedAt = home.lastSyncStatus.startedAtMillis ?: System.currentTimeMillis()
                        "正在缓存本地数据，已持续 ${System.currentTimeMillis() - startedAt} ms"
                    }
                    home.lastSyncStatus.finishedAtMillis != null -> "上次本地缓存完成于 ${home.lastSyncStatus.finishedAtMillis}"
                    else -> "下拉可刷新最新消息"
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            if (home.viewMode == ViewMode.TIMELINE) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(home.loading) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    val atTop = listState.firstVisibleItemIndex == 0 &&
                                        listState.firstVisibleItemScrollOffset == 0
                                    if (!atTop || home.loading) return@detectVerticalDragGestures
                                    if (dragAmount > 0) {
                                        pullDistance += dragAmount
                                        if (pullDistance >= 140f) {
                                            pullDistance = 0f
                                            viewModel.refreshHome()
                                        }
                                    } else {
                                        pullDistance = 0f
                                    }
                                },
                                onDragEnd = {
                                    pullDistance = 0f
                                },
                                onDragCancel = {
                                    pullDistance = 0f
                                }
                            )
                        },
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(home.visibleMessages, key = { it.id }) { message ->
                        MessageCard(message = message, onClick = { viewModel.selectMessage(message) })
                    }
                }
            } else {
                GroupedMessageList(
                    groups = home.groupedMessages,
                    expandedGroups = home.expandedGroups,
                    listState = listState,
                    onToggle = viewModel::toggleGroup,
                    onMessageClick = { viewModel.selectMessage(it) }
                )
            }
        }
    }

    if (showFilters) {
        FilterDialog(
            allTags = home.availableTags,
            selectedTags = home.selectedTags,
            initialQuery = home.query,
            onVibrate = viewModel::vibrate,
            onDismiss = { showFilters = false },
            onApply = { tags, query ->
                showFilters = false
                viewModel.applyFilters(tags, query)
            }
        )
    }

    home.selectedMessage?.let { message ->
        MessageDetailSheet(
            message = message,
            onDismiss = { viewModel.selectMessage(null) },
            onVibrate = viewModel::vibrate
        )
    }

    home.errorMessage?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = viewModel::clearHomeError,
            title = { Text("连接异常") },
            text = {
                Text(
                    if (home.visibleMessages.isNotEmpty()) {
                        "$errorMessage\n\n当前继续显示已保存的消息。"
                    } else {
                        "$errorMessage\n\n无法获取消息。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearHomeError) { Text("知道了") }
            }
        )
    }
}
