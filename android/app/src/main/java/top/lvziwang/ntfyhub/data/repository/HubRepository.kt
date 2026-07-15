package top.lvziwang.ntfyhub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import top.lvziwang.ntfyhub.data.local.MessageDao
import top.lvziwang.ntfyhub.data.local.MessageEntity
import top.lvziwang.ntfyhub.data.local.SettingsStore
import top.lvziwang.ntfyhub.data.local.SyncStateDao
import top.lvziwang.ntfyhub.data.local.SyncStateEntity
import top.lvziwang.ntfyhub.data.remote.HubApi
import top.lvziwang.ntfyhub.data.remote.SessionManager
import top.lvziwang.ntfyhub.model.AppSettings
import top.lvziwang.ntfyhub.model.BootstrapResponse
import top.lvziwang.ntfyhub.model.MessageDto
import top.lvziwang.ntfyhub.model.MessageItem
import top.lvziwang.ntfyhub.model.MessagesResponse
import top.lvziwang.ntfyhub.model.ServerConfig
import top.lvziwang.ntfyhub.model.SyncStatus
import top.lvziwang.ntfyhub.model.SyncStatusKind
import top.lvziwang.ntfyhub.model.ThemePreset

class HubRepository(
    private val settingsStore: SettingsStore,
    private val api: HubApi,
    private val sessionManager: SessionManager,
    private val messageDao: MessageDao,
    private val syncStateDao: SyncStateDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<AppSettings> = settingsStore.settings

    val cachedMessages: Flow<List<MessageItem>> = messageDao.observeMessages().map { entities ->
        entities.map { entity ->
            MessageItem(
                id = entity.id,
                ntfyMessageId = entity.ntfyMessageId,
                title = normalizeTitle(entity.rawTitle),
                rawTitle = entity.rawTitle,
                messageText = entity.messageText,
                priority = entity.priority,
                tags = json.decodeFromString(entity.tagsJson),
                clickUrl = entity.clickUrl,
                ntfyTime = entity.ntfyTime,
                receivedAt = entity.receivedAt,
                rawJson = entity.rawJson,
                topic = entity.topic,
                sourceUser = entity.sourceUser
            )
        }
    }

    val syncStatus: Flow<SyncStatus> = syncStateDao.observeState().map { state ->
        if (state == null) SyncStatus() else SyncStatus(
            kind = SyncStatusKind.valueOf(state.status),
            startedAtMillis = state.startedAtMillis,
            finishedAtMillis = state.finishedAtMillis,
            durationMillis = state.durationMillis,
            message = state.message
        )
    }

    suspend fun updateServerConfig(config: ServerConfig) {
        val oldSettings = settings.first()
        val oldBaseUrl = oldSettings.serverConfig.baseUrl
        val newBaseUrl = config.baseUrl.trim()
        settingsStore.updateServerConfig(config)
        sessionManager.baseUrl = normalizeBaseUrl(config.baseUrl)
        if (!config.rememberSession || (oldBaseUrl.isNotBlank() && oldBaseUrl != newBaseUrl)) {
            clearSession()
        }
    }

    suspend fun login(serverConfig: ServerConfig): Result<Unit> = runCatching {
        clearSession()
        sessionManager.baseUrl = normalizeBaseUrl(serverConfig.baseUrl)
        val response = api.login(loginRequestBody(serverConfig.accessKey))
        if (!response.isSuccessful) {
            error(loginFailureMessage(response.code()))
        }
        updateServerConfig(serverConfig.copy(accessKey = ""))
        persistSessionIfNeeded()
        if (!sessionManager.hasSessionForBaseUrl()) {
            error("登录失败，服务器未返回有效会话")
        }
    }

    suspend fun restoreSession() {
        val settings = settings.first()
        if (settings.serverConfig.baseUrl.isBlank()) return
        sessionManager.baseUrl = normalizeBaseUrl(settings.serverConfig.baseUrl)
        val saved = settingsStore.getSavedSession() ?: return
        sessionManager.restoreSession(saved.first, saved.second)
    }

    suspend fun bootstrap(): BootstrapResponse {
        ensureBaseUrl()
        ensureAuthenticated()
        return api.bootstrap()
    }

    suspend fun validateSession(): Result<Unit> = runCatching {
        ensureBaseUrl()
        ensureAuthenticated()
        api.bootstrap()
        Unit
    }.onFailure {
        // Keep a valid local session through temporary network or server failures.
        // Only an explicit authorization response means the cookie is no longer usable.
        if (isAuthenticationFailure(it)) clearSession()
    }

    suspend fun fetchMessages(
        topic: String?,
        tags: List<String>,
        query: String?
    ): MessagesResponse {
        ensureBaseUrl()
        ensureAuthenticated()
        return api.messages(
            topic = topic,
            tags = tags,
            query = query,
            limit = 120
        )
    }

    suspend fun syncAll(
        topic: String?,
        tags: List<String>,
        query: String?
    ): Result<Int> = runCatching {
        val startedAt = System.currentTimeMillis()
        syncStateDao.upsert(
            SyncStateEntity(
                status = SyncStatusKind.RUNNING.name,
                startedAtMillis = startedAt,
                finishedAtMillis = null,
                durationMillis = null,
                message = "正在同步"
            )
        )

        val response = fetchMessages(topic, tags, query)
        val settings = settings.first()
        if (settings.localStorageEnabled) {
            replaceMessages(response.messages)
        } else {
            messageDao.clearAll()
        }

        val finishedAt = System.currentTimeMillis()
        syncStateDao.upsert(
            SyncStateEntity(
                status = SyncStatusKind.SUCCESS.name,
                startedAtMillis = startedAt,
                finishedAtMillis = finishedAt,
                durationMillis = finishedAt - startedAt,
                message = "同步完成"
            )
        )
        response.messages.size
    }.onFailure { throwable ->
        val now = System.currentTimeMillis()
        syncStateDao.upsert(
            SyncStateEntity(
                status = SyncStatusKind.ERROR.name,
                startedAtMillis = now,
                finishedAtMillis = now,
                durationMillis = 0,
                message = throwable.message ?: "同步失败"
            )
        )
    }

    suspend fun updateThemeMode(themeMode: top.lvziwang.ntfyhub.model.ThemeMode) = settingsStore.updateThemeMode(themeMode)
    suspend fun updateDynamicColor(enabled: Boolean) = settingsStore.updateDynamicColor(enabled)
    suspend fun updateThemePreset(themePreset: ThemePreset) = settingsStore.updateThemePreset(themePreset)
    suspend fun updateVibration(enabled: Boolean) = settingsStore.updateVibration(enabled)
    suspend fun updateLocalStorage(enabled: Boolean) = settingsStore.updateLocalStorage(enabled)

    suspend fun clearCachedMessages() {
        messageDao.clearAll()
    }

    suspend fun invalidateSession() {
        clearSession()
    }

    private suspend fun replaceMessages(messages: List<MessageDto>) {
        messageDao.clearAll()
        messageDao.insertAll(
            messages.map { dto ->
                MessageEntity(
                    id = dto.id,
                    ntfyMessageId = dto.ntfyMessageId,
                    title = normalizeTitle(dto.title),
                    rawTitle = dto.title,
                    messageText = dto.messageText.orEmpty(),
                    priority = dto.priority,
                    tagsJson = json.encodeToString(dto.tags.orEmpty()),
                    clickUrl = dto.clickUrl,
                    ntfyTime = dto.ntfyTime,
                    receivedAt = dto.receivedAt,
                    rawJson = dto.rawJson?.toString(),
                    topic = dto.topic,
                    sourceUser = dto.sourceUser
                )
            }
        )
    }

    private suspend fun ensureBaseUrl() {
        if (sessionManager.baseUrl.isNullOrBlank()) {
            sessionManager.baseUrl = normalizeBaseUrl(settings.first().serverConfig.baseUrl)
        }
    }

    private suspend fun ensureAuthenticated() {
        if (sessionManager.hasSessionForBaseUrl()) return

        clearSession()
        error("登录已失效")
    }

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        if (trimmed.isBlank()) return "/"
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        return "$withScheme/"
    }

    private fun normalizeTitle(title: String?): String =
        title?.trim()?.removePrefix("\"")?.removeSuffix("\"").orEmpty().ifBlank { "无标题消息" }

    private suspend fun persistSessionIfNeeded() {
        val settings = settings.first()
        if (!settings.serverConfig.rememberSession) return
        val session = sessionManager.sessionCookieValueForBaseUrl() ?: return
        settingsStore.saveSession(session.first, session.second)
    }

    private fun loginRequestBody(accessKey: String) =
        buildJsonObject {
            put("accessKey", accessKey)
        }.toString().toRequestBody("application/json".toMediaType())

    private fun loginFailureMessage(statusCode: Int): String = when (statusCode) {
        401, 403 -> "访问密钥不正确"
        else -> "登录请求失败（HTTP $statusCode）"
    }

    fun isAuthenticationFailure(throwable: Throwable): Boolean =
        throwable is HttpException && (throwable.code() == 401 || throwable.code() == 403)

    private suspend fun clearSession() {
        sessionManager.clear()
        settingsStore.clearSession()
    }
}
