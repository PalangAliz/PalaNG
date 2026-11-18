package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data class ProfileEdit(
        val serverId: String? = null,
        val initialProtocol: String = "VLESS"
    ) : AppRoute

    @Serializable
    data object QrScanner : AppRoute

    @Serializable
    data object Subscriptions : AppRoute

    @Serializable
    data object RoutingSettings : AppRoute

    @Serializable
    data object PerAppProxy : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object Logs : AppRoute

    @Serializable
    data object Assets : AppRoute

    @Serializable
    data object About : AppRoute
}
