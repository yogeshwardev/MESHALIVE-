package com.meshalive.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshalive.algorithms.Algorithm
import com.meshalive.app.ui.MeshViewModel
import com.meshalive.app.ui.components.SectionCard
import com.meshalive.app.ui.theme.SosRed

@Composable
fun SettingsScreen(vm: MeshViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var name by remember(s.displayName) { mutableStateOf(s.displayName) }
    var alpha by remember(s.alpha) { mutableStateOf(s.alpha.toString()) }
    var beta by remember(s.beta) { mutableStateOf(s.beta.toString()) }
    var gamma by remember(s.gamma) { mutableStateOf(s.gamma.toString()) }
    var ttl by remember(s.ttl) { mutableStateOf(s.ttl.toString()) }
    var lsa by remember(s.lsaIntervalSec) { mutableStateOf(s.lsaIntervalSec.toString()) }
    var webhook by remember(s.webhookUrl) { mutableStateOf(s.webhookUrl) }
    var pass by remember(s.passphrase) { mutableStateOf(s.passphrase) }
    var topic by remember(s.cloudTopic) { mutableStateOf(s.cloudTopic) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    fun save() = vm.updateSettings {
        it.copy(
            displayName = name.trim(),
            alpha = alpha.toDoubleOrNull() ?: it.alpha,
            beta = beta.toDoubleOrNull() ?: it.beta,
            gamma = gamma.toDoubleOrNull()?.takeIf { g -> g > 0 } ?: it.gamma,
            ttl = ttl.toIntOrNull() ?: it.ttl,
            lsaIntervalSec = lsa.toIntOrNull() ?: it.lsaIntervalSec,
            webhookUrl = webhook,
            passphrase = pass,
            cloudTopic = topic.ifBlank { it.cloudTopic },
        )
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(title = "Identity") {
            OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Node ID: ${s.nodeId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(title = "Routing algorithm") {
            Algorithm.entries.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = s.algorithm == a, onClick = { vm.updateSettings { it.copy(algorithm = a) } }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.algorithm == a, onClick = { vm.updateSettings { it.copy(algorithm = a) } })
                    Text(a.label + if (a == Algorithm.DIJKSTRA) " (default)" else "")
                }
            }
            Text("Edge cost u→v = α·normRTT + β·(1 / max(battery_v, 5)) + γ", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("α", alpha, Modifier.weight(1f)) { alpha = it }
                NumField("β", beta, Modifier.weight(1f)) { beta = it }
                NumField("γ", gamma, Modifier.weight(1f)) { gamma = it }
            }
        }
        SectionCard(title = "Mesh") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("TTL (hops)", ttl, Modifier.weight(1f)) { ttl = it }
                NumField("LSA interval (s)", lsa, Modifier.weight(1f)) { lsa = it }
            }
            OutlinedTextField(webhook, { webhook = it }, label = { Text("Gateway webhook URL (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(pass, { pass = it }, label = { Text("Encryption passphrase") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(
                "All phones must use the same passphrase. Demo-grade AES-GCM, not production security.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard(title = "Internet alerts (gateway)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-upload when signal returns")
                    Text(
                        "Any phone that gets internet becomes a gateway and uploads every SOS it is carrying. Turn off on relay phones during a live demo.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(checked = s.autoGateway, onCheckedChange = { on -> vm.updateSettings { it.copy(autoGateway = on) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Push SOS to a live public channel", modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = s.cloudEnabled, onCheckedChange = { on -> vm.updateSettings { it.copy(cloudEnabled = on) } })
            }
            OutlinedTextField(topic, { topic = it }, label = { Text("Channel name (ntfy.sh topic)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(
                "Free ntfy.sh service, no account. Anyone with the channel name can watch alerts at ntfy.sh/<channel> — use the same name on every gateway.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = { save(); vm.sendCloudTest() }, modifier = Modifier.weight(1f)) { Text("Send test") }
                androidx.compose.material3.OutlinedButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(s.cloudUrl))) }
                }, modifier = Modifier.weight(1f)) { Text("Open channel") }
            }
        }
        SectionCard(title = "Demo tools") {
            val override = s.batteryOverride
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pretend my battery is low")
                    Text(
                        if (override in 1..100) "Reporting $override% to the mesh — Dijkstra will route around this phone"
                        else "Off — reporting the real battery level",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = override in 1..100,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(batteryOverride = if (on) 8 else -1) } },
                )
            }
            if (override in 1..100) androidx.compose.material3.Slider(
                value = override.toFloat(), valueRange = 5f..100f,
                onValueChange = { v -> vm.updateSettings { it.copy(batteryOverride = v.toInt()) } },
            )
            Text(
                "Cut individual radio links from Network → Live map to build a multi-hop chain in a room where every phone can hear each other.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(android.net.Uri.parse("package:${context.packageName}"))
                    )
                }.onFailure {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Keep relay alive in background") }
        }
        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
        Button(
            onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = SosRed),
        ) { Text("Clear data") }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear all data?") },
        text = { Text("Deletes all messages, the gateway inbox and the store-and-forward queue on this phone.") },
        confirmButton = { TextButton(onClick = { vm.clearData(); confirmClear = false }) { Text("Clear", color = SosRed) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
