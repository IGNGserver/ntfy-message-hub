package top.lvziwang.ntfyhub.ui.viewmodel

import android.app.Application
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import retrofit2.HttpException
import top.lvziwang.ntfyhub.app.AppContainer
import top.lvziwang.ntfyhub.model.AppSettings
import top.lvziwang.ntfyhub.model.HomeUiState
import top.lvziwang.ntfyhub.model.MessageGroup
import top.lvziwang.ntfyhub.model.MessageItem
import top.lvziwang.ntfyhub.model.ServerConfig
import top.lvziwang.ntfyhub.model.StartDestination
import top.lvziwang.ntfyhub.model.SyncStatus
import top.lvziwang.ntfyhub.model.ThemeMode
import top.lvziwang.ntfyhub.model.ThemePreset
import top.lvziwang.ntfyhub.model.ViewMode

data class AppUiState(
    val settings: AppSettings = AppSettings(),
    val startDestination: StartDestination = StartDestination.LOADING,
    val home: HomeUiState = HomeUiState(),
    val loginError: String? = null,
    val setupError: String? = null
)

class AppViewModel(
    application: Application,
    private val container: AppContainer
) : AndroidViewModel(application) {
    private val repository = container.repository
    private val mutableHome = MutableStateFlow(HomeUiState())
    private val loginError = MutableStateFlow<String?>(null)
    private val setupError = MutableStateFlow<String?>(null)
    private val sessionReady = MutableStateFlow(false)
    private val sessionAuthenticated = MutableStateFlow(false)
    private val vibrator = application.getSystemService(Vibrator::class.java)

    private val combinedCore = combine(
        repository.settings,
        repository.cachedMessages,
        repository.syncStatus,
        ::Triple
    )

    private val combinedSessionState = combine(
        sessionReady,
        sessionAuthenticated,
        ::Pair
    )

    val state: StateFlow<AppUiState> = combine(
        combinedCore,
        mutableHome,
        loginError,
        setupError,
        combinedSessionState
    ) { core, homeState, loginErr, setupErr, sessionState ->
        val (settings, cachedMessages, syncStatus) = core
        val (ready, authenticated) = sessionState
        val startDestination = when {
            !ready -> StartDestination.LOADING
            settings.serverConfig.baseUrl.isBlank() -> StartDestination.SERVER
            authenticated -> StartDestination.HOME
            else -> StartDestination.LOGIN
        }

        AppUiState(
            settings = settings,
            startDestination = startDestination,
            loginError = loginErr,
            setupError = setupErr,
            home = homeState.copy(
                cachedMessages = cachedMessages,
                groupedMessages = groupMessages(
                    if (homeState.offline) cachedMessages else homeState.onlineMessages
                ),
                lastSyncStatus = syncStatus,
                canLoadCachedData = settings.localStorageEnabled && cachedMessages.isNotEmpty(),
                offline = homeState.offline || (syncStatus.message?.contains("失败") == true && cachedMessages.isNotEmpty())
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState()
    )

    init {
        viewModelScope.launch {
            repository.restoreSession()
            val settings = repository.settings.first()
            val validation = if (settings.serverConfig.baseUrl.isNotBlank() && settings.hasSavedSession) {
                repository.validateSession()
            } else {
                Result.failure(IllegalStateException("没有已保存的会话"))
            }
            val validationError = validation.exceptionOrNull()
            val authenticated = validation.isSuccess ||
                (settings.hasSavedSession && validationError != null && !repository.isAuthenticationFailure(validationError))

            sessionAuthenticated.value = authenticated
            sessionReady.value = true

            if (validationError != null && authenticated) {
                showHomeError(validationError)
            }

            if (authenticated) {
                refreshHome()
            }
        }
    }

    fun updateServerConfig(serverConfig: ServerConfig) {
        viewModelScope.launch {
            runCatching {
                repository.updateServerConfig(serverConfig.copy(accessKey = ""))
            }.onSuccess {
                sessionAuthenticated.value = false
                sessionReady.value = true
                setupError.value = null
                refreshBootstrap()
            }.onFailure {
                setupError.value = it.message ?: "保存服务器配置失败"
            }
        }
    }

    fun login(serverConfig: ServerConfig) {
        viewModelScope.launch {
                repository.login(serverConfig)
                .onSuccess {
                    sessionAuthenticated.value = true
                    sessionReady.value = true
                    loginError.value = null
                    setupError.value = null
                    refreshHome()
                }
                .onFailure {
                    sessionAuthenticated.value = false
                    sessionReady.value = true
                    loginError.value = it.message ?: "登录失败"
                    setupError.value = it.message ?: "登录失败"
                }
        }
    }

    fun selectTopic(topic: String?) {
        mutableHome.value = mutableHome.value.copy(selectedTopic = topic, loading = true)
        vibrateIfNeeded()
        refreshHome()
    }

    fun updateQuery(query: String) {
        mutableHome.value = mutableHome.value.copy(query = query)
    }

    fun applyFilters(tags: Set<String>, query: String) {
        mutableHome.value = mutableHome.value.copy(
            selectedTags = tags,
            query = query,
            loading = true
        )
        vibrateIfNeeded()
        refreshHome()
    }

    fun changeViewMode(viewMode: ViewMode) {
        mutableHome.value = mutableHome.value.copy(viewMode = viewMode)
        vibrateIfNeeded()
    }

    fun toggleGroup(title: String) {
        val next = mutableHome.value.expandedGroups.toMutableSet()
        if (!next.add(title)) next.remove(title)
        mutableHome.value = mutableHome.value.copy(expandedGroups = next)
        vibrateIfNeeded()
    }

    fun selectMessage(message: MessageItem?) {
        mutableHome.value = mutableHome.value.copy(selectedMessage = message)
        if (message != null) vibrateIfNeeded()
    }

    fun clearHomeError() {
        mutableHome.value = mutableHome.value.copy(errorMessage = null)
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        vibrateIfNeeded()
        viewModelScope.launch { repository.updateThemeMode(themeMode) }
    }

    fun updateDynamicColor(enabled: Boolean) {
        vibrateIfNeeded()
        viewModelScope.launch { repository.updateDynamicColor(enabled) }
    }

    fun updateThemePreset(themePreset: ThemePreset) {
        vibrateIfNeeded()
        viewModelScope.launch { repository.updateThemePreset(themePreset) }
    }

    fun updateVibration(enabled: Boolean) {
        if (enabled) vibrateIfNeeded(force = true)
        viewModelScope.launch { repository.updateVibration(enabled) }
    }

    fun updateLocalStorage(enabled: Boolean) {
        vibrateIfNeeded()
        viewModelScope.launch {
            repository.updateLocalStorage(enabled)
            if (enabled) {
                val home = mutableHome.value
                repository.syncAll(
                    topic = home.selectedTopic,
                    tags = home.selectedTags.toList(),
                    query = home.query.takeIf { it.isNotBlank() }
                )
            } else {
                repository.clearCachedMessages()
            }
        }
    }

    fun refreshHome() {
        mutableHome.value = mutableHome.value.copy(loading = true)
        refreshBootstrap()
        syncCurrentFilters()
    }

    fun vibrate() {
        vibrateIfNeeded()
    }

    private fun refreshBootstrap() {
        viewModelScope.launch {
            val settings = state.value.settings
            if (settings.serverConfig.baseUrl.isBlank() || !sessionAuthenticated.value) {
                mutableHome.value = mutableHome.value.copy(loading = false)
                return@launch
            }

            runCatching { repository.bootstrap() }
                .onSuccess { bootstrap ->
                    val wasOffline = mutableHome.value.offline
                    mutableHome.value = mutableHome.value.copy(
                        channels = bootstrap.topics,
                        availableTags = bootstrap.tags,
                        loading = false,
                        errorMessage = null,
                        offline = if (mutableHome.value.onlineMessages.isNotEmpty()) false else wasOffline
                    )
                }
                .onFailure {
                    Log.e("NtfyHub", "bootstrap failed", it)
                    handleSessionFailure(it)
                    showHomeError(it)
                    mutableHome.value = mutableHome.value.copy(
                        loading = false,
                        offline = isNetworkFailure(it)
                    )
                }
        }
    }

    private fun syncCurrentFilters() {
        viewModelScope.launch {
            val settings = state.value.settings
            if (settings.serverConfig.baseUrl.isBlank() || !sessionAuthenticated.value) {
                mutableHome.value = mutableHome.value.copy(loading = false)
                return@launch
            }

            val home = mutableHome.value
            runCatching {
                repository.fetchMessages(
                    topic = home.selectedTopic,
                    tags = home.selectedTags.toList(),
                    query = home.query.takeIf { it.isNotBlank() }
                )
            }.onSuccess { direct ->
                if (state.value.settings.localStorageEnabled) {
                    repository.syncAll(
                        topic = home.selectedTopic,
                        tags = home.selectedTags.toList(),
                        query = home.query.takeIf { it.isNotBlank() }
                    )
                }
                mutableHome.value = mutableHome.value.copy(
                    loading = false,
                    onlineMessages = direct.messages.map(::mapMessageItem),
                    errorMessage = null,
                    offline = false,
                    lastRemoteRefreshAtMillis = System.currentTimeMillis()
                )
            }.onFailure {
                Log.e("NtfyHub", "messages failed", it)
                handleSessionFailure(it)
                showHomeError(it)
                mutableHome.value = mutableHome.value.copy(
                    loading = false,
                    offline = isNetworkFailure(it)
                )
            }
        }
    }

    private fun handleSessionFailure(throwable: Throwable) {
        if (!isAuthenticationFailure(throwable)) return
        viewModelScope.launch {
            repository.invalidateSession()
            sessionAuthenticated.value = false
        }
    }

    private fun isAuthenticationFailure(throwable: Throwable): Boolean {
        return repository.isAuthenticationFailure(throwable) || throwable.message.orEmpty().contains("登录已失效")
    }

    private fun showHomeError(throwable: Throwable) {
        mutableHome.value = mutableHome.value.copy(errorMessage = errorMessage(throwable))
    }

    private fun errorMessage(throwable: Throwable): String {
        if (isNetworkFailure(throwable)) return "网络无法连接，请检查设备网络或服务器地址。"
        if (repository.isAuthenticationFailure(throwable)) return "服务器拒绝了当前会话，请重新输入访问密钥。"
        if (throwable is HttpException) return "服务器返回错误（HTTP ${throwable.code()}）。"
        return "服务器数据处理失败：${throwable.message ?: "未知错误"}"
    }

    private fun isNetworkFailure(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is IOException || current is SocketTimeoutException) return true
            current = current.cause
        }
        return false
    }

    private fun groupMessages(messages: List<MessageItem>): List<MessageGroup> =
        messages.groupBy { it.title }.map { (title, items) -> MessageGroup(title, items) }

    private fun mapMessageItem(dto: top.lvziwang.ntfyhub.model.MessageDto): MessageItem =
        MessageItem(
            id = dto.id,
            ntfyMessageId = dto.ntfyMessageId,
            title = dto.title?.trim()?.removePrefix("\"")?.removeSuffix("\"").orEmpty().ifBlank { "无标题消息" },
            rawTitle = dto.title,
            messageText = dto.messageText.orEmpty(),
            priority = dto.priority,
            tags = dto.tags.orEmpty(),
            clickUrl = dto.clickUrl,
            ntfyTime = dto.ntfyTime,
            receivedAt = dto.receivedAt,
            rawJson = dto.rawJson?.toString(),
            topic = dto.topic,
            sourceUser = dto.sourceUser
        )

    private fun vibrateIfNeeded(force: Boolean = false) {
        if (!force && !state.value.settings.vibrationEnabled) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else {
            VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator?.vibrate(effect)
    }
}

class AppViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
        val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        return AppViewModel(application, container) as T
    }
}
