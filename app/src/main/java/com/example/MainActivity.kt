package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.LanguageMode
import com.example.service.ConnectionState
import com.example.ui.MainViewModel
import com.example.ui.components.PalangBackdrop
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AssetsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.PerAppProxyScreen
import com.example.ui.screens.ProfileEditScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.RoutingSettingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.theme.XrayNgTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current

            val localizedContext = remember(settings.languageMode, baseContext) {
                when (settings.languageMode) {
                    LanguageMode.EN -> applyLocale(baseContext, Locale.ENGLISH)
                    LanguageMode.FA -> applyLocale(baseContext, Locale("fa"))
                    LanguageMode.SYSTEM -> baseContext
                }
            }
            val layoutDirection = if (settings.languageMode == LanguageMode.FA ||
                (settings.languageMode == LanguageMode.SYSTEM && baseConfig.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL)
            ) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                LocalLayoutDirection provides layoutDirection
            ) {
                XrayNgTheme(
                    themeMode = settings.themeMode,
                    dynamicColor = settings.dynamicColorEnabled
                ) {
                    val navController = rememberNavController()

                    val vpnConsentLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == RESULT_OK) {
                            viewModel.toggleVpnConnection()
                        }
                    }

                    val vpnReconnectConsentLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == RESULT_OK) {
                            viewModel.applyPendingTunnelRestartIfNeeded()
                        }
                    }

                    val onApplyReconnect: () -> Unit = {
                        if (viewModel.pendingTunnelRestart.value) {
                            val latestSettings = viewModel.settingsFlow.value
                            val state = viewModel.connectionState.value
                            val active = state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING
                            val prepareIntent: Intent? = if (active && latestSettings.vpnMode) {
                                VpnService.prepare(this)
                            } else {
                                null
                            }
                            if (prepareIntent != null) {
                                vpnReconnectConsentLauncher.launch(prepareIntent)
                            } else {
                                viewModel.applyPendingTunnelRestartIfNeeded()
                            }
                        }
                    }

                    val notificationPermLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) {
                        val prepareIntent = if (settings.vpnMode) VpnService.prepare(this) else null
                        if (prepareIntent != null) {
                            vpnConsentLauncher.launch(prepareIntent)
                        } else {
                            viewModel.toggleVpnConnection()
                        }
                    }

                    val onRequestConnect: () -> Unit = {
                        val state = viewModel.connectionState.value
                        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
                            viewModel.toggleVpnConnection()
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val prepareIntent: Intent? = if (settings.vpnMode) VpnService.prepare(this) else null
                                if (prepareIntent != null) {
                                    vpnConsentLauncher.launch(prepareIntent)
                                } else {
                                    viewModel.toggleVpnConnection()
                                }
                            }
                        }
                    }

                    PalangBackdrop {
                        NavHost(
                            navController = navController,
                            startDestination = AppRoute.Home
                        ) {
                        composable<AppRoute.Home> {
                            HomeScreen(
                                viewModel = viewModel,
                                onRequestConnect = onRequestConnect,
                                onNavigateEdit = { serverId, proto ->
                                    navController.navigate(AppRoute.ProfileEdit(serverId, proto))
                                },
                                onNavigateQrScanner = { navController.navigate(AppRoute.QrScanner) },
                                onNavigateSubscriptions = { navController.navigate(AppRoute.Subscriptions) },
                                onNavigateRouting = { navController.navigate(AppRoute.RoutingSettings) },
                                onNavigatePerApp = { navController.navigate(AppRoute.PerAppProxy) },
                                onNavigateSettings = { navController.navigate(AppRoute.Settings) },
                                onNavigateLogs = { navController.navigate(AppRoute.Logs) },
                                onNavigateAssets = { navController.navigate(AppRoute.Assets) },
                                onNavigateAbout = { navController.navigate(AppRoute.About) }
                            )
                        }
                        composable<AppRoute.ProfileEdit> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.ProfileEdit>()
                            ProfileEditScreen(
                                viewModel = viewModel,
                                serverId = route.serverId,
                                initialProtocol = route.initialProtocol,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<AppRoute.QrScanner> {
                            QrScannerScreen(
                                onQrDecoded = { text ->
                                    viewModel.importBatchText(text)
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<AppRoute.Subscriptions> {
                            SubscriptionsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<AppRoute.RoutingSettings> {
                            RoutingSettingsScreen(
                                viewModel = viewModel,
                                onApplyReconnect = onApplyReconnect,
                                onBack = {
                                    onApplyReconnect()
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable<AppRoute.PerAppProxy> {
                            PerAppProxyScreen(
                                viewModel = viewModel,
                                onApplyReconnect = onApplyReconnect,
                                onBack = {
                                    onApplyReconnect()
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable<AppRoute.Settings> {
                            SettingsScreen(
                                viewModel = viewModel,
                                onApplyReconnect = onApplyReconnect,
                                onBack = {
                                    onApplyReconnect()
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable<AppRoute.Logs> {
                            LogsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<AppRoute.Assets> {
                            AssetsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<AppRoute.About> {
                            AboutScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        }
                    }
                }
            }
        }
    }

    private fun applyLocale(context: android.content.Context, locale: Locale): android.content.Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
