package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.ConfigParser
import com.example.data.model.ProtocolType
import com.example.data.model.ServerConfig
import com.example.ui.MainViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    viewModel: MainViewModel,
    serverId: String?,
    initialProtocol: String,
    onBack: () -> Unit
) {
    val defaultProto = remember(initialProtocol) {
        ProtocolType.entries.firstOrNull { it.name == initialProtocol } ?: ProtocolType.VLESS
    }
    var config by remember {
        mutableStateOf(
            ServerConfig(
                protocol = defaultProto,
                passwordOrId = if (defaultProto == ProtocolType.VMESS || defaultProto == ProtocolType.VLESS) {
                    UUID.randomUUID().toString()
                } else "",
                security = when (defaultProto) {
                    ProtocolType.VLESS -> "none"
                    ProtocolType.SHADOWSOCKS -> "aes-256-gcm"
                    else -> "auto"
                },
                streamSecurity = if (defaultProto == ProtocolType.TROJAN || defaultProto == ProtocolType.HYSTERIA2) "tls" else ""
            )
        )
    }
    var portText by remember { mutableStateOf("443") }
    var alterIdText by remember { mutableStateOf("0") }
    var loaded by remember { mutableStateOf(serverId == null) }

    LaunchedEffect(serverId) {
        if (serverId != null) {
            viewModel.getServerById(serverId)?.let { existing ->
                config = if (existing.protocol == ProtocolType.AMIR && existing.extraParam1.isBlank() && existing.address.isNotBlank()) {
                    existing.copy(extraParam1 = "wss://${existing.address}/")
                } else {
                    existing
                }
                portText = existing.port.toString()
                alterIdText = existing.alterId.toString()
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("edit_back_button")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = if (serverId == null) "Add ${config.protocol.displayName}" else "Edit ${config.protocol.displayName}"
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (config.protocol == ProtocolType.AMIR) {
                                val normalizedUrl = ConfigParser.normalizeAmirWorkerUrl(
                                    config.extraParam1.ifBlank { config.address }
                                )
                                val parsedUri = Uri.parse(normalizedUrl)
                                val workerHost = parsedUri.host.orEmpty().ifBlank { config.address.trim() }
                                val workerPort = if (parsedUri.port > 0) parsedUri.port else 443
                                val finalRemarks = config.remarks.ifBlank {
                                    if (workerHost.isNotBlank()) "Amir ($workerHost)" else "Amir"
                                }
                                viewModel.saveServer(
                                    config.copy(
                                        remarks = finalRemarks,
                                        address = workerHost,
                                        port = workerPort,
                                        extraParam1 = normalizedUrl,
                                        extraParam2 = config.extraParam2.trim(),
                                        passwordOrId = config.passwordOrId.trim()
                                    )
                                )
                            } else {
                                val finalPort = portText.toIntOrNull()?.coerceIn(1, 65535) ?: 443
                                val finalAid = alterIdText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                                val finalRemarks = config.remarks.ifBlank {
                                    if (config.protocol == ProtocolType.CUSTOM) "Custom Config" else "${config.address}:$finalPort"
                                }
                                viewModel.saveServer(
                                    config.copy(
                                        remarks = finalRemarks,
                                        port = finalPort,
                                        alterId = finalAid
                                    )
                                )
                            }
                            onBack()
                        },
                        modifier = Modifier.testTag("save_profile_button")
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = "Save configuration")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!loaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DropdownField(
                label = "Protocol",
                selected = config.protocol.displayName,
                options = ProtocolType.entries.map { it.displayName },
                onSelect = { label ->
                    val proto = ProtocolType.entries.first { it.displayName == label }
                    config = if (proto == ProtocolType.AMIR && config.extraParam1.isBlank() && config.address.isNotBlank()) {
                        config.copy(protocol = proto, extraParam1 = "wss://${config.address}/")
                    } else {
                        config.copy(protocol = proto)
                    }
                }
            )

            OutlinedTextField(
                value = config.remarks,
                onValueChange = { config = config.copy(remarks = it) },
                label = { Text("Remarks") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_remarks_input")
            )

            if (config.protocol == ProtocolType.CUSTOM) {
                OutlinedTextField(
                    value = config.rawCustomJson,
                    onValueChange = { config = config.copy(rawCustomJson = it) },
                    label = { Text("Raw Xray JSON") },
                    minLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (config.protocol == ProtocolType.AMIR) {
                OutlinedTextField(
                    value = config.extraParam1,
                    onValueChange = { config = config.copy(extraParam1 = it.trim()) },
                    label = { Text("Worker URL") },
                    placeholder = { Text("wss://example.workers.dev/") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_amir_worker_url")
                )
                OutlinedTextField(
                    value = config.extraParam2,
                    onValueChange = { config = config.copy(extraParam2 = it.trim()) },
                    label = { Text("CF Clean IP") },
                    placeholder = { Text("172.67.70.244") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_amir_clean_ip")
                )
                OutlinedTextField(
                    value = config.passwordOrId,
                    onValueChange = { config = config.copy(passwordOrId = it.trim()) },
                    label = { Text("UUID") },
                    placeholder = { Text("63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_amir_uuid")
                )
                Text(
                    text = "Note: Enable Local DNS and Enable Fake DNS will be turned on automatically when connecting to an Amir config, and restored afterward.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = config.address,
                        onValueChange = { config = config.copy(address = it.trim()) },
                        label = { Text("Address (IP / Domain)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(2f)
                            .testTag("edit_address_input")
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter(Char::isDigit) },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_port_input")
                    )
                }

                when (config.protocol) {
                    ProtocolType.VMESS, ProtocolType.VLESS -> {
                        OutlinedTextField(
                            value = config.passwordOrId,
                            onValueChange = { config = config.copy(passwordOrId = it.trim()) },
                            label = { Text("UUID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (config.protocol == ProtocolType.VMESS) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = alterIdText,
                                    onValueChange = { alterIdText = it.filter(Char::isDigit) },
                                    label = { Text("AlterID") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                DropdownField(
                                    label = "Security",
                                    selected = config.security.ifBlank { "auto" },
                                    options = listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero"),
                                    onSelect = { config = config.copy(security = it) },
                                    modifier = Modifier.weight(2f)
                                )
                            }
                        } else {
                            DropdownField(
                                label = "Flow",
                                selected = config.flow,
                                options = listOf("", "xtls-rprx-vision", "xtls-rprx-vision-udp443"),
                                onSelect = { config = config.copy(flow = it) }
                            )
                        }
                    }

                    ProtocolType.TROJAN -> {
                        OutlinedTextField(
                            value = config.passwordOrId,
                            onValueChange = { config = config.copy(passwordOrId = it) },
                            label = { Text("Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ProtocolType.SHADOWSOCKS -> {
                        OutlinedTextField(
                            value = config.passwordOrId,
                            onValueChange = { config = config.copy(passwordOrId = it) },
                            label = { Text("Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownField(
                            label = "Encryption Method",
                            selected = config.security.ifBlank { "aes-256-gcm" },
                            options = listOf(
                                "aes-256-gcm",
                                "aes-128-gcm",
                                "chacha20-ietf-poly1305",
                                "2022-blake3-aes-128-gcm",
                                "2022-blake3-aes-256-gcm",
                                "2022-blake3-chacha20-poly1305",
                                "none"
                            ),
                            onSelect = { config = config.copy(security = it) }
                        )
                    }

                    ProtocolType.SOCKS, ProtocolType.HTTP -> {
                        OutlinedTextField(
                            value = config.extraParam1,
                            onValueChange = { config = config.copy(extraParam1 = it) },
                            label = { Text("Username (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = config.passwordOrId,
                            onValueChange = { config = config.copy(passwordOrId = it) },
                            label = { Text("Password (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ProtocolType.WIREGUARD -> {
                        OutlinedTextField(
                            value = config.passwordOrId,
                            onValueChange = { config = config.copy(passwordOrId = it.trim()) },
                            label = { Text("Private Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = config.publicKey,
                            onValueChange = { config = config.copy(publicKey = it.trim()) },
                            label = { Text("Peer Public Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = config.extraParam1,
                            onValueChange = { config = config.copy(extraParam1 = it.trim()) },
                            label = { Text("Local Address (e.g. 10.0.0.2/32)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = config.extraParam2,
                                onValueChange = { config = config.copy(extraParam2 = it.filter(Char::isDigit)) },
                                label = { Text("MTU") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = config.extraParam3,
                                onValueChange = { config = config.copy(extraParam3 = it) },
                                label = { Text("Reserved (e.g. 0,0,0)") },
                                singleLine = true,
                                modifier = Modifier.weight(2f)
                            )
                        }
                    }

                    ProtocolType.HYSTERIA2 -> {
                        OutlinedTextField(
                            value = config.passwordOrId,
                            onValueChange = { config = config.copy(passwordOrId = it) },
                            label = { Text("Auth Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = config.extraParam3,
                            onValueChange = { config = config.copy(extraParam3 = it) },
                            label = { Text("Port Hopping (e.g. 20000-40000)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ProtocolType.AMIR, ProtocolType.CUSTOM -> Unit
                }

                if (config.protocol in listOf(
                        ProtocolType.VMESS,
                        ProtocolType.VLESS,
                        ProtocolType.TROJAN,
                        ProtocolType.SHADOWSOCKS
                    )
                ) {
                    Text(
                        text = "Transport & TLS Settings",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    DropdownField(
                        label = "Transport Network",
                        selected = config.network.ifBlank { "tcp" },
                        options = listOf("tcp", "ws", "grpc", "httpupgrade", "xhttp", "h2", "kcp"),
                        onSelect = { config = config.copy(network = it) }
                    )
                    if (config.network in listOf("tcp", "kcp")) {
                        DropdownField(
                            label = "Header Type",
                            selected = config.headerType.ifBlank { "none" },
                            options = listOf("none", "http", "srtp", "utp", "wechat-video", "dtls", "wireguard"),
                            onSelect = { config = config.copy(headerType = it) }
                        )
                    }
                    OutlinedTextField(
                        value = config.host,
                        onValueChange = { config = config.copy(host = it.trim()) },
                        label = { Text("Request Host / Authority") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = config.path,
                        onValueChange = { config = config.copy(path = it.trim()) },
                        label = { Text(if (config.network == "grpc") "gRPC Service Name" else "Path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownField(
                        label = "Stream Security",
                        selected = config.streamSecurity,
                        options = listOf("", "tls", "reality"),
                        onSelect = { config = config.copy(streamSecurity = it) }
                    )
                }

                if (config.streamSecurity == "tls" || config.streamSecurity == "reality" || config.protocol == ProtocolType.HYSTERIA2) {
                    OutlinedTextField(
                        value = config.sni,
                        onValueChange = { config = config.copy(sni = it.trim()) },
                        label = { Text("SNI (Server Name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownField(
                        label = "uTLS Fingerprint",
                        selected = config.fingerprint,
                        options = listOf("", "chrome", "firefox", "safari", "ios", "android", "edge", "random", "randomized"),
                        onSelect = { config = config.copy(fingerprint = it) }
                    )
                    OutlinedTextField(
                        value = config.alpn,
                        onValueChange = { config = config.copy(alpn = it.trim()) },
                        label = { Text("ALPN (e.g. h2,http/1.1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (config.streamSecurity == "reality") {
                        OutlinedTextField(
                            value = config.publicKey,
                            onValueChange = { config = config.copy(publicKey = it.trim()) },
                            label = { Text("REALITY Public Key (pbk)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = config.shortId,
                            onValueChange = { config = config.copy(shortId = it.trim()) },
                            label = { Text("REALITY Short ID (sid)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = config.spiderX,
                            onValueChange = { config = config.copy(spiderX = it.trim()) },
                            label = { Text("REALITY SpiderX (spx)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Allow Insecure (skip cert verification)")
                            Switch(
                                checked = config.allowInsecure,
                                onCheckedChange = { config = config.copy(allowInsecure = it) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected.ifEmpty { "(none)" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.ifEmpty { "(none)" }) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
