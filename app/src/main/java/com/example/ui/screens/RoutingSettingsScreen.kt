package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.RoutingMode
import com.example.data.model.RoutingRule
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingSettingsScreen(
    viewModel: MainViewModel,
    onApplyReconnect: () -> Unit = {},
    onBack: () -> Unit
) {
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
    val rules by viewModel.routingRulesFlow.collectAsStateWithLifecycle()
    val pendingTunnelRestart by viewModel.pendingTunnelRestart.collectAsStateWithLifecycle()
    var showRuleDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = !showRuleDialog) {
        onBack()
    }

    if (showRuleDialog) {
        AddRoutingRuleDialog(
            onSave = { rule ->
                viewModel.saveRoutingRule(rule.copy(sortOrder = rules.size))
                showRuleDialog = false
            },
            onDismiss = { showRuleDialog = false }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.74f)
                ),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("routing_back_button")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Routing Settings", fontWeight = FontWeight.SemiBold) }
            )
        },
        floatingActionButton = {
            if (settings.routingMode == RoutingMode.CUSTOM) {
                FloatingActionButton(
                    onClick = { showRuleDialog = true },
                    modifier = Modifier.testTag("add_routing_rule_fab")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add custom rule")
                }
            }
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
                            modifier = Modifier.testTag("routing_apply_reconnect_button")
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
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            item {
                Text(
                    text = "Routing Mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
                    border = BorderStroke(0.85.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        RoutingMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.routingMode == mode,
                                    onClick = {
                                        viewModel.updateSettings { it.copy(routingMode = mode) }
                                    }
                                )
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            item {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = settings.domainStrategy,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Domain Strategy") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf("IPIfNonMatch", "AsIs", "IPOnDemand").forEach { strat ->
                            DropdownMenuItem(
                                text = { Text(strat) },
                                onClick = {
                                    expanded = false
                                    viewModel.updateSettings { it.copy(domainStrategy = strat) }
                                }
                            )
                        }
                    }
                }
            }

            if (settings.routingMode == RoutingMode.CUSTOM) {
                item {
                    Text(
                        text = "Custom Ruleset",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(rules, key = { it.id }) { rule ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
                        border = BorderStroke(0.85.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = rule.outboundTag.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(rule.remarks, style = MaterialTheme.typography.titleSmall)
                                }
                                if (rule.domains.isNotBlank()) {
                                    Text(
                                        text = "Domains: ${rule.domains}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (rule.ips.isNotBlank()) {
                                    Text(
                                        text = "IPs: ${rule.ips}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { enabled ->
                                    viewModel.saveRoutingRule(rule.copy(enabled = enabled))
                                }
                            )
                            IconButton(onClick = { viewModel.deleteRoutingRule(rule.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete rule")
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(64.dp)) }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRoutingRuleDialog(
    onSave: (RoutingRule) -> Unit,
    onDismiss: () -> Unit
) {
    var remarks by remember { mutableStateOf("") }
    var outboundTag by remember { mutableStateOf("direct") }
    var domains by remember { mutableStateOf("") }
    var ips by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Routing Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = outboundTag,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Outbound Tag") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("proxy", "direct", "block").forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag) },
                                onClick = {
                                    outboundTag = tag
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = domains,
                    onValueChange = { domains = it },
                    label = { Text("Domains (comma separated)") },
                    placeholder = { Text("geosite:ir,domain:example.com") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ips,
                    onValueChange = { ips = it },
                    label = { Text("IPs (comma separated)") },
                    placeholder = { Text("geoip:private,192.168.0.0/16") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = domains.isNotBlank() || ips.isNotBlank() || port.isNotBlank(),
                onClick = {
                    onSave(
                        RoutingRule(
                            remarks = remarks.ifBlank { "Custom Rule" },
                            enabled = true,
                            outboundTag = outboundTag,
                            domains = domains.trim(),
                            ips = ips.trim(),
                            port = port.trim()
                        )
                    )
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
