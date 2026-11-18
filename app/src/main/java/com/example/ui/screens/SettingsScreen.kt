package com.example.ui.screens

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.LanguageMode
import com.example.data.model.ThemeMode
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()

    var editDialog by remember { mutableStateOf<SettingEditTarget?>(null) }
    var showLogLevelDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    editDialog?.let { target ->
        TextSettingDialog(
            title = target.title,
            initialValue = target.initialValue,
            isNumeric = target.isNumeric,
            onSave = { value ->
                target.onSave(value)
                editDialog = null
            },
            onDismiss = { editDialog = null }
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

    if (showLogLevelDialog) {
        ChoiceDialog(
            title = "Core Log Level",
            options = listOf("debug", "info", "warning", "error", "none").map { it to it },
            selected = settings.logLevel,
            onSelect = { lvl ->
                viewModel.updateSettings { it.copy(logLevel = lvl) }
                showLogLevelDialog = false
            },
            onDismiss = { showLogLevelDialog = false }
        )
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it.name to (themeLabels[it.name] ?: it.name) },
            selected = settings.themeMode.name,
            onSelect = { name ->
                viewModel.updateSettings { it.copy(themeMode = ThemeMode.valueOf(name)) }
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLanguageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = LanguageMode.entries.map { it.name to (languageLabels[it.name] ?: it.name) },
            selected = settings.languageMode.name,
            onSelect = { name ->
                viewModel.updateSettings { it.copy(languageMode = LanguageMode.valueOf(name)) }
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
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
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_core)) }
            item {
                SwitchRow(
                    title = "VPN Mode (TUN)",
                    subtitle = if (settings.vpnMode) "Full device VPN tunnel via Xray TUN" else "Proxy-only mode (local SOCKS & HTTP ports)",
                    checked = settings.vpnMode,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(vpnMode = v) } }
                )
            }
            item {
                SwitchRow(
                    title = "Bypass LAN",
                    subtitle = "Route private network IP ranges directly",
                    checked = settings.bypassLan,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(bypassLan = v) } }
                )
            }
            item {
                SwitchRow(
                    title = "Prefer IPv6",
                    subtitle = "Enable IPv6 addresses and routes on TUN interface",
                    checked = settings.preferIpv6,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(preferIpv6 = v) } }
                )
            }
            item {
                SwitchRow(
                    title = "Allow LAN Connections",
                    subtitle = "Listen on 0.0.0.0 instead of 127.0.0.1 for local proxy",
                    checked = settings.allowLan,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(allowLan = v) } }
                )
            }
            item {
                ValueRow(
                    title = "Local SOCKS5 Port",
                    value = settings.socksPort.toString(),
                    onClick = {
                        editDialog = SettingEditTarget("Local SOCKS5 Port", settings.socksPort.toString(), true) { v ->
                            v.toIntOrNull()?.coerceIn(1024, 65535)?.let { p ->
                                viewModel.updateSettings { it.copy(socksPort = p) }
                            }
                        }
                    }
                )
            }
            item {
                ValueRow(
                    title = "Local HTTP Port",
                    value = settings.httpPort.toString(),
                    onClick = {
                        editDialog = SettingEditTarget("Local HTTP Port", settings.httpPort.toString(), true) { v ->
                            v.toIntOrNull()?.coerceIn(1024, 65535)?.let { p ->
                                viewModel.updateSettings { it.copy(httpPort = p) }
                            }
                        }
                    }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.settings_section_dns)) }
            item {
                SwitchRow(
                    title = "Enable Sniffing",
                    subtitle = "Detect HTTP/TLS/QUIC destination domains for routing",
                    checked = settings.sniffingEnabled,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(sniffingEnabled = v) } }
                )
            }
            item {
                SwitchRow(
                    title = "Route Only",
                    subtitle = "Use sniffed domain strictly for routing without overriding target",
                    checked = settings.routeOnly,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(routeOnly = v) } }
                )
            }
            item {
                val forcedByAmir = settings.preAmirLocalDnsEnabled != null || settings.preAmirFakeDnsEnabled != null
                SwitchRow(
                    title = "Enable Local DNS",
                    subtitle = if (forcedByAmir) {
                        "Forced ON while Amir protocol is active (restores automatically)"
                    } else {
                        "Intercept device DNS queries via Xray local DNS server"
                    },
                    checked = settings.localDnsEnabled,
                    enabled = !forcedByAmir,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(localDnsEnabled = v) } }
                )
            }
            item {
                val forcedByAmir = settings.preAmirLocalDnsEnabled != null || settings.preAmirFakeDnsEnabled != null
                SwitchRow(
                    title = "Enable Fake DNS",
                    subtitle = if (forcedByAmir) {
                        "Forced ON while Amir protocol is active (restores automatically)"
                    } else {
                        "Resolve DNS queries using virtual 198.18.0.0/15 pool"
                    },
                    checked = settings.fakeDnsEnabled,
                    enabled = !forcedByAmir,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(fakeDnsEnabled = v) } }
                )
            }
            item {
                ValueRow(
                    title = "Remote DNS",
                    value = settings.remoteDns,
                    onClick = {
                        editDialog = SettingEditTarget("Remote DNS", settings.remoteDns, false) { v ->
                            if (v.isNotBlank()) viewModel.updateSettings { it.copy(remoteDns = v.trim()) }
                        }
                    }
                )
            }
            item {
                ValueRow(
                    title = "Domestic DNS",
                    value = settings.domesticDns,
                    onClick = {
                        editDialog = SettingEditTarget("Domestic DNS", settings.domesticDns, false) { v ->
                            if (v.isNotBlank()) viewModel.updateSettings { it.copy(domesticDns = v.trim()) }
                        }
                    }
                )
            }
            item {
                ValueRow(
                    title = "Delay Test URL",
                    value = settings.delayTestUrl,
                    onClick = {
                        editDialog = SettingEditTarget("Delay Test URL", settings.delayTestUrl, false) { v ->
                            if (v.isNotBlank()) viewModel.updateSettings { it.copy(delayTestUrl = v.trim()) }
                        }
                    }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.settings_section_mux)) }
            item {
                SwitchRow(
                    title = "Enable Mux",
                    subtitle = "Multiplex multiple TCP streams over a single connection",
                    checked = settings.muxEnabled,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(muxEnabled = v) } }
                )
            }
            if (settings.muxEnabled) {
                item {
                    ValueRow(
                        title = "Mux Concurrency",
                        value = settings.muxConcurrency.toString(),
                        onClick = {
                            editDialog = SettingEditTarget("Mux Concurrency", settings.muxConcurrency.toString(), true) { v ->
                                v.toIntOrNull()?.coerceIn(1, 1024)?.let { c ->
                                    viewModel.updateSettings { it.copy(muxConcurrency = c) }
                                }
                            }
                        }
                    )
                }
            }
            item {
                SwitchRow(
                    title = "Enable Fragment",
                    subtitle = "Split TLS ClientHello packets via freedom dialerProxy",
                    checked = settings.fragmentEnabled,
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(fragmentEnabled = v) } }
                )
            }
            if (settings.fragmentEnabled) {
                item {
                    ValueRow(
                        title = "Fragment Packets",
                        value = settings.fragmentPackets,
                        onClick = {
                            editDialog = SettingEditTarget("Fragment Packets (e.g. tlshello, 1-3)", settings.fragmentPackets, false) { v ->
                                if (v.isNotBlank()) viewModel.updateSettings { it.copy(fragmentPackets = v.trim()) }
                            }
                        }
                    )
                }
                item {
                    ValueRow(
                        title = "Fragment Length",
                        value = settings.fragmentLength,
                        onClick = {
                            editDialog = SettingEditTarget("Fragment Length (e.g. 100-200)", settings.fragmentLength, false) { v ->
                                if (v.isNotBlank()) viewModel.updateSettings { it.copy(fragmentLength = v.trim()) }
                            }
                        }
                    )
                }
                item {
                    ValueRow(
                        title = "Fragment Interval (ms)",
                        value = settings.fragmentInterval,
                        onClick = {
                            editDialog = SettingEditTarget("Fragment Interval (e.g. 10-20)", settings.fragmentInterval, false) { v ->
                                if (v.isNotBlank()) viewModel.updateSettings { it.copy(fragmentInterval = v.trim()) }
                            }
                        }
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.settings_section_general)) }
            item {
                ValueRow(
                    title = "Core Log Level",
                    value = settings.logLevel,
                    onClick = { showLogLevelDialog = true }
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.settings_theme),
                    value = themeLabels[settings.themeMode.name] ?: settings.themeMode.name,
                    onClick = { showThemeDialog = true }
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.settings_language),
                    value = languageLabels[settings.languageMode.name] ?: settings.languageMode.name,
                    onClick = { showLanguageDialog = true }
                )
            }
        }
    }
}

private data class SettingEditTarget(
    val title: String,
    val initialValue: String,
    val isNumeric: Boolean,
    val onSave: (String) -> Unit
)

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
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
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TextSettingDialog(
    title: String,
    initialValue: String,
    isNumeric: Boolean,
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
                    value = if (isNumeric) next.filter(Char::isDigit) else next
                },
                singleLine = true,
                keyboardOptions = if (isNumeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
