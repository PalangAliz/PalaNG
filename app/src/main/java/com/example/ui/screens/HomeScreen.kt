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
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AppSettings
import com.example.data.model.ProtocolType
import com.example.data.model.ServerConfig
import com.example.data.model.ThemeMode
import com.example.service.ConnectionState
import com.example.service.XrayVpnService
import com.example.ui.ConnectionInfoState
import com.example.ui.MainViewModel
import com.example.ui.theme.AmberLatency
import com.example.ui.theme.BlueProtocol
import com.example.ui.theme.BrandCanvasDark
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.IndigoProtocol
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.PumpkinYellow
import com.example.ui.theme.PumpkinYellowDeep
import com.example.ui.theme.PurpleReality
import com.example.ui.theme.TealProtocol
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
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(304.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    BrandCanvasDark,
                                    BrandCanvasDark.copy(alpha = 0.92f)
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.25.dp, PumpkinYellow.copy(alpha = 0.7f)),
                            color = BrandCanvasDark,
                            shadowElevation = 4.dp
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_palang_logo),
                                contentDescription = "PalaNG logo",
                                modifier = Modifier
                                    .size(54.dp)
                                    .padding(5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = PumpkinYellow.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.brand_subtitle),
                                        style = MaterialTheme.typography.labelSmall,
                                        letterSpacing = 1.2.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PumpkinYellow,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (uiState.settings.vpnMode) "VPN" else "PROXY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFCBD5E1),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_profiles)) },
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
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
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
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
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
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BrandCanvasDark,
                                    border = BorderStroke(
                                        1.25.dp,
                                        PumpkinYellow.copy(alpha = 0.75f)
                                    )
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_palang_logo),
                                        contentDescription = "PalaNG emblem",
                                        modifier = Modifier
                                            .size(36.dp)
                                            .padding(3.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.brand_subtitle),
                                            style = MaterialTheme.typography.labelSmall,
                                            letterSpacing = 1.2.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "• ${if (uiState.settings.vpnMode) "TUN" else "SOCKS"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            val isDark = uiState.settings.themeMode == ThemeMode.DARK
                            IconButton(
                                onClick = {
                                    val next = if (isDark) ThemeMode.LIGHT else ThemeMode.DARK
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
                    HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
            bottomBar = {
                VpnBottomControlDock(
                    connectionState = uiState.connectionState,
                    activeRemarks = uiState.servers.firstOrNull { it.id == uiState.settings.selectedServerId }?.remarks
                        ?: "",
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
                    onToggleConnect = onRequestConnect
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedVisibility(visible = showSearch) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
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
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("search_profiles_input")
                    )
                }

                // Group tabs
                val chipShape = RoundedCornerShape(10.dp)
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.settings.showAllGroupTab) {
                        FilterChip(
                            selected = uiState.settings.selectedGroupId == AppSettings.GROUP_ALL,
                            onClick = { viewModel.selectGroup(AppSettings.GROUP_ALL) },
                            label = { Text("${stringResource(R.string.group_all)} (${uiState.servers.size})") },
                            shape = chipShape,
                            colors = chipColors,
                            modifier = Modifier.testTag("group_chip_all")
                        )
                    }
                    val defaultCount = uiState.servers.count { it.subscriptionId.isEmpty() }
                    FilterChip(
                        selected = uiState.settings.selectedGroupId == AppSettings.GROUP_DEFAULT,
                        onClick = { viewModel.selectGroup(AppSettings.GROUP_DEFAULT) },
                        label = { Text("${stringResource(R.string.group_default)} ($defaultCount)") },
                        shape = chipShape,
                        colors = chipColors,
                        modifier = Modifier.testTag("group_chip_default")
                    )
                    uiState.subscriptions.forEach { sub ->
                        val count = uiState.servers.count { it.subscriptionId == sub.id }
                        FilterChip(
                            selected = uiState.settings.selectedGroupId == sub.id,
                            onClick = { viewModel.selectGroup(sub.id) },
                            label = { Text("${sub.remarks} ($count)") },
                            shape = chipShape,
                            colors = chipColors
                        )
                    }
                }

                // Batch Delay / Subscription Update progress banner
                val progress = uiState.batchProgress
                if (uiState.isTestingBatch && progress != null) {
                    val fraction = if (progress.second > 0) progress.first.toFloat() / progress.second else 0f
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
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
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        gridItems(uiState.visibleServers, key = { it.id }) { server ->
                            val isSelected = server.id == uiState.settings.selectedServerId
                            val isConnected = isSelected && uiState.connectionState == ConnectionState.CONNECTED
                            CompactServerGridCard(
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
                        item(span = { GridItemSpan(2) }) { Spacer(modifier = Modifier.height(14.dp)) }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.visibleServers, key = { it.id }) { server ->
                            val isSelected = server.id == uiState.settings.selectedServerId
                            val isConnected = isSelected && uiState.connectionState == ConnectionState.CONNECTED
                            ServerProfileCard(
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
                        item { Spacer(modifier = Modifier.height(14.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerProfileCard(
    server: ServerConfig,
    isSelected: Boolean,
    isConnected: Boolean,
    onSelect: () -> Unit,
    onTestDelay: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val indicatorColor by animateColorAsState(
        targetValue = when {
            isConnected -> EmeraldConnected
            isSelected -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        },
        label = "indicatorColor"
    )
    val cardContainer = when {
        isConnected -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
        isSelected -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.93f)
    }
    val cardBorder = BorderStroke(
        width = if (isSelected || isConnected) 1.25.dp else 1.dp,
        color = when {
            isConnected -> EmeraldConnected.copy(alpha = 0.65f)
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainer),
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
                .height(84.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(indicatorColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProtocolBadge(server.protocol)
                    Text(
                        text = server.remarks,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = server.maskedAddress(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "• ${server.transportSummary()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                LatencyBadge(latencyMs = server.latencyMs, onClick = onTestDelay)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "Share ${server.remarks}",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit ${server.remarks}",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete ${server.remarks}",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolBadge(protocol: ProtocolType) {
    val isDark = LocalIsDarkTheme.current
    val accent = when (protocol) {
        ProtocolType.AMIR -> PumpkinYellow
        ProtocolType.VLESS -> IndigoProtocol
        ProtocolType.VMESS -> BlueProtocol
        ProtocolType.TROJAN -> AmberLatency
        ProtocolType.WIREGUARD -> EmeraldConnected
        ProtocolType.HYSTERIA2 -> PurpleReality
        ProtocolType.SHADOWSOCKS -> TealProtocol
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val isAccentStrong = protocol == ProtocolType.AMIR
    val textColor = if (isAccentStrong) {
        if (isDark) PumpkinYellow else PumpkinYellowDeep
    } else {
        accent
    }
    Surface(
        color = accent.copy(alpha = if (isAccentStrong) 0.16f else 0.11f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.75.dp, accent.copy(alpha = if (isAccentStrong) 0.55f else 0.3f))
    ) {
        Text(
            text = protocol.displayName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
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
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.75.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                color = color
            )
        }
    }
}

@Composable
private fun CompactServerGridCard(
    server: ServerConfig,
    isSelected: Boolean,
    isConnected: Boolean,
    onSelect: () -> Unit,
    onTestDelay: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val indicatorColor by animateColorAsState(
        targetValue = when {
            isConnected -> EmeraldConnected
            isSelected -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        },
        label = "gridIndicatorColor"
    )
    val cardContainer = when {
        isConnected -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
        isSelected -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.93f)
    }
    val cardBorder = BorderStroke(
        width = if (isSelected || isConnected) 1.25.dp else 1.dp,
        color = when {
            isConnected -> EmeraldConnected.copy(alpha = 0.65f)
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    )
    var showCardMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainer),
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
                    .height(3.5.dp)
                    .background(indicatorColor)
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProtocolBadge(server.protocol)
                    LatencyBadge(latencyMs = server.latencyMs, onClick = onTestDelay)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = server.remarks,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
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
                            modifier = Modifier.size(34.dp)
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
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${state.elapsedMs} ms • ${state.url}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
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
private fun VpnBottomControlDock(
    connectionState: ConnectionState,
    activeRemarks: String,
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
    onToggleConnect: () -> Unit
) {
    val isConnected = connectionState == ConnectionState.CONNECTED
    val isBusy = connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.DISCONNECTING
    val fabContainer by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> EmeraldConnected
            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> AmberLatency
            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.primary
        },
        label = "fabColor"
    )
    val dockAccent by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> EmeraldConnected
            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> AmberLatency
            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "dockAccent"
    )

    val pulseTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isConnected || isBusy) dockAccent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        val statusGlowColor = if (isConnected) EmeraldConnected.copy(alpha = 0.12f) else Color.Transparent
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (isConnected) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(statusGlowColor, Color.Transparent),
                                center = Offset(size.width * 0.22f, size.height * 0.5f),
                                radius = size.width * 0.45f
                            ),
                            center = Offset(size.width * 0.22f, size.height * 0.5f),
                            radius = size.width * 0.45f
                        )
                    }
                }
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = isConnected, onClick = onTapStatusTest)
                    .padding(end = 10.dp, top = 4.dp, bottom = 4.dp)
                    .testTag("bottom_status_bar")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dotColor = when (connectionState) {
                        ConnectionState.CONNECTED -> EmeraldConnected
                        ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> AmberLatency
                        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(14.dp)
                    ) {
                        if (isConnected || isBusy) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(dotColor.copy(alpha = pulseAlpha * 0.4f))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val statusLabel = when (connectionState) {
                        ConnectionState.DISCONNECTED -> stringResource(R.string.status_not_connected)
                        ConnectionState.CONNECTING -> stringResource(R.string.status_connecting)
                        ConnectionState.DISCONNECTING -> stringResource(R.string.status_disconnecting)
                        ConnectionState.CONNECTED -> when {
                            isTestingDelay -> stringResource(R.string.checking_connection)
                            lastTestedDelay != null && lastTestedDelay >= 0 -> stringResource(R.string.connected_delay_ms, lastTestedDelay)
                            lastTestedDelay != null && lastTestedDelay < 0 -> stringResource(R.string.connected_timeout)
                            else -> stringResource(R.string.tap_to_test)
                        }
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                if (isConnected) {
                    val subStatus = buildString {
                        if (speedDisplayEnabled) {
                            append("↑ ${XrayVpnService.formatSpeed(uplinkBps)} (${XrayVpnService.formatBytes(totalUpload)})  ")
                            append("↓ ${XrayVpnService.formatSpeed(downlinkBps)} (${XrayVpnService.formatBytes(totalDownload)})")
                        } else {
                            append(activeRemarks.ifBlank { "PalaNG" })
                        }
                        if (activeSocksPort != null) {
                            append("  • :$activeSocksPort")
                        }
                    }
                    Text(
                        text = subStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = if (activeRemarks.isNotBlank()) stringResource(R.string.selected_server, activeRemarks) else stringResource(R.string.no_server_selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isConnected) {
                IconButton(
                    onClick = onTestConnectionInfo,
                    modifier = Modifier.testTag("connection_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = stringResource(R.string.menu_connection_info),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val fabContentColor = when (connectionState) {
                ConnectionState.CONNECTED -> Color.White
                ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> Color(0xFF211300)
                ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onPrimary
            }
            FloatingActionButton(
                onClick = onToggleConnect,
                shape = RoundedCornerShape(16.dp),
                containerColor = fabContainer,
                contentColor = fabContentColor,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp
                ),
                modifier = Modifier
                    .width(64.dp)
                    .height(52.dp)
                    .testTag("vpn_connect_fab")
            ) {
                when {
                    isBusy -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = fabContentColor
                    )
                    isConnected -> Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = "Disconnect VPN",
                        modifier = Modifier.size(26.dp)
                    )
                    else -> Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Connect VPN",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProfilesState(
    onImportClipboard: () -> Unit,
    onScanQr: () -> Unit,
    onManualAdd: () -> Unit
) {
    val glowColor = PumpkinYellow
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(116.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = 0.28f), Color.Transparent)
                        ),
                        radius = size.maxDimension * 0.72f
                    )
                }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = BrandCanvasDark,
                border = BorderStroke(1.5.dp, glowColor.copy(alpha = 0.68f)),
                shadowElevation = 6.dp
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_palang_logo),
                    contentDescription = "PalaNG logo",
                    modifier = Modifier
                        .size(92.dp)
                        .padding(8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.empty_profiles_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_profiles_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
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
