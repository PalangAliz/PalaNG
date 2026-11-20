package com.example.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AppSettings
import com.example.data.model.LanguageMode
import com.example.data.model.ThemeMode
import com.example.ui.MainViewModel

private enum class SettingsDialogChoice {
    LANGUAGE,
    THEME,
    BYPASS_LAN,
    VPN_INTERFACE_ADDR,
    LOG_LEVEL,
    OUTBOUND_DOMAIN_RESOLVE,
    MUX_QUIC_HANDLING,
    OBSERVATORY_MODE,
    LEAST_LOAD_HTTP_METHOD,
    VPN_MODE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onApplyReconnect: () -> Unit = {},
    onBack: () -> Unit
) {
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
    val pendingTunnelRestart by viewModel.pendingTunnelRestart.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editDialog by remember { mutableStateOf<SettingEditTarget?>(null) }
    var activeChoiceDialog by remember { mutableStateOf<SettingsDialogChoice?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }

    var uiExpanded by remember { mutableStateOf(true) }
    var vpnExpanded by remember { mutableStateOf(true) }
    var coreExpanded by remember { mutableStateOf(true) }
    var muxExpanded by remember { mutableStateOf(true) }
    var fragmentExpanded by remember { mutableStateOf(false) }
    var observatoryExpanded by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(true) }
    var modeExpanded by remember { mutableStateOf(true) }

    BackHandler(
        enabled = editDialog == null && activeChoiceDialog == null && !showHelpDialog
    ) {
        onBack()
    }

    editDialog?.let { target ->
        TextSettingDialog(
            title = target.title,
            initialValue = target.initialValue,
            inputMode = target.inputMode,
            isPassword = target.isPassword,
            hint = target.hint,
            onSave = { value ->
                target.onSave(value)
                editDialog = null
            },
            onDismiss = { editDialog = null }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(stringResource(R.string.pref_more_help_title)) },
            text = {
                Text(
                    text = stringResource(R.string.help_dialog_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    val themeLabels = mapOf(
        ThemeMode.LIGHT.name to stringResource(R.string.theme_light),
        ThemeMode.DARK.name to stringResource(R.string.theme_dark),
        ThemeMode.SYSTEM.name to stringResource(R.string.theme_system)
    )
    val languageLabels = mapOf(
        LanguageMode.SYSTEM.name to stringResource(R.string.lang_system),
        LanguageMode.EN.name to stringResource(R.string.lang_en),
        LanguageMode.FA.name to stringResource(R.string.lang_fa)
    )
    val outboundResolveLabels = mapOf(
        AppSettings.OUTBOUND_RESOLVE_AND_ADD to stringResource(R.string.outbound_resolve_and_add),
        AppSettings.OUTBOUND_AS_IS to "AsIs",
        AppSettings.OUTBOUND_USE_IP to "UseIP",
        AppSettings.OUTBOUND_USE_IPV4 to "UseIPv4",
        AppSettings.OUTBOUND_USE_IPV6 to "UseIPv6"
    )
    val observatoryModeLabels = mapOf(
        AppSettings.OBSERVATORY_NONE to stringResource(R.string.observatory_none),
        AppSettings.OBSERVATORY_LEAST_PING to stringResource(R.string.observatory_least_ping),
        AppSettings.OBSERVATORY_LEAST_LOAD to stringResource(R.string.observatory_least_load)
    )

    when (activeChoiceDialog) {
        SettingsDialogChoice.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = LanguageMode.entries.map { it.name to (languageLabels[it.name] ?: it.name) },
            selected = settings.languageMode.name,
            onSelect = { name ->
                viewModel.updateSettings { it.copy(languageMode = LanguageMode.valueOf(name)) }
                activeChoiceDialog = null
            },
            onDismiss = { activeChoiceDialog = null }
        )

        SettingsDialogChoice.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it.name to (themeLabels[it.name] ?: it.name) },
            selected = settings.themeMode.name,
            onSelect = { name ->
                viewModel.updateSettings { it.copy(themeMode = ThemeMode.valueOf(name)) }
                activeChoiceDialog = null
            },
            onDismiss = { activeChoiceDialog = null }
        )

        SettingsDialogChoice.BYPASS_LAN -> ChoiceDialog(
            title = stringResource(R.string.pref_bypass_lan_title),
            options = listOf(
                "true" to stringResource(R.string.pref_bypass_lan_yes),
                "false" to stringResource(R.string.pref_bypass_lan_no)
            ),
            selected = settings.bypassLan.toString(),
            onSelect = { value ->
                viewModel.updateSettings { it.copy(bypassLan = value == "true") }
                activeChoiceDialog = null
            },
            onDismiss = { activeChoiceDialog = null }
        )

        SettingsDialogChoice.VPN_INTERFACE_ADDR -> ChoiceDialog(
            title = stringResource(R.string.pref_vpn_interface_addr_title),
            options = AppSettings.VPN_ADDRESS_PRESETS.map { it.key to "${it.label} (${it.clientIp}/30)" },
            selected = settings.vpnInterfaceAddress,
            onSelect = { key ->
                viewModel.updateSettings { it.copy(vpnInterfaceAddress = key) }
                activeChoiceDialog = null
            },
            onDismiss = { activeChoiceDialog = null }
        )

        SettingsDialogChoice.LOG_LEVEL -> ChoiceDialog(
            title = stringResource(R.string.pref_log_level_title),
            options = listOf("debug", "info", "warning", "error", "none").map { it to it },
            selected = settings.logLevel,
            onSelect = { lvl ->
                viewModel.updateSettings { it.copy(logLevel = lvl) }
                activeChoiceDialog = null
            },
            onDismiss = { activeChoiceDialog = null }
        )

        SettingsDialogChoice.OUTBOUND_DOMAIN_RESOLVE -> ChoiceDialog(
            title = stringResource(R.string.pref_outbound_resolve_title),
            options = listOf(
                AppSettings.OUTBOUND_RESOLVE_AND_ADD,
                AppSettings.OUTBOUND_AS_IS,
                AppSettings.OUTBOUND_USE_IP,
                AppSettings.OUTBOUND_USE_IPV4,
                AppSettings.OUTBOUND_USE_IPV6
            ).map { it to (outboundResolveLabels[it] ?: it) },
            selected = settings.outboundDomainResolve,
            onSelect = { mode ->
                viewModel.updateSettings { it.copy(outboundDomainResolve = mode) }
                activeChoiceDialog = null
            },
            onDismiss = { activeChoiceDialog = null }
        )

        SettingsDialogChoice.MUX_QUIC_HANDLING -> ChoiceDialog(
            title = stringResource(R.string.pref_mux_quic_title),
            options = listOf("reject", "allow", "skip").map { it to it },
            selected = settings.muxXudpProxyUdp443,
            onSelect = { mode ->
                viewModel.updateSettings { it.copy(muxXudpProxyUdp443 = mode) }
                activeChoiceDialog = null
            },
            onDismiss = { activeChoiceDialog = null }
        )

        SettingsDialogChoice.OBSERVATORY_MODE -> ChoiceDialog(
            title = stringResource(R.string.pref_observatory_mode_title),
            options = listOf(
                AppSettings.OBSERVATORY_NONE,
                AppSettings.OBSERVATORY_LEAST_PING,
                AppSettings.OBSERVATORY_LEAST_LOAD
            ).map { it to (observatoryModeLabels[it] ?: it) },
            selected = settings.observatoryMode,
            onSelect = { mode ->
                viewModel.updateSettings { it.copy(observatoryMode = mode) }
                activeChoiceDialog = null
            },
            onDismiss = { activeChoiceDialog = null }
        )

        SettingsDialogChoice.LEAST_LOAD_HTTP_METHOD -> ChoiceDialog(
            title = stringResource(R.string.pref_least_load_method_title),
            options = listOf("HEAD", "GET").map { it to it },
            selected = settings.burstObservatoryHttpMethod,
            onSelect = { method ->
                viewModel.updateSettings { it.copy(burstObservatoryHttpMethod = method) }
                activeChoiceDialog = null
            },
            onDismiss = { activeChoiceDialog = null }
        )

        SettingsDialogChoice.VPN_MODE -> ChoiceDialog(
            title = stringResource(R.string.pref_mode_title),
            options = listOf(
                "true" to stringResource(R.string.mode_vpn),
                "false" to stringResource(R.string.mode_proxy_only)
            ),
            selected = settings.vpnMode.toString(),
            onSelect = { value ->
                viewModel.updateSettings { it.copy(vpnMode = value == "true") }
                activeChoiceDialog = null
            },
            onDismiss = { activeChoiceDialog = null }
        )

        null -> Unit
    }

    val effectiveLocalProxy = settings.effectiveLocalProxyEnabled()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.74f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = pendingTunnelRestart) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.pending_reconnect_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        TextButton(
                            onClick = onApplyReconnect,
                            modifier = Modifier.testTag("settings_apply_reconnect_button")
                        ) {
                            Text(
                                text = stringResource(R.string.action_apply_now),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. UI settings
                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_section_ui),
                        icon = Icons.Outlined.Palette,
                        expanded = uiExpanded,
                        onToggle = { uiExpanded = !uiExpanded },
                        tag = "section_ui"
                    ) {
                        SwitchRow(
                            title = stringResource(R.string.pref_speed_display_title),
                            subtitle = stringResource(R.string.pref_speed_display_sub),
                            checked = settings.speedDisplayEnabled,
                            testTag = "switch_speed_display",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(speedDisplayEnabled = v) } }
                        )
                        RowInsetDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_confirm_delete_title),
                            subtitle = stringResource(R.string.pref_confirm_delete_sub),
                            checked = settings.confirmDeleteConfig,
                            testTag = "switch_confirm_delete",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(confirmDeleteConfig = v) } }
                        )
                        RowInsetDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_two_column_title),
                            subtitle = stringResource(R.string.pref_two_column_sub),
                            checked = settings.twoColumnLayout,
                            testTag = "switch_two_column",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(twoColumnLayout = v) } }
                        )
                        RowInsetDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_show_all_tab_title),
                            subtitle = stringResource(R.string.pref_show_all_tab_sub),
                            checked = settings.showAllGroupTab,
                            testTag = "switch_show_all_tab",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(showAllGroupTab = v) } }
                        )
                        RowInsetDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_dynamic_color_title),
                            subtitle = stringResource(R.string.pref_dynamic_color_sub),
                            checked = settings.dynamicColorEnabled,
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            testTag = "switch_dynamic_color",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(dynamicColorEnabled = v) } }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.settings_language),
                            value = languageLabels[settings.languageMode.name] ?: settings.languageMode.name,
                            testTag = "row_language",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.LANGUAGE }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.settings_theme),
                            value = themeLabels[settings.themeMode.name] ?: settings.themeMode.name,
                            testTag = "row_theme",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.THEME }
                        )
                    }
                }

                // 2. VPN Settings
                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_section_vpn),
                        icon = Icons.Outlined.Shield,
                        expanded = vpnExpanded,
                        onToggle = { vpnExpanded = !vpnExpanded },
                        tag = "section_vpn"
                    ) {
                        SwitchRow(
                            title = stringResource(R.string.pref_enable_ipv6_title),
                            subtitle = stringResource(R.string.pref_enable_ipv6_sub),
                            checked = settings.enableIpv6,
                            enabled = settings.vpnMode,
                            testTag = "switch_enable_ipv6",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(enableIpv6 = v) } }
                        )
                        RowInsetDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_prefer_ipv6_title),
                            subtitle = stringResource(R.string.pref_prefer_ipv6_sub),
                            checked = settings.preferIpv6,
                            testTag = "switch_prefer_ipv6",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(preferIpv6 = v) } }
                        )
                        RowInsetDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_local_dns_title),
                            subtitle = stringResource(R.string.pref_local_dns_sub),
                            checked = settings.localDnsEnabled,
                            testTag = "switch_local_dns",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(localDnsEnabled = v) } }
                        )
                        RowInsetDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_fake_dns_title),
                            subtitle = stringResource(R.string.pref_fake_dns_sub),
                            checked = settings.fakeDnsEnabled,
                            testTag = "switch_fake_dns",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(fakeDnsEnabled = v) } }
                        )
                        RowInsetDivider()
                        val vpnDnsEnabled = settings.vpnMode && !settings.localDnsEnabled && !settings.fakeDnsEnabled
                        ValueRow(
                            title = stringResource(R.string.pref_vpn_dns_title),
                            value = if (settings.localDnsEnabled || settings.fakeDnsEnabled) {
                                "${settings.vpnDns} (${stringResource(R.string.pref_vpn_dns_disabled_note)})"
                            } else {
                                settings.vpnDns
                            },
                            enabled = vpnDnsEnabled,
                            testTag = "row_vpn_dns",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_vpn_dns_title),
                                    initialValue = settings.vpnDns,
                                    hint = "1.1.1.1,8.8.8.8"
                                ) { v ->
                                    if (v.isNotBlank()) viewModel.updateSettings { it.copy(vpnDns = v.trim()) }
                                }
                            }
                        )
                        RowInsetDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_append_http_proxy_title),
                            subtitle = stringResource(R.string.pref_append_http_proxy_sub),
                            checked = settings.appendHttpProxyToVpn,
                            enabled = settings.vpnMode && effectiveLocalProxy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                            testTag = "switch_append_http_proxy",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(appendHttpProxyToVpn = v) } }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_bypass_lan_title),
                            value = if (settings.bypassLan) {
                                stringResource(R.string.pref_bypass_lan_yes)
                            } else {
                                stringResource(R.string.pref_bypass_lan_no)
                            },
                            enabled = settings.vpnMode,
                            testTag = "row_bypass_lan",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.BYPASS_LAN }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_vpn_interface_addr_title),
                            value = settings.vpnInterfaceAddress,
                            enabled = settings.vpnMode,
                            testTag = "row_vpn_interface_addr",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.VPN_INTERFACE_ADDR }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_vpn_mtu_title),
                            value = settings.vpnMtu.toString(),
                            enabled = settings.vpnMode,
                            testTag = "row_vpn_mtu",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_vpn_mtu_title),
                                    initialValue = settings.vpnMtu.toString(),
                                    inputMode = InputMode.UNSIGNED_INT,
                                    hint = "1280 - 9000"
                                ) { v ->
                                    v.toIntOrNull()?.coerceIn(1280, 9000)?.let { mtu ->
                                        viewModel.updateSettings { it.copy(vpnMtu = mtu) }
                                    }
                                }
                            }
                        )
                    }
                }

                // 3. Core Settings
                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_section_core),
                        icon = Icons.Outlined.Dns,
                        expanded = coreExpanded,
                        onToggle = { coreExpanded = !coreExpanded },
                        tag = "section_core"
                    ) {
                        SwitchRow(
                            title = stringResource(R.string.pref_sniffing_title),
                            subtitle = stringResource(R.string.pref_sniffing_sub),
                            checked = settings.sniffingEnabled,
                            testTag = "switch_sniffing",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(sniffingEnabled = v) } }
                        )
                        RowInsetDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_route_only_title),
                            subtitle = stringResource(R.string.pref_route_only_sub),
                            checked = settings.routeOnly,
                            enabled = settings.sniffingEnabled && !settings.fakeDnsEnabled,
                            testTag = "switch_route_only",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(routeOnly = v) } }
                        )
                        RowInsetDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_local_proxy_title),
                            subtitle = stringResource(R.string.pref_local_proxy_sub),
                            checked = effectiveLocalProxy,
                            enabled = settings.vpnMode,
                            testTag = "switch_local_proxy",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(localProxyEnabled = v) } }
                        )
                        RowInsetDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_allow_lan_title),
                            subtitle = stringResource(R.string.pref_allow_lan_sub),
                            checked = settings.allowLan,
                            enabled = effectiveLocalProxy,
                            testTag = "switch_allow_lan",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(allowLan = v) } }
                        )
                        RowInsetDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_dynamic_port_title),
                            subtitle = stringResource(R.string.pref_dynamic_port_sub),
                            checked = settings.dynamicProxyPortEnabled,
                            enabled = effectiveLocalProxy,
                            testTag = "switch_dynamic_port",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(dynamicProxyPortEnabled = v) } }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_local_socks_port_title),
                            value = settings.socksPort.toString(),
                            enabled = effectiveLocalProxy && !settings.dynamicProxyPortEnabled,
                            testTag = "row_socks_port",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_local_socks_port_title),
                                    initialValue = settings.socksPort.toString(),
                                    inputMode = InputMode.UNSIGNED_INT,
                                    hint = "1024 - 65535"
                                ) { v ->
                                    v.toIntOrNull()?.coerceIn(1024, 65535)?.let { p ->
                                        viewModel.updateSettings { it.copy(socksPort = p) }
                                    }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_local_http_port_title),
                            value = settings.httpPort.toString(),
                            enabled = effectiveLocalProxy && !settings.dynamicProxyPortEnabled,
                            testTag = "row_http_port",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_local_http_port_title),
                                    initialValue = settings.httpPort.toString(),
                                    inputMode = InputMode.UNSIGNED_INT,
                                    hint = "1024 - 65535"
                                ) { v ->
                                    v.toIntOrNull()?.coerceIn(1024, 65535)?.let { p ->
                                        viewModel.updateSettings { it.copy(httpPort = p) }
                                    }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_proxy_user_title),
                            value = settings.localProxyUsername.ifBlank { "—" },
                            enabled = effectiveLocalProxy,
                            testTag = "row_proxy_user",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_proxy_user_title),
                                    initialValue = settings.localProxyUsername
                                ) { v ->
                                    viewModel.updateSettings { it.copy(localProxyUsername = v.trim()) }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_proxy_pass_title),
                            value = if (settings.localProxyPassword.isBlank()) "—" else "••••••••",
                            enabled = effectiveLocalProxy,
                            testTag = "row_proxy_pass",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_proxy_pass_title),
                                    initialValue = settings.localProxyPassword,
                                    isPassword = true
                                ) { v ->
                                    viewModel.updateSettings { it.copy(localProxyPassword = v) }
                                }
                            }
                        )
                        RowInsetDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_socks5_udp_title),
                            subtitle = stringResource(R.string.pref_socks5_udp_sub),
                            checked = settings.socks5UdpEnabled,
                            enabled = effectiveLocalProxy,
                            testTag = "switch_socks5_udp",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(socks5UdpEnabled = v) } }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_remote_dns_title),
                            value = settings.remoteDns,
                            testTag = "row_remote_dns",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_remote_dns_title),
                                    initialValue = settings.remoteDns,
                                    hint = "https://1.1.1.1/dns-query"
                                ) { v ->
                                    if (v.isNotBlank()) viewModel.updateSettings { it.copy(remoteDns = v.trim()) }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_direct_dns_title),
                            value = settings.domesticDns,
                            testTag = "row_direct_dns",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_direct_dns_title),
                                    initialValue = settings.domesticDns,
                                    hint = "223.5.5.5"
                                ) { v ->
                                    if (v.isNotBlank()) viewModel.updateSettings { it.copy(domesticDns = v.trim()) }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_dns_hosts_title),
                            value = settings.dnsHosts.ifBlank { "—" },
                            testTag = "row_dns_hosts",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_dns_hosts_title),
                                    initialValue = settings.dnsHosts,
                                    hint = "example.com:1.2.3.4,domain:foo.org:8.8.8.8"
                                ) { v ->
                                    viewModel.updateSettings { it.copy(dnsHosts = v.trim()) }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_log_level_title),
                            value = settings.logLevel,
                            testTag = "row_log_level",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.LOG_LEVEL }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_outbound_resolve_title),
                            value = outboundResolveLabels[settings.outboundDomainResolve] ?: settings.outboundDomainResolve,
                            testTag = "row_outbound_resolve",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.OUTBOUND_DOMAIN_RESOLVE }
                        )
                    }
                }

                // 4. Mux Settings
                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_section_mux),
                        icon = Icons.Outlined.Speed,
                        expanded = muxExpanded,
                        onToggle = { muxExpanded = !muxExpanded },
                        tag = "section_mux"
                    ) {
                        SwitchRow(
                            title = stringResource(R.string.pref_mux_title),
                            subtitle = stringResource(R.string.pref_mux_sub),
                            checked = settings.muxEnabled,
                            testTag = "switch_mux",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(muxEnabled = v) } }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_mux_tcp_title),
                            value = settings.muxConcurrency.toString(),
                            enabled = settings.muxEnabled,
                            testTag = "row_mux_concurrency",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_mux_tcp_title),
                                    initialValue = settings.muxConcurrency.toString(),
                                    inputMode = InputMode.SIGNED_INT,
                                    hint = "-1 to 1024"
                                ) { v ->
                                    v.toIntOrNull()?.coerceIn(-1, 1024)?.let { c ->
                                        viewModel.updateSettings { it.copy(muxConcurrency = c) }
                                    }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_mux_xudp_title),
                            value = settings.muxXudpConcurrency.toString(),
                            enabled = settings.muxEnabled,
                            testTag = "row_mux_xudp",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_mux_xudp_title),
                                    initialValue = settings.muxXudpConcurrency.toString(),
                                    inputMode = InputMode.SIGNED_INT,
                                    hint = "-1 to 1024"
                                ) { v ->
                                    v.toIntOrNull()?.coerceIn(-1, 1024)?.let { c ->
                                        viewModel.updateSettings { it.copy(muxXudpConcurrency = c) }
                                    }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_mux_quic_title),
                            value = settings.muxXudpProxyUdp443,
                            enabled = settings.muxEnabled,
                            testTag = "row_mux_quic",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.MUX_QUIC_HANDLING }
                        )
                    }
                }

                // 5. Fragment Settings
                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_section_fragment),
                        icon = Icons.Outlined.Bolt,
                        expanded = fragmentExpanded,
                        onToggle = { fragmentExpanded = !fragmentExpanded },
                        tag = "section_fragment"
                    ) {
                        SwitchRow(
                            title = stringResource(R.string.pref_fragment_title),
                            subtitle = stringResource(R.string.pref_fragment_sub),
                            checked = settings.fragmentEnabled,
                            testTag = "switch_fragment",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(fragmentEnabled = v) } }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_fragment_packets_title),
                            value = settings.fragmentPackets,
                            enabled = settings.fragmentEnabled,
                            testTag = "row_fragment_packets",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_fragment_packets_title),
                                    initialValue = settings.fragmentPackets,
                                    hint = "tlshello or 1-3"
                                ) { v ->
                                    if (v.isNotBlank()) viewModel.updateSettings { it.copy(fragmentPackets = v.trim()) }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_fragment_length_title),
                            value = settings.fragmentLength,
                            enabled = settings.fragmentEnabled,
                            testTag = "row_fragment_length",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_fragment_length_title),
                                    initialValue = settings.fragmentLength,
                                    hint = "50-100 or 100-200"
                                ) { v ->
                                    if (v.isNotBlank()) viewModel.updateSettings { it.copy(fragmentLength = v.trim()) }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_fragment_interval_title),
                            value = settings.fragmentInterval,
                            enabled = settings.fragmentEnabled,
                            testTag = "row_fragment_interval",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_fragment_interval_title),
                                    initialValue = settings.fragmentInterval,
                                    hint = "10-20"
                                ) { v ->
                                    if (v.isNotBlank()) viewModel.updateSettings { it.copy(fragmentInterval = v.trim()) }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_fragment_max_split_title),
                            value = settings.fragmentMaxSplit.ifBlank { "10" },
                            enabled = settings.fragmentEnabled,
                            testTag = "row_fragment_max_split",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_fragment_max_split_title),
                                    initialValue = settings.fragmentMaxSplit,
                                    hint = "10 or 5-10"
                                ) { v ->
                                    viewModel.updateSettings { it.copy(fragmentMaxSplit = v.trim()) }
                                }
                            }
                        )
                    }
                }

                // 6. Observatory Settings
                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_section_observatory),
                        icon = Icons.Outlined.Radar,
                        expanded = observatoryExpanded,
                        onToggle = { observatoryExpanded = !observatoryExpanded },
                        tag = "section_observatory"
                    ) {
                        ValueRow(
                            title = stringResource(R.string.pref_observatory_mode_title),
                            value = observatoryModeLabels[settings.observatoryMode] ?: settings.observatoryMode,
                            testTag = "row_observatory_mode",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.OBSERVATORY_MODE }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_least_ping_interval_title),
                            value = settings.observatoryInterval,
                            testTag = "row_least_ping_interval",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_least_ping_interval_title),
                                    initialValue = settings.observatoryInterval,
                                    hint = "3m"
                                ) { v ->
                                    if (v.isNotBlank()) viewModel.updateSettings { it.copy(observatoryInterval = v.trim()) }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_least_load_interval_title),
                            value = settings.burstObservatoryInterval,
                            testTag = "row_least_load_interval",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_least_load_interval_title),
                                    initialValue = settings.burstObservatoryInterval,
                                    hint = "5m"
                                ) { v ->
                                    if (v.isNotBlank()) viewModel.updateSettings { it.copy(burstObservatoryInterval = v.trim()) }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_least_load_method_title),
                            value = settings.burstObservatoryHttpMethod,
                            testTag = "row_least_load_method",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.LEAST_LOAD_HTTP_METHOD }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_least_load_sampling_title),
                            value = settings.burstObservatorySampling.toString(),
                            testTag = "row_least_load_sampling",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_least_load_sampling_title),
                                    initialValue = settings.burstObservatorySampling.toString(),
                                    inputMode = InputMode.UNSIGNED_INT,
                                    hint = "1 - 20"
                                ) { v ->
                                    v.toIntOrNull()?.coerceIn(1, 20)?.let { s ->
                                        viewModel.updateSettings { it.copy(burstObservatorySampling = s) }
                                    }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_least_load_timeout_title),
                            value = settings.burstObservatoryTimeout,
                            testTag = "row_least_load_timeout",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_least_load_timeout_title),
                                    initialValue = settings.burstObservatoryTimeout,
                                    hint = "30s"
                                ) { v ->
                                    if (v.isNotBlank()) viewModel.updateSettings { it.copy(burstObservatoryTimeout = v.trim()) }
                                }
                            }
                        )
                    }
                }

                // 7. Advanced Settings
                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_section_advanced),
                        icon = Icons.Outlined.Tune,
                        expanded = advancedExpanded,
                        onToggle = { advancedExpanded = !advancedExpanded },
                        tag = "section_advanced"
                    ) {
                        SwitchRow(
                            title = stringResource(R.string.pref_auto_boot_title),
                            subtitle = stringResource(R.string.pref_auto_boot_sub),
                            checked = settings.autoStartOnBoot,
                            testTag = "switch_auto_boot",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoStartOnBoot = v) } }
                        )
                        RowInsetDivider()
                        ActionRow(
                            title = stringResource(R.string.pref_system_vpn_title),
                            subtitle = stringResource(R.string.pref_system_vpn_sub),
                            testTag = "row_system_vpn_settings",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }.onFailure {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_delay_url_title),
                            value = settings.delayTestUrl,
                            testTag = "row_delay_test_url",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_delay_url_title),
                                    initialValue = settings.delayTestUrl,
                                    hint = "https://www.gstatic.com/generate_204"
                                ) { v ->
                                    if (v.isNotBlank()) viewModel.updateSettings { it.copy(delayTestUrl = v.trim()) }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_concurrent_delay_title),
                            value = settings.concurrentDelayTests.toString(),
                            testTag = "row_concurrent_delay",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_concurrent_delay_title),
                                    initialValue = settings.concurrentDelayTests.toString(),
                                    inputMode = InputMode.UNSIGNED_INT,
                                    hint = "1 - 64"
                                ) { v ->
                                    v.toIntOrNull()?.coerceIn(1, 64)?.let { count ->
                                        viewModel.updateSettings { it.copy(concurrentDelayTests = count) }
                                    }
                                }
                            }
                        )
                        RowInsetDivider()
                        ValueRow(
                            title = stringResource(R.string.pref_conn_info_url_title),
                            value = settings.connInfoTestUrl,
                            testTag = "row_conn_info_url",
                            onClick = {
                                editDialog = SettingEditTarget(
                                    title = context.getString(R.string.pref_conn_info_url_title),
                                    initialValue = settings.connInfoTestUrl,
                                    hint = "https://api.ip.sb/geoip"
                                ) { v ->
                                    if (v.isNotBlank()) viewModel.updateSettings { it.copy(connInfoTestUrl = v.trim()) }
                                }
                            }
                        )
                    }
                }

                // 8. Mode Settings
                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_section_mode),
                        icon = Icons.Outlined.Widgets,
                        expanded = modeExpanded,
                        onToggle = { modeExpanded = !modeExpanded },
                        tag = "section_mode"
                    ) {
                        ValueRow(
                            title = stringResource(R.string.pref_mode_title),
                            value = if (settings.vpnMode) {
                                stringResource(R.string.mode_vpn)
                            } else {
                                stringResource(R.string.mode_proxy_only)
                            },
                            testTag = "row_vpn_mode",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.VPN_MODE }
                        )
                        RowInsetDivider()
                        ActionRow(
                            title = stringResource(R.string.pref_more_help_title),
                            subtitle = stringResource(R.string.pref_more_help_sub),
                            testTag = "row_more_help",
                            onClick = { showHelpDialog = true }
                        )
                    }
                }
            }
        }
    }
}

private enum class InputMode {
    TEXT,
    UNSIGNED_INT,
    SIGNED_INT
}

private data class SettingEditTarget(
    val title: String,
    val initialValue: String,
    val inputMode: InputMode = InputMode.TEXT,
    val isPassword: Boolean = false,
    val hint: String = "",
    val onSave: (String) -> Unit
)

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    tag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
        border = BorderStroke(0.85.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag(tag),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    content()
                }
            }
        }
    }
}

@Composable
private fun RowInsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    testTag: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val modifier = Modifier
        .fillMaxWidth()
        .minimumInteractiveComponentSize()
        .alpha(if (enabled) 1f else 0.45f)
        .clickable(enabled = enabled) { onCheckedChange(!checked) }
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ValueRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val modifier = Modifier
        .fillMaxWidth()
        .minimumInteractiveComponentSize()
        .alpha(if (enabled) 1f else 0.45f)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }

    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val modifier = Modifier
        .fillMaxWidth()
        .minimumInteractiveComponentSize()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }

    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TextSettingDialog(
    title: String,
    initialValue: String,
    inputMode: InputMode,
    isPassword: Boolean,
    hint: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { next ->
                    value = when (inputMode) {
                        InputMode.UNSIGNED_INT -> next.filter(Char::isDigit)
                        InputMode.SIGNED_INT -> buildString {
                            next.forEachIndexed { idx, ch ->
                                if (ch.isDigit() || (ch == '-' && idx == 0)) append(ch)
                            }
                        }
                        InputMode.TEXT -> next
                    }
                },
                placeholder = if (hint.isNotEmpty()) {
                    { Text(hint) }
                } else {
                    null
                },
                singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = when (inputMode) {
                    InputMode.UNSIGNED_INT -> KeyboardOptions(keyboardType = KeyboardType.Number)
                    InputMode.SIGNED_INT -> KeyboardOptions(keyboardType = KeyboardType.Text)
                    InputMode.TEXT -> KeyboardOptions.Default
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(value) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable { onSelect(key) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = key == selected, onClick = { onSelect(key) })
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}
