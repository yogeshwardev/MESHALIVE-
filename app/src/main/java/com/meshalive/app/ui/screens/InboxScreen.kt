package com.meshalive.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshalive.app.ui.MeshViewModel
import com.meshalive.app.ui.components.Chip
import com.meshalive.app.ui.components.InfoRow
import com.meshalive.app.ui.components.QrCode
import com.meshalive.app.ui.components.SectionCard
import com.meshalive.app.ui.components.WarningBanner
import com.meshalive.app.ui.theme.GatewayGreen
import com.meshalive.app.ui.theme.LowBatteryOrange
import com.meshalive.app.ui.theme.SosRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InboxScreen(vm: MeshViewModel) {
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fmt = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!state.hasInternet) item {
            WarningBanner("No internet detected. SOS will be stored here and uploaded automatically when internet returns.")
        }
        if (settings.cloudEnabled) item {
            SectionCard(title = "Live alert channel") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrCode(settings.cloudUrl, Modifier.size(132.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Scan to watch SOS alerts arrive live", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Opens in any browser, or subscribe in the free ntfy app for push notifications.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(settings.cloudUrl.removePrefix("https://"), fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelMedium, color = SosRed)
                    }
                }
                Text(
                    "Control room: open dashboard/index.html and enter channel \"${settings.cloudTopic}\" to see every SOS on a live map, sorted by urgency.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.sendCloudTest() }, modifier = Modifier.weight(1f)) { Text("Send test alert") }
                    OutlinedButton(onClick = { open(settings.cloudUrl) }, modifier = Modifier.weight(1f)) { Text("Open live page") }
                }
            }
        }
        item {
            SectionCard {
                InfoRow("Alerts received", "${inbox.size}")
                InfoRow("Uploaded to internet", "${inbox.count { it.cloudPushed }}", GatewayGreen)
                val waiting = inbox.count { !it.cloudPushed && (settings.cloudEnabled || settings.webhookUrl.isNotBlank()) }
                if (waiting > 0) InfoRow("Waiting to upload", "$waiting", LowBatteryOrange)
            }
        }
        if (inbox.isEmpty()) item {
            Text("No SOS received yet. Messages from the mesh will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(inbox, key = { it.msgId }) { e ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(e.type, SosRed)
                    if (e.urgency > 0) Chip("${com.meshalive.algorithms.Triage.band(e.urgency)} ${e.urgency}", urgencyColor(e.urgency))
                    Text(e.srcName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(fmt.format(Date(e.receivedAt)), style = MaterialTheme.typography.labelSmall)
                }
                Text(e.text, style = MaterialTheme.typography.bodyLarge)
                if (e.rescueStatus.isNotBlank()) Text("🚤 " + com.meshalive.app.mesh.RescueStatus.label(e.rescueStatus),
                    color = GatewayGreen, fontWeight = FontWeight.Bold)
                if (e.triage.isNotBlank()) Text("👥 " + e.triage, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Column {
                    Text(
                        if (e.lat != null && e.lng != null) "📍 %.5f, %.5f".format(e.lat, e.lng) else "📍 Location not available",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Sent ${fmt.format(Date(e.sentAt))} · ${e.hopCount} hop(s)", style = MaterialTheme.typography.bodySmall)
                    Text("Path: ${e.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (e.webhookStatus.isNotBlank()) Text(
                        "☁ ${e.webhookStatus}", style = MaterialTheme.typography.bodySmall,
                        color = if (e.cloudPushed) GatewayGreen else LowBatteryOrange,
                    )
                }
                if (e.lat != null && e.lng != null) {
                    Button(onClick = {
                        val geo = Uri.parse("geo:${e.lat},${e.lng}?q=${e.lat},${e.lng}(${Uri.encode(e.srcName)})")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo)) }
                            .onFailure { open("https://www.google.com/maps/search/?api=1&query=${e.lat},${e.lng}") }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Open in Maps") }
                }
            }
        }
    }
}
