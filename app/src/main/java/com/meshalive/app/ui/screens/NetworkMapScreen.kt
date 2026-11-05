package com.meshalive.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshalive.app.mesh.Flight
import com.meshalive.app.mesh.LogEvent
import com.meshalive.app.mesh.LogKind
import com.meshalive.app.mesh.PacketType
import com.meshalive.app.ui.MeshViewModel
import com.meshalive.app.ui.components.ActiveDot
import com.meshalive.app.ui.components.DrawNode
import com.meshalive.app.ui.components.ForceLayout
import com.meshalive.app.ui.components.GraphCanvas
import com.meshalive.app.ui.components.InfoRow
import com.meshalive.app.ui.components.SectionCard
import com.meshalive.app.ui.theme.GatewayGreen
import com.meshalive.app.ui.theme.LowBatteryOrange
import com.meshalive.app.ui.theme.MeBlue
import com.meshalive.app.ui.theme.NodeGrey
import com.meshalive.app.ui.theme.RouteGold
import com.meshalive.app.ui.theme.SosRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FLIGHT_MS = 900L

fun flightColor(t: PacketType) = when (t) {
    PacketType.SOS -> SosRed
    PacketType.MEDICAL -> Color(0xFFAD1457)
    PacketType.CHAT -> MeBlue
    PacketType.ACK -> GatewayGreen
    else -> NodeGrey
}

@Composable
fun NetworkMapScreen(vm: MeshViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val log by vm.log.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Live map") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Routing log (${log.size})") })
        }
        if (tab == 0) LiveMap(vm) else RoutingLog(log)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveMap(vm: MeshViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val graph = state.graph
    val ids = remember(graph) { graph.nodeIds.sorted() }
    val index = remember(ids) { ids.withIndex().associate { it.value to it.index } }
    val edges = remember(graph, index) {
        graph.edges().mapNotNull { (u, v, _) -> val a = index[u]; val b = index[v]; if (a != null && b != null) a to b else null }
    }
    val positions = remember(ids, edges) { ForceLayout.layout(ids, edges) }
    val route = state.routeToGateway
    val highlight = remember(route, index) {
        route.zipWithNext().mapNotNull { (u, v) -> val a = index[u]; val b = index[v]; if (a != null && b != null) a to b else null }.toSet()
    }

    // Live packets: every real send/receive on this phone becomes a moving dot.
    val flights = remember { mutableStateListOf<Flight>() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { vm.flights.collect { flights += it.copy(at = System.currentTimeMillis()) } }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { }
            now = System.currentTimeMillis()
            flights.removeAll { now - it.at > FLIGHT_MS }
        }
    }
    val active = flights.mapNotNull { f ->
        val a = index[f.from]; val b = index[f.to]
        if (a == null || b == null) null
        else ActiveDot(a, b, ((now - f.at).toFloat() / FLIGHT_MS).coerceIn(0f, 1f), flightColor(f.type), big = true)
    }

    val nodes = ids.mapIndexed { i, id ->
        val info = graph.node(id)
        val color = when {
            info?.hasInternet == true -> GatewayGreen
            id == vm.myId -> MeBlue
            (info?.battery ?: 100) < 20 -> LowBatteryOrange
            else -> NodeGrey
        }
        val name = if (id == vm.myId) "Me" else state.names[id] ?: id.take(5)
        DrawNode(positions[i].first, positions[i].second, color, "$name ${info?.battery ?: "?"}%", radius = 10f, ring = id == vm.myId)
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.95f)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .background(Color(0xFFFCFCFC), RoundedCornerShape(16.dp)),
        ) {
            if (ids.size <= 1) {
                Text(
                    "Only this phone so far.\nOpen MeshAlive on other phones nearby — they will appear here automatically.",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GraphCanvas(Modifier.fillMaxWidth().aspectRatio(0.95f), nodes, edges, highlight, RouteGold, active)
            Text(
                "V=${graph.size}  E=${graph.edgeCount()}",
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(MeBlue, "Me"); Legend(GatewayGreen, "Gateway"); Legend(LowBatteryOrange, "Battery < 20%")
            Legend(RouteGold, "Route"); Legend(SosRed, "SOS packet"); Legend(GatewayGreen, "ACK")
        }

        SectionCard(title = "Phones in radio range") {
            Text(
                "Turn a link off to pretend that phone is out of range — useful in a demo room where every phone can hear every other. Watch the route re-compute live.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.peers.isEmpty()) Text("Searching for nearby MeshAlive phones…", style = MaterialTheme.typography.bodyMedium)
            state.peers.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(if (p.connected) GatewayGreen else if (p.blocked) SosRed else LowBatteryOrange, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                p.blocked -> "Link cut (simulated out of range)"
                                p.connected -> "Connected" + (p.rttMs?.let { " · RTT ${it.toInt()} ms" } ?: "")
                                else -> "Connecting…"
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = !p.blocked, onCheckedChange = { vm.setLinkBlocked(p.nodeId, !it) })
                }
            }
        }

        SectionCard(title = "Link-state database") {
            InfoRow("Phones (V)", "${graph.size}")
            InfoRow("Links (E)", "${graph.edgeCount()}")
            InfoRow("Gateways", "${graph.gateways().size}")
            InfoRow("Algorithm", settings.algorithm.label)
            InfoRow("Relayed for others", "${state.relayedCount}")
            Text(
                "Route to gateway: " + if (route.isEmpty()) "none yet" else route.joinToString(" → ") { vm.nameOf(it) },
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun RoutingLog(log: List<LogEvent>) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFF101418)),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (log.isEmpty()) item {
            Text("Waiting for mesh events…", color = Color(0xFF9E9E9E), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        items(log, key = { it.time.toString() + it.text.hashCode() + it.kind }) { e ->
            Row {
                Text(fmt.format(Date(e.time)), color = Color(0xFF78909C), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    e.kind.name.padEnd(7),
                    color = kindColor(e.kind), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Text(e.text, color = Color(0xFFE0E0E0), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

private fun kindColor(k: LogKind) = when (k) {
    LogKind.LINK -> Color(0xFF4FC3F7)
    LogKind.LSA -> Color(0xFFB39DDB)
    LogKind.ROUTE -> Color(0xFFFFD54F)
    LogKind.DELIVER -> Color(0xFF81C784)
    LogKind.ACK -> Color(0xFFA5D6A7)
    LogKind.DROP -> Color(0xFFE57373)
    LogKind.STORE -> Color(0xFFFFB74D)
    LogKind.CLOUD -> Color(0xFF80DEEA)
    LogKind.INFO -> Color(0xFFBDBDBD)
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.size(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}
