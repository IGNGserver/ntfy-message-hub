package top.lvziwang.ntfyhub.ui.viewmodel

sealed class Route(val route: String) {
    data object Loading : Route("loading")
    data object ServerSetup : Route("server_setup")
    data object Login : Route("login")
    data object Home : Route("home")
    data object Settings : Route("settings")
}
