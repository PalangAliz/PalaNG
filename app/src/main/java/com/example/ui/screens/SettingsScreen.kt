package com.example.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
    onBack: () -> Unit
) {
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editDialog by remember { mutableStateOf<SettingEditTarget?>(null) }
    var activeChoiceDialog by remember { mutableStateOf<SettingsDialogChoice?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Collapsible section states (all expanded by default or cleanly toggleable)
    var uiExpanded by remember { mutableStateOf(true) }
    var vpnExpanded by remember { mutableStateOf(true) }
    var coreExpanded by remember { mutableStateOf(true) }
    var muxExpanded by remember { mutableStateOf(true) }
    var fragmentExpanded by remember { mutableStateOf(false) }
    var observatoryExpanded by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(true) }
    var modeExpanded by remember { mutableStateOf(true) }

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

    val forcedByAmir = settings.preAmirLocalDnsEnabled != null || settings.preAmirFakeDnsEnabled != null
    val effectiveLocalProxy = settings.effectiveLocalProxyEnabled()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(stringResource(R.string.settings_title)) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            // 1. UI settings
            item {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.settings_section_ui),
                    expanded = uiExpanded,
                    onToggle = { uiExpanded = !uiExpanded },
                    tag = "section_ui"
                )
            }
            item {
                AnimatedVisibility(visible = uiExpanded) {
                    Column {
                        SwitchRow(
                            title = stringResource(R.string.pref_speed_display_title),
                            subtitle = stringResource(R.string.pref_speed_display_sub),
                            checked = settings.speedDisplayEnabled,
                            testTag = "switch_speed_display",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(speedDisplayEnabled = v) } }
                        )
                        SwitchRow(
                            title = stringResource(R.string.pref_confirm_delete_title),
                            subtitle = stringResource(R.string.pref_confirm_delete_sub),
                            checked = settings.confirmDeleteConfig,
                            testTag = "switch_confirm_delete",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(confirmDeleteConfig = v) } }
                        )
                        SwitchRow(
                            title = stringResource(R.string.pref_two_column_title),
                            subtitle = stringResource(R.string.pref_two_column_sub),
                            checked = settings.twoColumnLayout,
                            testTag = "switch_two_column",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(twoColumnLayout = v) } }
                        )
                        SwitchRow(
                            title = stringResource(R.string.pref_show_all_tab_title),
                            subtitle = stringResource(R.string.pref_show_all_tab_sub),
                            checked = settings.showAllGroupTab,
                            testTag = "switch_show_all_tab",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(showAllGroupTab = v) } }
                        )
                        SwitchRow(
                            title = stringResource(R.string.pref_dynamic_color_title),
                            subtitle = stringResource(R.string.pref_dynamic_color_sub),
                            checked = settings.dynamicColorEnabled,
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            testTag = "switch_dynamic_color",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(dynamicColorEnabled = v) } }
                        )
                        ValueRow(
                            title = stringResource(R.string.settings_language),
                            value = languageLabels[settings.languageMode.name] ?: settings.languageMode.name,
                            testTag = "row_language",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.LANGUAGE }
                        )
                        ValueRow(
                            title = stringResource(R.string.settings_theme),
                            value = themeLabels[settings.themeMode.name] ?: settings.themeMode.name,
                            testTag = "row_theme",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.THEME }
                        )
                    }
                }
            }

            // 2. VPN Settings
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp)) }
            item {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.settings_section_vpn),
                    expanded = vpnExpanded,
                    onToggle = { vpnExpanded = !vpnExpanded },
                    tag = "section_vpn"
                )
            }
            item {
                AnimatedVisibility(visible = vpnExpanded) {
                    Column {
                        SwitchRow(
                            title = stringResource(R.string.pref_enable_ipv6_title),
                            subtitle = stringResource(R.string.pref_enable_ipv6_sub),
                            checked = settings.enableIpv6,
                            enabled = settings.vpnMode,
                            testTag = "switch_enable_ipv6",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(enableIpv6 = v) } }
                        )
                        SwitchRow(
                            title = stringResource(R.string.pref_prefer_ipv6_title),
                            subtitle = stringResource(R.string.pref_prefer_ipv6_sub),
                            checked = settings.preferIpv6,
                            testTag = "switch_prefer_ipv6",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(preferIpv6 = v) } }
                        )
                        SwitchRow(
                            title = stringResource(R.string.pref_local_dns_title),
                            subtitle = if (forcedByAmir) {
                                stringResource(R.string.pref_forced_amir_dns)
                            } else {
                                stringResource(R.string.pref_local_dns_sub)
                            },
                            checked = settings.localDnsEnabled,
                            enabled = !forcedByAmir,
                            testTag = "switch_local_dns",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(localDnsEnabled = v) } }
                        )
                        SwitchRow(
                            title = stringResource(R.string.pref_fake_dns_title),
                            subtitle = if (forcedByAmir) {
                                stringResource(R.string.pref_forced_amir_dns)
                            } else {
                                stringResource(R.string.pref_fake_dns_sub)
                            },
                            checked = settings.fakeDnsEnabled,
                            enabled = !forcedByAmir,
                            testTag = "switch_fake_dns",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(fakeDnsEnabled = v) } }
                        )
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
                        SwitchRow(
                            title = stringResource(R.string.pref_append_http_proxy_title),
                            subtitle = stringResource(R.string.pref_append_http_proxy_sub),
                            checked = settings.appendHttpProxyToVpn,
                            enabled = settings.vpnMode && effectiveLocalProxy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                            testTag = "switch_append_http_proxy",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(appendHttpProxyToVpn = v) } }
                        )
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
                        ValueRow(
                            title = stringResource(R.string.pref_vpn_interface_addr_title),
                            value = settings.vpnInterfaceAddress,
                            enabled = settings.vpnMode,
                            testTag = "row_vpn_interface_addr",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.VPN_INTERFACE_ADDR }
                        )
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
            }

            // 3. Core Settings
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp)) }
            item {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.settings_section_core),
                    expanded = coreExpanded,
                    onToggle = { coreExpanded = !coreExpanded },
                    tag = "section_core"
                )
            }
            item {
                AnimatedVisibility(visible = coreExpanded) {
                    Column {
                        SwitchRow(
                            title = stringResource(R.string.pref_sniffing_title),
                            subtitle = stringResource(R.string.pref_sniffing_sub),
                            checked = settings.sniffingEnabled,
                            testTag = "switch_sniffing",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(sniffingEnabled = v) } }
                        )
                        SwitchRow(
                            title = stringResource(R.string.pref_route_only_title),
                            subtitle = stringResource(R.string.pref_route_only_sub),
                            checked = settings.routeOnly,
                            enabled = settings.sniffingEnabled && !settings.fakeDnsEnabled,
                            testTag = "switch_route_only",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(routeOnly = v) } }
                        )
                        SwitchRow(
                            title = stringResource(R.string.pref_local_proxy_title),
                            subtitle = stringResource(R.string.pref_local_proxy_sub),
                            checked = effectiveLocalProxy,
                            enabled = settings.vpnMode,
                            testTag = "switch_local_proxy",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(localProxyEnabled = v) } }
                        )
                        SwitchRow(
                            title = stringResource(R.string.pref_allow_lan_title),
                            subtitle = stringResource(R.string.pref_allow_lan_sub),
                            checked = settings.allowLan,
                            enabled = effectiveLocalProxy,
                            testTag = "switch_allow_lan",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(allowLan = v) } }
                        )
                        SwitchRow(
                            title = stringResource(R.string.pref_dynamic_port_title),
                            subtitle = stringResource(R.string.pref_dynamic_port_sub),
                            checked = settings.dynamicProxyPortEnabled,
                            enabled = effectiveLocalProxy,
                            testTag = "switch_dynamic_port",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(dynamicProxyPortEnabled = v) } }
                        )
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
                        SwitchRow(
                            title = stringResource(R.string.pref_socks5_udp_title),
                            subtitle = stringResource(R.string.pref_socks5_udp_sub),
                            checked = settings.socks5UdpEnabled,
                            enabled = effectiveLocalProxy,
                            testTag = "switch_socks5_udp",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(socks5UdpEnabled = v) } }
                        )
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
                        ValueRow(
                            title = stringResource(R.string.pref_log_level_title),
                            value = settings.logLevel,
                            testTag = "row_log_level",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.LOG_LEVEL }
                        )
                        ValueRow(
                            title = stringResource(R.string.pref_outbound_resolve_title),
                            value = outboundResolveLabels[settings.outboundDomainResolve] ?: settings.outboundDomainResolve,
                            testTag = "row_outbound_resolve",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.OUTBOUND_DOMAIN_RESOLVE }
                        )
                    }
                }
            }

            // 4. Mux Settings
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp)) }
            item {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.settings_section_mux),
                    expanded = muxExpanded,
                    onToggle = { muxExpanded = !muxExpanded },
                    tag = "section_mux"
                )
            }
            item {
                AnimatedVisibility(visible = muxExpanded) {
                    Column {
                        SwitchRow(
                            title = stringResource(R.string.pref_mux_title),
                            subtitle = stringResource(R.string.pref_mux_sub),
                            checked = settings.muxEnabled,
                            testTag = "switch_mux",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(muxEnabled = v) } }
                        )
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
                        ValueRow(
                            title = stringResource(R.string.pref_mux_quic_title),
                            value = settings.muxXudpProxyUdp443,
                            enabled = settings.muxEnabled,
                            testTag = "row_mux_quic",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.MUX_QUIC_HANDLING }
                        )
                    }
                }
            }

            // 5. Fragment Settings
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp)) }
            item {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.settings_section_fragment),
                    expanded = fragmentExpanded,
                    onToggle = { fragmentExpanded = !fragmentExpanded },
                    tag = "section_fragment"
                )
            }
            item {
                AnimatedVisibility(visible = fragmentExpanded) {
                    Column {
                        SwitchRow(
                            title = stringResource(R.string.pref_fragment_title),
                            subtitle = stringResource(R.string.pref_fragment_sub),
                            checked = settings.fragmentEnabled,
                            testTag = "switch_fragment",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(fragmentEnabled = v) } }
                        )
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
            }

            // 6. Observatory Settings
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp)) }
            item {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.settings_section_observatory),
                    expanded = observatoryExpanded,
                    onToggle = { observatoryExpanded = !observatoryExpanded },
                    tag = "section_observatory"
                )
            }
            item {
                AnimatedVisibility(visible = observatoryExpanded) {
                    Column {
                        ValueRow(
                            title = stringResource(R.string.pref_observatory_mode_title),
                            value = observatoryModeLabels[settings.observatoryMode] ?: settings.observatoryMode,
                            testTag = "row_observatory_mode",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.OBSERVATORY_MODE }
                        )
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
                        ValueRow(
                            title = stringResource(R.string.pref_least_load_method_title),
                            value = settings.burstObservatoryHttpMethod,
                            testTag = "row_least_load_method",
                            onClick = { activeChoiceDialog = SettingsDialogChoice.LEAST_LOAD_HTTP_METHOD }
                        )
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
            }

            // 7. Advanced Settings
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp)) }
            item {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.settings_section_advanced),
                    expanded = advancedExpanded,
                    onToggle = { advancedExpanded = !advancedExpanded },
                    tag = "section_advanced"
                )
            }
            item {
                AnimatedVisibility(visible = advancedExpanded) {
                    Column {
                        SwitchRow(
                            title = stringResource(R.string.pref_auto_boot_title),
                            subtitle = stringResource(R.string.pref_auto_boot_sub),
                            checked = settings.autoStartOnBoot,
                            testTag = "switch_auto_boot",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoStartOnBoot = v) } }
                        )
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
            }

            // 8. Mode Settings
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp)) }
            item {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.settings_section_mode),
                    expanded = modeExpanded,
                    onToggle = { modeExpanded = !modeExpanded },
                    tag = "section_mode"
                )
            }
            item {
                AnimatedVisibility(visible = modeExpanded) {
                    Column {
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
private fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            tint = MaterialTheme.colorScheme.primary
        )
    }
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
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
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
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(2.dp))
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
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
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
