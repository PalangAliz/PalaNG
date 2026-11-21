package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AppSettings
import com.example.data.model.ProtocolType
import com.example.data.model.ServerConfig
import com.example.data.model.ThemeMode
import com.example.service.ConnectionState
import com.example.service.XrayVpnService
import com.example.ui.ConnectionInfoState
import com.example.ui.MainViewModel
import com.example.ui.components.PalangLineArtLogo
import com.example.ui.theme.AmberLatency
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.PumpkinYellow
import com.example.ui.theme.PumpkinYellowDeep
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onRequestConnect: () -> Unit,
    onNavigateEdit: (serverId: String?, initialProtocol: String) -> Unit,
    onNavigateQrScanner: () -> Unit,
    onNavigateSubscriptions: () -> Unit,
    onNavigateRouting: () -> Unit,
    onNavigatePerApp: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateLogs: () -> Unit,
    onNavigateAssets: () -> Unit,
    onNavigateAbout: () -> Unit
) {
    val uiState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val trafficStats by viewModel.trafficStats.collectAsStateWithLifecycle()
    val lastTestedDelay by viewModel.lastTestedDelayMs.collectAsStateWithLifecycle()
    val isTestingActiveDelay by viewModel.isTestingActiveDelay.collectAsStateWithLifecycle()
    val routingRules by viewModel.routingRulesFlow.collectAsStateWithLifecycle()
    val vpnError by viewModel.vpnError.collectAsStateWithLifecycle()
    val connectionInfoState by viewModel.connectionInfoState.collectAsStateWithLifecycle()
    val activeProxy by viewModel.activeProxy.collectAsStateWithLifecycle()

    val isDark = LocalIsDarkTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    var showSearch by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<ServerConfig?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<ServerConfig?>(null) }

    val jsonFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { text ->
                viewModel.importBatchText(text)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(uiState.settings.showAllGroupTab, uiState.settings.selectedGroupId) {
        if (!uiState.settings.showAllGroupTab && uiState.settings.selectedGroupId == AppSettings.GROUP_ALL) {
            viewModel.selectGroup(AppSettings.GROUP_DEFAULT)
        }
    }

    LaunchedEffect(vpnError) {
        val err = vpnError
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearVpnError()
        }
    }

    deleteConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message, target.remarks)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteServer(target.id)
                        deleteConfirmTarget = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (connectionInfoState !is ConnectionInfoState.Idle) {
        ConnectionInfoDialog(
            state = connectionInfoState,
            onRetest = { viewModel.testCurrentConnectionInfo() },
            onDismiss = { viewModel.clearConnectionInfo() }
        )
    }

    shareTarget?.let { target ->
        ShareProfileDialog(
            server = target,
            settings = uiState.settings,
            routingRules = routingRules,
            onCopyText = { label, text ->
                viewModel.copyToClipboard(label, text)
                scope.launch { snackbarHostState.showSnackbar("$label copied to clipboard") }
            },
            onDismiss = { shareTarget = null }
        )
    }

    val navItemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = if (isDark) PumpkinYellow.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        selectedIconColor = if (isDark) PumpkinYellow else MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.onSurface
    )

    val selectedServer = remember(uiState.servers, uiState.settings.selectedServerId) {
        uiState.servers.firstOrNull { it.id == uiState.settings.selectedServerId }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                modifier = Modifier.width(292.dp)
            ) {
                // Clean Editorial Drawer Header (matches current Light/Dark surface)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PalangLineArtLogo(
                            modifier = Modifier.size(46.dp),
                            contentDescription = "PalaNG logo"
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = stringResource(R.string.brand_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.6.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) PumpkinYellow else PumpkinYellowDeep
                            )
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} · ${if (uiState.settings.vpnMode) "TUN" else "SOCKS"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(2.5.dp)
                            .clip(CircleShape)
                            .background(if (isDark) PumpkinYellow else PumpkinYellowDeep)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                Spacer(modifier = Modifier.height(10.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_profiles), fontWeight = FontWeight.Medium) },
                    selected = true,
                    colors = navItemColors,
                    shape = RoundedCornerShape(12.dp),
                    onClick = { scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Outlined.RssFeed, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_subscriptions)) },
                    selected = false,
                    colors = navItemColors,
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateSubscriptions()
                    },
                    icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .testTag("drawer_subscriptions")
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_routing)) },
                    selected = false,
                    colors = navItemColors,
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateRouting()
                    },
                    icon = { Icon(Icons.Outlined.AltRoute, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .testTag("drawer_routing")
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_per_app)) },
                    selected = false,
                    colors = navItemColors,
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigatePerApp()
                    },
                    icon = { Icon(Icons.Outlined.Apps, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .testTag("drawer_per_app")
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_settings)) },
                    selected = false,
                    colors = navItemColors,
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateSettings()
                    },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .testTag("drawer_settings")
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 18.dp)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_logs)) },
                    selected = false,
                    colors = navItemColors,
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateLogs()
                    },
                    icon = { Icon(Icons.AutoMirrored.Outlined.Article, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .testTag("drawer_logs")
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_assets)) },
                    selected = false,
                    colors = navItemColors,
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateAssets()
                    },
                    icon = { Icon(Icons.Outlined.Storage, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .testTag("drawer_assets")
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_about)) },
                    selected = false,
                    colors = navItemColors,
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateAbout()
                    },
                    icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .testTag("drawer_about")
                )
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.navigationBarsPadding()
                )
            },
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                            actionIconContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("drawer_menu_button")
                            ) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Open navigation drawer")
                            }
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PalangLineArtLogo(
                                    modifier = Modifier.size(32.dp),
                                    contentDescription = "PalaNG emblem"
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.4).sp,
                                        maxLines = 1
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.brand_subtitle),
                                            style = MaterialTheme.typography.labelSmall,
                                            letterSpacing = 1.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) PumpkinYellow else PumpkinYellowDeep
                                        )
                                        Text(
                                            text = "· ${if (uiState.settings.vpnMode) "TUN" else "SOCKS"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            val nextThemeIsLight = isDark
                            IconButton(
                                onClick = {
                                    val next = if (nextThemeIsLight) ThemeMode.LIGHT else ThemeMode.DARK
                                    viewModel.updateSettings { it.copy(themeMode = next) }
                                },
                                modifier = Modifier.testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                    contentDescription = stringResource(R.string.settings_theme)
                                )
                            }
                            IconButton(
                                onClick = {
                                    showSearch = !showSearch
                                    if (!showSearch) viewModel.setSearchQuery("")
                                },
                                modifier = Modifier.testTag("search_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (showSearch) Icons.Outlined.Close else Icons.Outlined.Search,
                                    contentDescription = "Search profiles"
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showAddMenu = true },
                                    modifier = Modifier.testTag("add_config_button")
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_add))
                                }
                                DropdownMenu(
                                    expanded = showAddMenu,
                                    onDismissRequest = { showAddMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_import_clipboard)) },
                                        leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                                        onClick = {
                                            showAddMenu = false
                                            viewModel.importFromClipboard()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_scan_qr)) },
                                        leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, null) },
                                        onClick = {
                                            showAddMenu = false
                                            onNavigateQrScanner()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_import_custom)) },
                                        leadingIcon = { Icon(Icons.Outlined.Storage, null) },
                                        onClick = {
                                            showAddMenu = false
                                            jsonFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                        }
                                    )
                                    HorizontalDivider()
                                    listOf(
                                        ProtocolType.AMIR to R.string.action_manual_amir,
                                        ProtocolType.VLESS to R.string.action_manual_vless,
                                        ProtocolType.VMESS to R.string.action_manual_vmess,
                                        ProtocolType.TROJAN to R.string.action_manual_trojan,
                                        ProtocolType.SHADOWSOCKS to R.string.action_manual_ss,
                                        ProtocolType.SOCKS to R.string.action_manual_socks,
                                        ProtocolType.WIREGUARD to R.string.action_manual_wg,
                                        ProtocolType.HYSTERIA2 to R.string.action_manual_hy2
                                    ).forEach { (proto, labelRes) ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(labelRes)) },
                                            onClick = {
                                                showAddMenu = false
                                                onNavigateEdit(null, proto.name)
                                            }
                                        )
                                    }
                                }
                            }

                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier.testTag("overflow_menu_button")
                                ) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (uiState.isTestingBatch) stringResource(R.string.menu_stop_delay_test) else stringResource(R.string.menu_real_delay_all)
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.Speed, null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.testAllVisibleDelay()
                                        }
                                    )
                                    if (uiState.connectionState == ConnectionState.CONNECTED) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_connection_info)) },
                                            leadingIcon = { Icon(Icons.Outlined.Public, null) },
                                            onClick = {
                                                showOverflowMenu = false
                                                viewModel.testCurrentConnectionInfo()
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_sort_delay)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.sortByTestResults()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_update_subs)) },
                                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.updateAllSubscriptions()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_remove_duplicates)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.removeDuplicates()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_remove_invalid)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.removeInvalidConfigs()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_export_all)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.exportAllVisibleToClipboard()
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.menu_delete_all),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.deleteAllInCurrentGroup()
                                        }
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Top Architectural Bento Hero Connection Card
                HeroConnectionCard(
                    connectionState = uiState.connectionState,
                    selectedServer = selectedServer,
                    speedDisplayEnabled = uiState.settings.speedDisplayEnabled,
                    activeSocksPort = if (activeProxy.enabled && uiState.settings.dynamicProxyPortEnabled) activeProxy.socksPort else null,
                    uplinkBps = trafficStats.uplinkSpeedBps,
                    downlinkBps = trafficStats.downlinkSpeedBps,
                    totalUpload = trafficStats.totalUploadBytes,
                    totalDownload = trafficStats.totalDownloadBytes,
                    lastTestedDelay = lastTestedDelay,
                    isTestingDelay = isTestingActiveDelay,
                    onTapStatusTest = {
                        if (uiState.connectionState == ConnectionState.CONNECTED) {
                            viewModel.measureConnectedDelay()
                        }
                    },
                    onTestConnectionInfo = { viewModel.testCurrentConnectionInfo() },
                    onToggleConnect = onRequestConnect,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )

                AnimatedVisibility(visible = showSearch) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        ),
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Outlined.Close, "Clear search")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("search_profiles_input")
                    )
                }

                // Minimalist Capsule Group Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.settings.showAllGroupTab) {
                        MinimalGroupPill(
                            label = "${stringResource(R.string.group_all)} (${uiState.servers.size})",
                            selected = uiState.settings.selectedGroupId == AppSettings.GROUP_ALL,
                            onClick = { viewModel.selectGroup(AppSettings.GROUP_ALL) },
                            modifier = Modifier.testTag("group_chip_all")
                        )
                    }
                    val defaultCount = uiState.servers.count { it.subscriptionId.isEmpty() }
                    MinimalGroupPill(
                        label = "${stringResource(R.string.group_default)} ($defaultCount)",
                        selected = uiState.settings.selectedGroupId == AppSettings.GROUP_DEFAULT,
                        onClick = { viewModel.selectGroup(AppSettings.GROUP_DEFAULT) },
                        modifier = Modifier.testTag("group_chip_default")
                    )
                    uiState.subscriptions.forEach { sub ->
                        val count = uiState.servers.count { it.subscriptionId == sub.id }
                        MinimalGroupPill(
                            label = "${sub.remarks} ($count)",
                            selected = uiState.settings.selectedGroupId == sub.id,
                            onClick = { viewModel.selectGroup(sub.id) }
                        )
                    }
                }

                // Batch Delay / Subscription Update progress banner
                val progress = uiState.batchProgress
                if (uiState.isTestingBatch && progress != null) {
                    val fraction = if (progress.second > 0) progress.first.toFloat() / progress.second else 0f
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Testing real delay (${progress.first}/${progress.second})…",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                TextButton(onClick = { viewModel.testAllVisibleDelay() }) {
                                    Text("Stop")
                                }
                            }
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                } else if (uiState.isUpdatingSubs) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (uiState.visibleServers.isEmpty()) {
                    EmptyProfilesState(
                        onImportClipboard = { viewModel.importFromClipboard() },
                        onScanQr = onNavigateQrScanner,
                        onManualAdd = { onNavigateEdit(null, ProtocolType.VLESS.name) }
                    )
                } else if (uiState.settings.twoColumnLayout) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        gridItems(uiState.visibleServers, key = { it.id }) { server ->
                            val isSelected = server.id == uiState.settings.selectedServerId
                            val isConnected = isSelected && uiState.connectionState == ConnectionState.CONNECTED
                            MinimalServerGridCard(
                                server = server,
                                isSelected = isSelected,
                                isConnected = isConnected,
                                onSelect = { viewModel.selectServer(server.id) },
                                onTestDelay = { viewModel.testSingleServerDelay(server) },
                                onShare = { shareTarget = server },
                                onEdit = { onNavigateEdit(server.id, server.protocol.name) },
                                onDelete = {
                                    if (uiState.settings.confirmDeleteConfig) {
                                        deleteConfirmTarget = server
                                    } else {
                                        viewModel.deleteServer(server.id)
                                    }
                                }
                            )
                        }
                        item(span = { GridItemSpan(2) }) {
                            Spacer(modifier = Modifier.navigationBarsPadding().height(8.dp))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        items(uiState.visibleServers, key = { it.id }) { server ->
                            val isSelected = server.id == uiState.settings.selectedServerId
                            val isConnected = isSelected && uiState.connectionState == ConnectionState.CONNECTED
                            MinimalServerRowCard(
                                server = server,
                                isSelected = isSelected,
                                isConnected = isConnected,
                                onSelect = { viewModel.selectServer(server.id) },
                                onTestDelay = { viewModel.testSingleServerDelay(server) },
                                onShare = { shareTarget = server },
                                onEdit = { onNavigateEdit(server.id, server.protocol.name) },
                                onDelete = {
                                    if (uiState.settings.confirmDeleteConfig) {
                                        deleteConfirmTarget = server
                                    } else {
                                        viewModel.deleteServer(server.id)
                                    }
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.navigationBarsPadding().height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroConnectionCard(
    connectionState: ConnectionState,
    selectedServer: ServerConfig?,
    speedDisplayEnabled: Boolean,
    activeSocksPort: Int?,
    uplinkBps: Long,
    downlinkBps: Long,
    totalUpload: Long,
    totalDownload: Long,
    lastTestedDelay: Long?,
    isTestingDelay: Boolean,
    onTapStatusTest: () -> Unit,
    onTestConnectionInfo: () -> Unit,
    onToggleConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val isConnected = connectionState == ConnectionState.CONNECTED
    val isBusy = connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.DISCONNECTING

    val powerColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> EmeraldConnected
            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> AmberLatency
            ConnectionState.DISCONNECTED -> PumpkinYellow
        },
        label = "heroPowerColor"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isConnected -> EmeraldConnected.copy(alpha = 0.55f)
            isBusy -> AmberLatency.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "heroBorderColor"
    )
    val pulseTransition = rememberInfiniteTransition(label = "heroPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "heroPulseAlpha"
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.94f else 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(if (isConnected || isBusy) 1.25.dp else 0.85.dp, cardBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = isConnected, onClick = onTapStatusTest)
                        .padding(end = 12.dp, top = 2.dp, bottom = 2.dp)
                        .testTag("bottom_status_bar"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        val dotColor = when (connectionState) {
                            ConnectionState.CONNECTED -> EmeraldConnected
                            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> AmberLatency
                            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
                        }
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(12.dp)) {
                            if (isConnected || isBusy) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(dotColor.copy(alpha = pulseAlpha * 0.42f))
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                        val statusLabel = when (connectionState) {
                            ConnectionState.DISCONNECTED -> stringResource(R.string.status_not_connected)
                            ConnectionState.CONNECTING -> stringResource(R.string.status_connecting)
                            ConnectionState.DISCONNECTING -> stringResource(R.string.status_disconnecting)
                            ConnectionState.CONNECTED -> when {
                                isTestingDelay -> stringResource(R.string.checking_connection)
                                lastTestedDelay != null && lastTestedDelay >= 0 ->
                                    stringResource(R.string.connected_delay_ms, lastTestedDelay)
                                lastTestedDelay != null && lastTestedDelay < 0 ->
                                    stringResource(R.string.connected_timeout)
                                else -> stringResource(R.string.tap_to_test)
                            }
                        }
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isConnected) EmeraldConnected else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = selectedServer?.remarks ?: stringResource(R.string.no_server_selected),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val meta = buildString {
                        if (selectedServer != null) {
                            append(selectedServer.protocol.displayName.uppercase())
                            append(" · ")
                            append(selectedServer.maskedAddress())
                        } else {
                            append(stringResource(R.string.empty_profiles_hint))
                        }
                        if (activeSocksPort != null && isConnected) {
                            append(" · :$activeSocksPort")
                        }
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isConnected) {
                        IconButton(
                            onClick = onTestConnectionInfo,
                            modifier = Modifier.testTag("connection_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Public,
                                contentDescription = stringResource(R.string.menu_connection_info),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val powerContentColor = when (connectionState) {
                        ConnectionState.CONNECTED -> Color.White
                        else -> Color(0xFF1A1300)
                    }
                    Surface(
                        onClick = onToggleConnect,
                        shape = RoundedCornerShape(16.dp),
                        color = powerColor,
                        contentColor = powerContentColor,
                        modifier = Modifier
                            .size(width = 58.dp, height = 52.dp)
                            .testTag("vpn_connect_fab")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when {
                                isBusy -> CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.2.dp,
                                    color = powerContentColor
                                )
                                isConnected -> Icon(
                                    imageVector = Icons.Outlined.Stop,
                                    contentDescription = "Disconnect VPN",
                                    modifier = Modifier.size(24.dp)
                                )
                                else -> Icon(
                                    imageVector = Icons.Outlined.PowerSettingsNew,
                                    contentDescription = "Connect VPN",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isConnected && speedDisplayEnabled) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↑ ${XrayVpnService.formatSpeed(uplinkBps)} (${XrayVpnService.formatBytes(totalUpload)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Text(
                            text = "↓ ${XrayVpnService.formatSpeed(downlinkBps)} (${XrayVpnService.formatBytes(totalDownload)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldConnected,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalGroupPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val containerColor = when {
        selected && isDark -> PumpkinYellow
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }
    val contentColor = when {
        selected && isDark -> Color(0xFF1A1200)
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) null else BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MinimalServerRowCard(
    server: ServerConfig,
    isSelected: Boolean,
    isConnected: Boolean,
    onSelect: () -> Unit,
    onTestDelay: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val activeAccent = if (isDark) PumpkinYellow else PumpkinYellowDeep
    val indicatorColor by animateColorAsState(
        targetValue = when {
            isConnected -> EmeraldConnected
            isSelected -> activeAccent
            else -> Color.Transparent
        },
        label = "rowIndicatorColor"
    )
    val cardContainer = MaterialTheme.colorScheme.surface.copy(
        alpha = when {
            isConnected || isSelected -> 0.96f
            else -> 0.93f
        }
    )
    val cardBorder = BorderStroke(
        width = if (isSelected || isConnected) 1.2.dp else 0.85.dp,
        color = when {
            isConnected -> EmeraldConnected.copy(alpha = 0.65f)
            isSelected -> activeAccent.copy(alpha = 0.65f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
        }
    )
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .clickable(onClick = onSelect)
            .testTag("server_card_${server.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(indicatorColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MinimalProtocolTag(server.protocol)
                    Text(
                        text = server.remarks,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Text(
                    text = "${server.maskedAddress()} · ${server.transportSummary()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                LatencyBadge(latencyMs = server.latencyMs, onClick = onTestDelay)
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "Actions for ${server.remarks}",
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Outlined.Share, null) },
                            onClick = {
                                showMenu = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Test Delay") },
                            leadingIcon = { Icon(Icons.Outlined.Speed, null) },
                            onClick = {
                                showMenu = false
                                onTestDelay()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalProtocolTag(protocol: ProtocolType) {
    val isDark = LocalIsDarkTheme.current
    val isAmir = protocol == ProtocolType.AMIR
    val textColor = when {
        isAmir && isDark -> PumpkinYellow
        isAmir -> PumpkinYellowDeep
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = when {
        isAmir -> PumpkinYellow.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f)
    }
    val borderColor = when {
        isAmir -> PumpkinYellow.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(0.65.dp, borderColor)
    ) {
        Text(
            text = protocol.displayName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (isAmir) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun LatencyBadge(latencyMs: Long, onClick: () -> Unit) {
    val (label, color) = when {
        latencyMs == ServerConfig.UNTESTED_LATENCY -> "Test" to MaterialTheme.colorScheme.onSurfaceVariant
        latencyMs < 0 -> "Timeout" to CoralError
        latencyMs < 350 -> "${latencyMs} ms" to EmeraldConnected
        latencyMs < 900 -> "${latencyMs} ms" to AmberLatency
        else -> "${latencyMs} ms" to CoralError
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (latencyMs != ServerConfig.UNTESTED_LATENCY) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = if (latencyMs >= 0) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun MinimalServerGridCard(
    server: ServerConfig,
    isSelected: Boolean,
    isConnected: Boolean,
    onSelect: () -> Unit,
    onTestDelay: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val activeAccent = if (isDark) PumpkinYellow else PumpkinYellowDeep
    val indicatorColor by animateColorAsState(
        targetValue = when {
            isConnected -> EmeraldConnected
            isSelected -> activeAccent
            else -> Color.Transparent
        },
        label = "gridIndicatorColor"
    )
    val cardContainer = MaterialTheme.colorScheme.surface.copy(
        alpha = if (isSelected || isConnected) 0.96f else 0.93f
    )
    val cardBorder = BorderStroke(
        width = if (isSelected || isConnected) 1.2.dp else 0.85.dp,
        color = when {
            isConnected -> EmeraldConnected.copy(alpha = 0.65f)
            isSelected -> activeAccent.copy(alpha = 0.65f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
        }
    )
    var showCardMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("server_card_${server.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(indicatorColor)
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MinimalProtocolTag(server.protocol)
                    LatencyBadge(latencyMs = server.latencyMs, onClick = onTestDelay)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = server.remarks,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = server.maskedAddress(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(
                            onClick = { showCardMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "More actions for ${server.remarks}",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showCardMenu,
                            onDismissRequest = { showCardMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = {
                                    showCardMenu = false
                                    onShare()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = {
                                    showCardMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showCardMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionInfoDialog(
    state: ConnectionInfoState,
    onRetest: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_connection_info)) },
        text = {
            when (state) {
                ConnectionInfoState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.checking_connection))
                    }
                }
                is ConnectionInfoState.Success -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.countryFlag.isNotEmpty() || state.countryLabel.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("connection_info_country_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (state.countryFlag.isNotEmpty()) {
                                        Text(
                                            text = state.countryFlag,
                                            style = MaterialTheme.typography.headlineSmall
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            text = state.countryLabel.ifBlank { state.countryFlag },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${state.elapsedMs} ms",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldConnected,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "${state.elapsedMs} ms • ${state.url}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (state.countryFlag.isNotEmpty() || state.countryLabel.isNotEmpty()) {
                            Text(
                                text = state.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.summaryLines.isNotEmpty()) {
                            state.summaryLines.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = state.rawBody.take(400),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                is ConnectionInfoState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                ConnectionInfoState.Idle -> Unit
            }
        },
        confirmButton = {
            if (state !is ConnectionInfoState.Loading) {
                TextButton(onClick = onRetest) {
                    Text(stringResource(R.string.action_retest))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun EmptyProfilesState(
    onImportClipboard: () -> Unit,
    onScanQr: () -> Unit,
    onManualAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PalangLineArtLogo(
            modifier = Modifier.size(90.dp),
            contentDescription = "PalaNG logo"
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.empty_profiles_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_profiles_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 310.dp)
        )
        Spacer(modifier = Modifier.height(22.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onImportClipboard,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("empty_import_clipboard")
            ) {
                Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_clipboard))
            }
            OutlinedButton(
                onClick = onScanQr,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("empty_scan_qr")
            ) {
                Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_scan_qr))
            }
            OutlinedButton(
                onClick = onManualAdd,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("empty_add_manual")
            ) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_manual))
            }
        }
    }
}
