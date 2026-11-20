package com.example.ui.screens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppProxyScreen(
    viewModel: MainViewModel,
    onApplyReconnect: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
    val pendingTunnelRestart by viewModel.pendingTunnelRestart.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            resolved
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val appInfo = info.activityInfo.applicationInfo
                    val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val label = info.loadLabel(pm)?.toString() ?: pkg
                    InstalledAppItem(pkg, label, isSys)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }
        }
    }

    val filtered = remember(apps, search, showSystem, settings.perAppPackages) {
        apps.filter { item ->
            (showSystem || !item.isSystem || settings.perAppPackages.contains(item.packageName)) &&
                (search.isBlank() ||
                    item.appName.contains(search, ignoreCase = true) ||
                    item.packageName.contains(search, ignoreCase = true))
        }.sortedByDescending { settings.perAppPackages.contains(it.packageName) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.74f)
                ),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("per_app_back_button")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Per-App Proxy", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.updateSettings { it.copy(perAppPackages = emptySet()) }
                        }
                    ) {
                        Icon(Icons.Outlined.ClearAll, contentDescription = "Clear selected apps")
                    }
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
                            modifier = Modifier.testTag("per_app_apply_reconnect_button")
                        ) {
                            Text(
                                text = stringResource(R.string.action_apply_now),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Per-App Proxy", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (settings.perAppBypassMode) "Bypass selected apps (direct)" else "Proxy only selected apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.perAppProxyEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings { it.copy(perAppProxyEnabled = enabled) }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = settings.perAppBypassMode,
                    onClick = { viewModel.updateSettings { it.copy(perAppBypassMode = true) } },
                    label = { Text("Bypass Mode") }
                )
                FilterChip(
                    selected = !settings.perAppBypassMode,
                    onClick = { viewModel.updateSettings { it.copy(perAppBypassMode = false) } },
                    label = { Text("Proxy-Only Mode") }
                )
                FilterChip(
                    selected = showSystem,
                    onClick = { showSystem = !showSystem },
                    label = { Text("System") }
                )
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search apps (${settings.perAppPackages.size} selected)") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.packageName }) { appItem ->
                    val checked = settings.perAppPackages.contains(appItem.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.updateSettings { cur ->
                                    val next = cur.perAppPackages.toMutableSet()
                                    if (checked) next.remove(appItem.packageName) else next.add(appItem.packageName)
                                    cur.copy(perAppPackages = next)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appItem.appName,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = appItem.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                viewModel.updateSettings { cur ->
                                    val next = cur.perAppPackages.toMutableSet()
                                    if (isChecked) next.add(appItem.packageName) else next.remove(appItem.packageName)
                                    cur.copy(perAppPackages = next)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
