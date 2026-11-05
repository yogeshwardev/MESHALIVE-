package com.meshalive.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshalive.algorithms.ANY_GATEWAY
import com.meshalive.app.data.MessageEntity
import com.meshalive.app.data.MsgStatus
import com.meshalive.app.ui.MeshViewModel
import com.meshalive.app.ui.components.Chip
import com.meshalive.app.ui.components.StatusChip
import com.meshalive.app.ui.theme.GatewayGreen
import com.meshalive.app.ui.theme.LowBatteryOrange
import com.meshalive.app.ui.theme.MeBlue
import com.meshalive.app.ui.theme.SosRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessagesScreen(vm: MeshViewModel, onOpenChat: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val messages by vm.messages.collectAsStateWithLifecycle()
    val alerts = messages.filter { it.type != "CHAT" }
    val chats = messages.filter { it.type == "CHAT" }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Alerts (${alerts.size})") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Chats") })
        }
        if (tab == 0) AlertsList(alerts, vm) else ChatList(chats, vm, onOpenChat)
    }
}

@Composable
private fun AlertsList(alerts: List<MessageEntity>, vm: MeshViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (alerts.isEmpty()) item {
            Text("No alerts yet. Send an SOS from Home — its journey through the mesh shows up here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(alerts, key = { it.msgId }) { MessageCard(it, vm) }
    }
}

@Composable
private fun ChatList(chats: List<MessageEntity>, vm: MeshViewModel, onOpenChat: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val online = state.neighbours.map { it.nodeId }.toSet()
    val partners = LinkedHashSet<String>()
    chats.forEach { partners += if (it.outgoing) it.dest else it.src }
    state.graph.nodeIds.filter { it != vm.myId }.forEach { partners += it }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (partners.isEmpty()) item {
            Text("No phones found yet. Phones appear here as soon as the mesh discovers them.",
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(partners.toList(), key = { it }) { id ->
            val last = chats.firstOrNull { (it.outgoing && it.dest == id) || (!it.outgoing && it.src == id) }
            val inMesh = state.graph.contains(id)
            val name = state.names[id] ?: last?.let { if (it.outgoing) null else it.srcName } ?: vm.nameOf(id)
            Row(
                Modifier.fillMaxWidth().clickable { onOpenChat(id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).background(MeBlue.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), color = MeBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                    Text(
                        last?.text ?: if (id in online) "Direct neighbour · tap to chat" else if (inMesh) "Reachable through the mesh" else "Offline",
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.size(10.dp).background(
                    when { id in online -> GatewayGreen; inMesh -> LowBatteryOrange; else -> Color(0xFFBDBDBD) }, CircleShape))
            }
            HorizontalDivider(Modifier.padding(start = 72.dp))
        }
    }
}

@Composable
fun ChatScreen(vm: MeshViewModel, peerId: String) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val convo = messages.filter { it.type == "CHAT" && ((it.outgoing && it.dest == peerId) || (!it.outgoing && it.src == peerId)) }
        .sortedBy { it.timestamp }
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(convo.size) { if (convo.isNotEmpty()) listState.animateScrollToItem(convo.size - 1) }
    val reachable = state.graph.contains(peerId)
    val route = remember(state.graph, peerId) {
        runCatching { com.meshalive.algorithms.BfsStrategy.route(state.graph, vm.myId, peerId) }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            if (reachable && route.size >= 2) "${route.size - 1} hop(s): " + route.joinToString(" → ") { vm.nameOf(it) }
            else "Not reachable right now — messages will wait and send automatically",
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (reachable) GatewayGreen else LowBatteryOrange,
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (convo.isEmpty()) item {
                Text("Say hello 👋 — this message travels phone to phone, no internet needed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
            items(convo, key = { it.msgId }) { Bubble(it) }
        }
        HorizontalDivider()
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Message ${vm.nameOf(peerId)}") }, maxLines = 4,
                shape = RoundedCornerShape(24.dp),
            )
            FilledIconButton(
                onClick = { vm.sendChat(peerId, text); text = "" },
                enabled = text.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp).size(48.dp),
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
        }
    }
}

private val timeShort = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
private fun Bubble(m: MessageEntity) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.outgoing) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 280.dp)
                .background(
                    if (m.outgoing) Color(0xFFE3F2FD) else Color(0xFFF1F1F1),
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (m.outgoing) 18.dp else 4.dp, bottomEnd = if (m.outgoing) 4.dp else 18.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(m.text)
            val meta = buildString {
                append(timeShort.format(Date(m.timestamp)))
                if (m.outgoing) append(
                    when (m.status) {
                        MsgStatus.DELIVERED -> "  ✓✓ ${m.hopCount} hop(s)" + (m.latencyMs?.let { " · $it ms" } ?: "")
                        MsgStatus.RELAYING -> "  ✓ sent"
                        MsgStatus.QUEUED -> "  ⏳ waiting for route"
                        MsgStatus.FAILED -> "  ✗ not confirmed"
                        else -> ""
                    }
                ) else append("  · ${m.hopCount} hop(s)")
            }
            Text(
                meta, fontSize = 11.sp, modifier = Modifier.align(Alignment.End),
                color = if (m.status == MsgStatus.DELIVERED) GatewayGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
private fun MessageCard(m: MessageEntity, vm: MeshViewModel) {
    val typeColor = when (m.type) {
        "SOS", "ALERT" -> SosRed; "MEDICAL" -> Color(0xFFAD1457); "SAFE" -> GatewayGreen; "UPDATE" -> MeBlue
        else -> Color(0xFF455A64)
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (m.outgoing) MaterialTheme.colorScheme.surfaceContainerLow else Color(0xFFF1F8E9),
        ),
    ) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(m.type, typeColor)
                StatusChip(m.status)
                if (m.urgency > 0) Chip("${m.urgency}", urgencyColor(m.urgency))
                Text(
                    when {
                        m.type == "UPDATE" -> "to ${m.srcName}"
                        m.outgoing -> "to " + (if (m.dest == ANY_GATEWAY) "nearest gateway" else vm.nameOf(m.dest))
                        else -> "from ${m.srcName}"
                    },
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
                )
                Text(timeFmt.format(Date(m.timestamp)), style = MaterialTheme.typography.labelSmall)
            }
            Text(m.text, fontWeight = FontWeight.Medium)
            if (m.triage.isNotBlank()) Text("👥 " + m.triage, style = MaterialTheme.typography.bodySmall)
            if (m.rescueStatus.isNotBlank()) Text(
                "🚤 " + com.meshalive.app.mesh.RescueStatus.label(m.rescueStatus) + if (m.rescueNote.isNotBlank()) " — ${m.rescueNote}" else "",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = GatewayGreen,
            )
            val meta = buildList {
                if (m.status == MsgStatus.DELIVERED || m.status == MsgStatus.RECEIVED) add("${m.hopCount} hop(s)")
                m.latencyMs?.let { add(if (m.outgoing) "ACK in $it ms" else "$it ms") }
                if (m.lat != null && m.lng != null) add("📍 %.5f, %.5f".format(m.lat, m.lng))
            }
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (m.path.isNotBlank()) Text("Path: ${m.path}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (m.outgoing && m.status == MsgStatus.CARRIED) Text(
                "🚤 A rescue carrier is holding this SOS and will upload it when it reaches signal",
                style = MaterialTheme.typography.bodySmall, color = com.meshalive.app.ui.theme.MeBlue,
            )
            if (m.outgoing && m.status == MsgStatus.QUEUED) Text(
                "Waiting for a route — will send automatically when a phone comes in range",
                style = MaterialTheme.typography.bodySmall, color = LowBatteryOrange,
            )
        }
    }
}
