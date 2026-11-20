package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SubscriptionGroup
import com.example.service.XrayVpnService
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val subscriptions by viewModel.subscriptionsFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingGroup by remember { mutableStateOf<SubscriptionGroup?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { snackbarHostState.showSnackbar(it) }
    }

    if (showAddDialog || editingGroup != null) {
        SubscriptionEditDialog(
            initial = editingGroup,
            onSave = { group ->
                viewModel.saveSubscription(group)
                showAddDialog = false
                editingGroup = null
            },
            onDismiss = {
                showAddDialog = false
                editingGroup = null
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.74f)
                ),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("subs_back_button")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Subscription Groups", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(
                        onClick = { viewModel.updateAllSubscriptions() },
                        modifier = Modifier.testTag("update_all_subs_button")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Update all subscriptions")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_subscription_fab")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add subscription")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isUpdatingSubs) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (subscriptions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No subscriptions added",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap + to add a remote V2Ray/Xray subscription URL.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(subscriptions, key = { it.id }) { sub ->
                        val serverCount = uiState.servers.count { it.subscriptionId == sub.id }
                        SubscriptionCard(
                            group = sub,
                            serverCount = serverCount,
                            onToggleEnabled = { enabled ->
                                viewModel.saveSubscription(sub.copy(enabled = enabled))
                            },
                            onUpdate = { viewModel.updateSubscription(sub) },
                            onEdit = { editingGroup = sub },
                            onDelete = { viewModel.deleteSubscription(sub.id, deleteConfigs = true) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    group: SubscriptionGroup,
    serverCount: Int,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
        border = BorderStroke(0.85.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.remarks,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = group.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(checked = group.enabled, onCheckedChange = onToggleEnabled)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val lastSync = if (group.lastUpdatedAt > 0L) {
                        "Updated: ${dateFmt.format(Date(group.lastUpdatedAt))}"
                    } else "Never synced"
                    Text(
                        text = "$serverCount node(s) • $lastSync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (group.totalBytes > 0L) {
                        val used = (group.uploadBytes.coerceAtLeast(0L) + group.downloadBytes.coerceAtLeast(0L))
                        Text(
                            text = "Quota: ${XrayVpnService.formatBytes(used)} / ${XrayVpnService.formatBytes(group.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row {
                    IconButton(onClick = onUpdate) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Sync ${group.remarks}")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit ${group.remarks}")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${group.remarks}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionEditDialog(
    initial: SubscriptionGroup?,
    onSave: (SubscriptionGroup) -> Unit,
    onDismiss: () -> Unit
) {
    var remarks by remember { mutableStateOf(initial?.remarks ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var userAgent by remember { mutableStateOf(initial?.userAgent ?: "v2rayNG/1.10.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Subscription" else "Edit Subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Group Name (Remarks)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sub_remarks_input")
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    label = { Text("Subscription URL (https://...)") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sub_url_input")
                )
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { Text("User-Agent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = url.isNotBlank(),
                onClick = {
                    val base = initial ?: SubscriptionGroup(remarks = remarks, url = url)
                    onSave(
                        base.copy(
                            remarks = remarks.ifBlank { "Subscription" },
                            url = url,
                            userAgent = userAgent.ifBlank { "v2rayNG/1.10.0" }
                        )
                    )
                },
                modifier = Modifier.testTag("save_subscription_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
