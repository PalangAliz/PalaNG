package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldConnected
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.palang_minimal_icon_1795003260717),
                                contentDescription = "PalaNG logo",
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.brand_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.4.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_profiles)) },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Outlined.RssFeed, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_subscriptions)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateSubscriptions()
                    },
                    icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .testTag("drawer_subscriptions")
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_routing)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateRouting()
                    },
                    icon = { Icon(Icons.Outlined.AltRoute, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .testTag("drawer_routing")
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_per_app)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigatePerApp()
                    },
                    icon = { Icon(Icons.Outlined.Apps, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .testTag("drawer_per_app")
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_settings)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateSettings()
                    },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .testTag("drawer_settings")
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_logs)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateLogs()
                    },
                    icon = { Icon(Icons.AutoMirrored.Outlined.Article, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .testTag("drawer_logs")
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_assets)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateAssets()
                    },
                    icon = { Icon(Icons.Outlined.Storage, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .testTag("drawer_assets")
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_about)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateAbout()
                    },
                    icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .testTag("drawer_about")
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
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
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = stringResource(R.string.brand_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
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
                                    ProtocolType.VLESS to R.string.action_manual_vless,
                                    ProtocolType.VMESS to R.string.action_manual_vmess,
                                    ProtocolType.TROJAN to R.string.action_manual_trojan,
                                    ProtocolType.SHADOWSOCKS to R.string.action_manual_ss,
                                    ProtocolType.SOCKS to R.string.action_manual_socks,
                                    ProtocolType.WIREGUARD to R.string.action_manual_wg,
                                    ProtocolType.HYSTERIA2 to R.string.action_manual_hy2,
                                    ProtocolType.AMIR to R.string.action_manual_amir
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

                // Subscription Group Tabs (like v2rayNG group bar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.settings.showAllGroupTab) {
                        FilterChip(
                            selected = uiState.settings.selectedGroupId == AppSettings.GROUP_ALL,
                            onClick = { viewModel.selectGroup(AppSettings.GROUP_ALL) },
                            label = { Text("${stringResource(R.string.group_all)} (${uiState.servers.size})") },
                            modifier = Modifier.testTag("group_chip_all")
                        )
                    }
                    val defaultCount = uiState.servers.count { it.subscriptionId.isEmpty() }
                    FilterChip(
                        selected = uiState.settings.selectedGroupId == AppSettings.GROUP_DEFAULT,
                        onClick = { viewModel.selectGroup(AppSettings.GROUP_DEFAULT) },
                        label = { Text("${stringResource(R.string.group_default)} ($defaultCount)") },
                        modifier = Modifier.testTag("group_chip_default")
                    )
                    uiState.subscriptions.forEach { sub ->
                        val count = uiState.servers.count { it.subscriptionId == sub.id }
                        FilterChip(
                            selected = uiState.settings.selectedGroupId == sub.id,
                            onClick = { viewModel.selectGroup(sub.id) },
                            label = { Text("${sub.remarks} ($count)") }
                        )
                    }
                }

                // Batch Delay / Subscription Update progress banner
                val progress = uiState.batchProgress
                if (uiState.isTestingBatch && progress != null) {
                    val fraction = if (progress.second > 0) progress.first.toFloat() / progress.second else 0f
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
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
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = { viewModel.testAllVisibleDelay() }) {
                                    Text("Stop")
                                }
                            }
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth()
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        item(span = { GridItemSpan(2) }) { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        item { Spacer(modifier = Modifier.height(12.dp)) }
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
    val cardContainer = if (isSelected) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val cardBorder = BorderStroke(
        width = 1.dp,
        color = when {
            isConnected -> EmeraldConnected.copy(alpha = 0.5f)
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    )

    Card(
        shape = RoundedCornerShape(10.dp),
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
                .height(78.dp),
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
                    .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(end = 6.dp)
            ) {
                LatencyBadge(latencyMs = server.latencyMs, onClick = onTestDelay)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(38.dp)
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
                        modifier = Modifier.size(38.dp)
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
                        modifier = Modifier.size(38.dp)
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
    val isAmir = protocol == ProtocolType.AMIR
    val bg = if (isAmir) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = if (isAmir) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(
            width = 0.75.dp,
            color = if (isAmir) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            text = protocol.displayName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold,
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
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.75.dp, color.copy(alpha = 0.25f)),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
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
    val cardContainer = if (isSelected) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val cardBorder = BorderStroke(
        width = 1.dp,
        color = when {
            isConnected -> EmeraldConnected.copy(alpha = 0.5f)
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    )
    var showCardMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
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
                    .height(3.dp)
                    .background(indicatorColor)
            )
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProtocolBadge(server.protocol)
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
                            modifier = Modifier.size(32.dp)
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

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = isConnected, onClick = onTapStatusTest)
                        .padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
                        .testTag("bottom_status_bar")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connectionState) {
                                        ConnectionState.CONNECTED -> EmeraldConnected
                                        ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> AmberLatency
                                        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
                                    }
                                )
                        )
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
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isConnected) {
                        val subStatus = buildString {
                            if (speedDisplayEnabled) {
                                append("↑ ${XrayVpnService.formatSpeed(uplinkBps)} (${XrayVpnService.formatBytes(totalUpload)})  ")
                                append("↓ ${XrayVpnService.formatSpeed(downlinkBps)} (${XrayVpnService.formatBytes(totalDownload)})")
                            } else {
                                append(activeRemarks.ifBlank { "PalaNG" })
                            }
                            if (activeSocksPort != null) {
                                append("  •  :$activeSocksPort")
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

                FloatingActionButton(
                    onClick = onToggleConnect,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = fabContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 1.dp,
                        pressedElevation = 2.dp
                    ),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("vpn_connect_fab")
                ) {
                    when {
                        isBusy -> CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary
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
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.palang_minimal_icon_1795003260717),
            contentDescription = "PalaNG logo",
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(18.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_profiles_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_profiles_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onImportClipboard, modifier = Modifier.testTag("empty_import_clipboard")) {
                Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_clipboard))
            }
            TextButton(onClick = onScanQr, modifier = Modifier.testTag("empty_scan_qr")) {
                Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_scan_qr))
            }
            TextButton(onClick = onManualAdd, modifier = Modifier.testTag("empty_add_manual")) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_manual))
            }
        }
    }
}
