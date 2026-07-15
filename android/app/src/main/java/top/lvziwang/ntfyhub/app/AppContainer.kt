package top.lvziwang.ntfyhub.app

import android.content.Context
import top.lvziwang.ntfyhub.data.local.HubDatabase
import top.lvziwang.ntfyhub.data.local.SettingsStore
import top.lvziwang.ntfyhub.data.remote.HubApi
import top.lvziwang.ntfyhub.data.remote.SessionManager
import top.lvziwang.ntfyhub.data.repository.HubRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = HubDatabase.create(appContext)
    private val settingsStore = SettingsStore(appContext)
    private val sessionManager = SessionManager()
    private val api = HubApi.create(sessionManager)

    val repository = HubRepository(
        settingsStore = settingsStore,
        api = api,
        sessionManager = sessionManager,
        messageDao = database.messageDao(),
        syncStateDao = database.syncStateDao()
    )
}
