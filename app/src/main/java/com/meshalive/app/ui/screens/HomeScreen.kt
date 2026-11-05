package com.meshalive.app.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshalive.algorithms.Triage
import com.meshalive.app.data.Settings
import com.meshalive.app.mesh.MeshState
import com.meshalive.app.mesh.PacketType
import com.meshalive.app.mesh.RescueStatus
import com.meshalive.app.mesh.TriageInfo
import com.meshalive.app.ui.MeshViewModel
import com.meshalive.app.ui.Tamil
import com.meshalive.app.ui.components.InfoRow
import com.meshalive.app.ui.components.SectionCard
import com.meshalive.app.ui.components.WarningBanner
import com.meshalive.app.ui.theme.GatewayGreen
import com.meshalive.app.ui.theme.HeroGradient
import com.meshalive.app.ui.theme.LowBatteryOrange
import com.meshalive.app.ui.theme.MeBlue
import com.meshalive.app.ui.theme.SafeGreen
import com.meshalive.app.ui.theme.SosGradient
import com.meshalive.app.ui.theme.SosRed
import com.meshalive.app.ui.tr
import kotlinx.coroutines.delay

fun urgencyColor(u: Int): Color = when {
    u >= 75 -> SosRed
    u >= 50 -> Color(0xFFE65100)
    u >= 30 -> Color(0xFFF59E0B)
    else -> GatewayGreen
}

/** An emergency waiting for the 3-second countdown, so a stray tap never sends an SOS. */
private data class PendingSend(val label: String, val color: Color, val send: () -> Unit)

@Composable
fun HomeScreen(vm: MeshViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val flags by vm.flags.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val pending by vm.pendingCount.collectAsStateWithLifecycle()
    val latestAlert by vm.latestAlert.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val rescue = messages.firstOrNull { it.outgoing && it.type != "CHAT" && it.type != "UPDATE" && it.rescueStatus.isNotBlank() }
    val lastSos = messages.firstOrNull { it.outgoing && (it.type == "SOS" || it.type == "MEDICAL") }
    val context = LocalContext.current
    val ta = settings.lang == "ta"
    val sosShown = if (ta && settings.sosText == Settings.DEFAULT_SOS_TEXT) Tamil.SOS_TEXT else settings.sosText
    var editing by remember { mutableStateOf(false) }
    var sosText by remember(sosShown) { mutableStateOf(sosShown) }
    var confirm by remember { mutableStateOf<PendingSend?>(null) }
    var showDetails by rememberSaveable { mutableStateOf(false) }

    confirm?.let { p -> CountdownDialog(p, onCancel = { confirm = null }, onSend = { confirm = null; p.send() }) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StatusHero(state, settings, pending)

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(2.dp))
            if (!flags.bluetoothOn) WarningBanner("Bluetooth is off. MeshAlive cannot reach nearby phones.", "Turn on") {
                runCatching { context.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS)) }
            }
            if (!flags.locationOn) WarningBanner("Location is off. SOS will be sent without GPS coordinates.", "Settings") {
                runCatching { context.startActivity(Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            }
            state.error?.let { WarningBanner(it, "Retry") { vm.refreshFlags() } }

            // ---------- word from the control room, delivered through the offline mesh ----------
            latestAlert?.takeIf { System.currentTimeMillis() - it.timestamp < 12 * 60 * 60_000L }?.let { a ->
                val lines = a.text.split("\n", limit = 2)
                Banner(SosRed, Icons.Filled.Campaign, "${a.status} · ${a.srcName}", lines[0], lines.getOrNull(1),
                    "Received through ${a.hopCount} phone(s) · no signal needed")
            }
            rescue?.let { m ->
                Banner(SafeGreen, Icons.Filled.DirectionsBoat, tr("From the district control room"),
                    tr(RescueStatus.label(m.rescueStatus)), m.rescueNote.ifBlank { null }, tr("Stay where you are. Keep this phone on."))
            }

            // ---------- SOS ----------
            Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                if (state.running) RadarPulse(Modifier.size(260.dp), state.neighbours.isNotEmpty())
                Box(
                    Modifier.size(196.dp).shadow(24.dp, CircleShape, spotColor = SosRed).clip(CircleShape).background(SosGradient)
                        .border(6.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable(enabled = !busy) {
                            confirm = PendingSend("SOS", SosRed) { vm.sendEmergency(PacketType.SOS, sosShown) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(tr("getting GPS…"), color = Color.White, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SOS", color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text(tr("tap to send"), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center)
                    }
                }
            }
            lastSos?.let { m ->
                Text(
                    "Last SOS: " + when {
                        m.rescueStatus.isNotBlank() -> RescueStatus.label(m.rescueStatus)
                        m.status == "Delivered" -> "reached the control room · ${m.hopCount} hop(s)"
                        else -> m.status
                    },
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (m.status == "Delivered" || m.rescueStatus.isNotBlank()) SafeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (editing) {
                OutlinedTextField(sosText, { sosText = it }, label = { Text(tr("SOS message")) }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.updateSettings { it.copy(sosText = sosText) }; editing = false }) { Text(tr("Save")) }
                    TextButton(onClick = { editing = false; sosText = sosShown }) { Text(tr("Cancel")) }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                        .clickable { editing = true }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("“$sosShown”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Filled.Edit, tr("Edit"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            // ---------- quick actions ----------
            val medicalLabel = tr("Medical help")
            val trappedLabel = tr("Trapped")
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(medicalLabel, Icons.Filled.LocalHospital, Color(0xFFD81B60), Modifier.weight(1f), !busy) {
                    confirm = PendingSend(medicalLabel, Color(0xFFD81B60)) {
                        vm.sendEmergency(PacketType.MEDICAL, if (ta) Tamil.MEDICAL_TEXT else "Medical help needed urgently", Triage.BASE_MEDICAL) { it.copy(injured = true) }
                    }
                }
                ActionTile(trappedLabel, Icons.Filled.Lock, Color(0xFFE65100), Modifier.weight(1f), !busy) {
                    confirm = PendingSend(trappedLabel, Color(0xFFE65100)) {
                        vm.sendEmergency(PacketType.SOS, if (ta) Tamil.TRAPPED_TEXT else "We are trapped and cannot get out") { it.copy(trapped = true) }
                    }
                }
            }
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(tr("Water / food"), Icons.Filled.WaterDrop, MeBlue, Modifier.weight(1f), !busy) {
                    vm.sendEmergency(PacketType.MEDICAL, if (ta) Tamil.SUPPLIES_TEXT else "Need drinking water and food", Triage.BASE_SUPPLIES)
                }
                ActionTile(tr("I'm safe"), Icons.Filled.CheckCircle, SafeGreen, Modifier.weight(1f), !busy) { vm.sendSafe() }
            }

            SituationCard(settings, vm.triageFor(Triage.BASE_SOS), vm)

            // ---------- this phone's role ----------
            SectionCard {
                RoleRow(Icons.Filled.CellTower, SafeGreen, tr("I have internet – act as gateway"),
                    when {
                        settings.gatewayMode && !state.hasInternet -> "Gateway on, but no internet detected yet"
                        !settings.gatewayMode && state.isGateway -> "Automatic: this phone has signal and uploads SOS"
                        else -> tr("Receive SOS from the mesh and forward them online")
                    },
                    settings.gatewayMode || state.isGateway) { vm.setGateway(it) }
                RoleRow(Icons.Filled.DirectionsBoat, MeBlue, tr("Rescue carrier (boat / NDRF / ambulance)"),
                    tr("Carries SOS out of the dead zone and uploads them when it reaches signal"),
                    settings.carrierMode) { on -> vm.updateSettings { it.copy(carrierMode = on) } }
            }

            // ---------- details ----------
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { showDetails = !showDetails }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Connection status"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(if (showDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(showDetails) {
                SectionCard {
                    InfoRow("Known phones in mesh", "${state.graph.size}")
                    InfoRow(
                        "My battery",
                        if (state.reportedBattery != state.battery) "${state.battery}% (demo: reporting ${state.reportedBattery}%)" else "${state.battery}%",
                        if (state.reportedBattery < 20) LowBatteryOrange else MaterialTheme.colorScheme.onSurface,
                    )
                    InfoRow("Relayed for others", "${state.relayedCount}")
                    InfoRow("Internet detected", if (state.hasInternet) "Yes" else "No")
                    InfoRow("Waiting in store-and-forward", "$pending")
                    InfoRow("Routing", settings.algorithm.label)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Navy header: is the mesh alive, can my SOS reach the control room right now? */
@Composable
private fun StatusHero(state: MeshState, s: Settings, pending: Int) {
    val hops = state.routeToGateway.size - 1
    val (headline, sub, ok) = when {
        !state.running -> Triple("Starting…", "Getting the relay ready", false)
        state.isGateway -> Triple("You are the gateway", "SOS from nearby phones reach the control room through you", true)
        state.gatewayReachable && hops > 0 -> Triple("Control room reachable", "Your SOS will travel $hops hop(s) through the mesh", true)
        state.neighbours.isNotEmpty() -> Triple("Connected, no route yet", "Your SOS will wait and send itself when a route appears", false)
        else -> Triple(tr("Scanning for nearby phones…"), "SOS still works: it is saved and sent as soon as anyone is in range", false)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(HeroGradient)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(if (ok) Color(0xFF4ADE80) else Color(0xFFFBBF24))
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.running) "MESH ACTIVE" else "STARTING", color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.weight(1f))
            if (s.carrierMode) Pill("Rescue carrier", Color(0xFF93C5FD))
            if (state.isGateway) Pill("Gateway", Color(0xFF86EFAC))
        }
        Text(headline, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text(sub, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat(Icons.Filled.Hub, "${state.neighbours.size}", "nearby", Modifier.weight(1f))
            HeroStat(Icons.Filled.Groups, "${state.graph.size}", "in mesh", Modifier.weight(1f))
            HeroStat(Icons.Filled.BatteryChargingFull, "${state.reportedBattery}%", "battery", Modifier.weight(1f))
            if (pending > 0) HeroStat(Icons.Filled.Campaign, "$pending", "waiting", Modifier.weight(1f))
        }
    }
}

@Composable
private fun LiveDot(color: Color) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.35f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(900),
        androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    Box(Modifier.size(10.dp).background(color.copy(alpha = a), CircleShape))
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 6.dp).border(1.dp, color.copy(alpha = 0.6f), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun HeroStat(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Column(
        modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun Banner(color: Color, icon: ImageVector, kicker: String, title: String, body: String?, foot: String) {
    Row(
        Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp), spotColor = color).background(color, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Box(Modifier.size(42.dp).background(Color.White.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(kicker.uppercase(), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
            if (body != null) Text(body, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text(foot, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, color: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier.fillMaxHeight().heightIn(min = 72.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun RoleRow(icon: ImageVector, color: Color, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = color))
    }
}

/** Full-screen 3-second countdown: tap Cancel if it was an accident, or Send now. */
@Composable
private fun CountdownDialog(p: PendingSend, onCancel: () -> Unit, onSend: () -> Unit) {
    var left by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        while (left > 0) { delay(1000); left-- }
        onSend()
    }
    val pulse by animateFloatAsState(if (left % 2 == 0) 1f else 1.08f, tween(500), label = "pulse")
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(p.color).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Sending ${p.label} in", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(180.dp).scale(pulse).background(Color.White.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                Text("$left", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(16.dp))
            Text("With your location and situation report", color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onSend, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = p.color),
            ) { Text("Send now", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onCancel, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, Color.White),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) { Text(tr("Cancel"), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** Structured situation report sent with every SOS, with a live urgency score. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SituationCard(s: Settings, preview: TriageInfo, vm: MeshViewModel) {
    SectionCard(title = tr("My situation (sent with every SOS)")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("People"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { vm.updateSettings { it.copy(people = (it.people - 1).coerceAtLeast(1)) } },
                contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp)) { Text("−", fontSize = 20.sp) }
            Text("${s.people}", modifier = Modifier.width(44.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = { vm.updateSettings { it.copy(people = (it.people + 1).coerceAtMost(99)) } },
                contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp)) { Text("+", fontSize = 20.sp) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle(tr("Trapped"), s.trapped) { on -> vm.updateSettings { it.copy(trapped = on) } }
            Toggle(tr("Injured"), s.injured) { on -> vm.updateSettings { it.copy(injured = on) } }
            Toggle(tr("Elderly"), s.elderly) { on -> vm.updateSettings { it.copy(elderly = on) } }
            Toggle(tr("Children"), s.children) { on -> vm.updateSettings { it.copy(children = on) } }
            Toggle(tr("Disabled"), s.disabled) { on -> vm.updateSettings { it.copy(disabled = on) } }
        }
        Text(tr("Water level"), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TriageInfo.WATER_LABELS.forEachIndexed { i, label ->
                FilterChip(selected = s.water == i, onClick = { vm.updateSettings { it.copy(water = i) } },
                    label = { Text(tr(label)) }, colors = chipColors(), shape = RoundedCornerShape(10.dp))
            }
        }
        val c = urgencyColor(preview.urgency)
        Column(Modifier.fillMaxWidth().background(c.copy(alpha = 0.10f), RoundedCornerShape(14.dp)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Urgency"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("${preview.urgency} · ${tr(Triage.band(preview.urgency))}", color = c, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).background(c.copy(alpha = 0.18f), CircleShape)) {
                Box(Modifier.fillMaxWidth(preview.urgency / 100f).height(8.dp).background(c, CircleShape))
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = on, onClick = { onChange(!on) }, label = { Text(label) }, colors = chipColors(), shape = RoundedCornerShape(10.dp))
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = SosRed,
    selectedLabelColor = Color.White,
)

/** Expanding rings, like a radar sweep: green when phones are connected, red while searching. */
@Composable
private fun RadarPulse(modifier: Modifier, connected: Boolean) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "radar")
    val p by t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(tween(2400, easing = androidx.compose.animation.core.LinearEasing)),
        label = "p",
    )
    val color = if (connected) GatewayGreen else SosRed
    androidx.compose.foundation.Canvas(modifier) {
        val maxR = size.minDimension / 2
        for (k in 0 until 3) {
            val f = (p + k / 3f) % 1f
            drawCircle(color.copy(alpha = (1f - f) * 0.30f), radius = maxR * (0.72f + 0.28f * f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
        }
    }
}
