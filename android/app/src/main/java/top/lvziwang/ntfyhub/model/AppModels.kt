package top.lvziwang.ntfyhub.model

import kotlinx.serialization.Serializable
import top.lvziwang.ntfyhub.ui.viewmodel.Route

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class ThemePreset {
    OCEAN,
    SUNSET,
    FOREST
}

enum class ViewMode {
    TIMELINE,
    GROUPED
}

enum class SyncStatusKind {
    IDLE,
    RUNNING,
    SUCCESS,
    ERROR
}

@Serializable
data class ServerConfig(
    val baseUrl: String = "",
    val accessKey: String = "",
    val rememberSession: Boolean = true
)

data class AppSettings(
    val serverConfig: ServerConfig = ServerConfig(),
    val hasSavedSession: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val themePreset: ThemePreset = ThemePreset.OCEAN,
    val vibrationEnabled: Boolean = true,
    val localStorageEnabled: Boolean = false
)

data class SyncStatus(
    val kind: SyncStatusKind = SyncStatusKind.IDLE,
    val startedAtMillis: Long? = null,
    val finishedAtMillis: Long? = null,
    val durationMillis: Long? = null,
    val message: String? = null
)

@Serializable
data class TopicDto(
    val name: String,
    val messageCount: Int,
    val latestReceivedAt: String? = null
)

@Serializable
data class TagDto(
    val tag: String,
    val messageCount: Int
)

@Serializable
data class BootstrapResponse(
    val topics: List<TopicDto>,
    val tags: List<TagDto>
)

@Serializable
data class MessageDto(
    val id: String,
    val ntfyMessageId: String,
    val eventType: String,
    val messageText: String? = null,
    val title: String? = null,
    val priority: Int? = null,
    val tags: List<String>? = null,
    val clickUrl: String? = null,
    val actions: kotlinx.serialization.json.JsonElement? = null,
    val ntfyTime: Long? = null,
    val receivedAt: String,
    val rawJson: kotlinx.serialization.json.JsonElement? = null,
    val topic: String,
    val recorderUser: String,
    val sourceUser: String? = null
)

@Serializable
data class MessagesResponse(
    val messages: List<MessageDto>,
    val nextBeforeId: String? = null
)

data class MessageItem(
    val id: String,
    val ntfyMessageId: String,
    val title: String,
    val rawTitle: String?,
    val messageText: String,
    val priority: Int?,
    val tags: List<String>,
    val clickUrl: String?,
    val ntfyTime: Long?,
    val receivedAt: String,
    val rawJson: String?,
    val topic: String,
    val sourceUser: String?
)

data class MessageGroup(
    val title: String,
    val items: List<MessageItem>
)

data class HomeUiState(
    val channels: List<TopicDto> = emptyList(),
    val availableTags: List<TagDto> = emptyList(),
    val selectedTopic: String? = null,
    val selectedTags: Set<String> = emptySet(),
    val query: String = "",
    val viewMode: ViewMode = ViewMode.TIMELINE,
    val onlineMessages: List<MessageItem> = emptyList(),
    val cachedMessages: List<MessageItem> = emptyList(),
    val groupedMessages: List<MessageGroup> = emptyList(),
    val expandedGroups: Set<String> = emptySet(),
    val selectedMessage: MessageItem? = null,
    val loading: Boolean = false,
    val offline: Boolean = false,
    val errorMessage: String? = null,
    val lastRemoteRefreshAtMillis: Long? = null,
    val lastSyncStatus: SyncStatus = SyncStatus(),
    val canLoadCachedData: Boolean = false
) {
    val visibleMessages: List<MessageItem>
        get() = if (offline) cachedMessages else onlineMessages
}

enum class StartDestination(val route: String) {
    LOADING(Route.Loading.route),
    SERVER(Route.ServerSetup.route),
    LOGIN(Route.Login.route),
    HOME(Route.Home.route)
}
