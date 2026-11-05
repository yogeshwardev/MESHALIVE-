package com.meshalive.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshalive.algorithms.Algorithm
import com.meshalive.app.sim.AlgoResult
import com.meshalive.app.sim.ScalePoint
import com.meshalive.app.sim.SimOutcome
import com.meshalive.app.sim.Simulator
import com.meshalive.app.ui.SimulatorViewModel
import com.meshalive.app.ui.components.ActiveDot
import com.meshalive.app.ui.components.DrawNode
import com.meshalive.app.ui.components.GraphCanvas
import com.meshalive.app.ui.components.SectionCard
import com.meshalive.app.ui.theme.GatewayGreen
import com.meshalive.app.ui.theme.LowBatteryOrange
import com.meshalive.app.ui.theme.MeBlue
import com.meshalive.app.ui.theme.RouteGold
import com.meshalive.app.ui.theme.SosRed
import kotlin.math.roundToInt

val AlgoColors = mapOf(
    Algorithm.FLOODING to Color(0xFFD32F2F),
    Algorithm.BFS to Color(0xFF1565C0),
    Algorithm.DIJKSTRA to Color(0xFF2E7D32),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulatorScreen(vm: SimulatorViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = ui.config
    val context = LocalContext.current
    LaunchedEffect(ui.message) {
        ui.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.consumeMessage() }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(title = "Scenario") {
            LabeledSlider("Phones (V)", c.nodes.toFloat(), 50f..1000f, "${c.nodes}") { v -> vm.update { it.copy(nodes = v.roundToInt()) } }
            LabeledSlider("Area size", c.areaSize.toFloat(), 200f..3000f, "${c.areaSize.toInt()} m") { v -> vm.update { it.copy(areaSize = (v / 50).roundToInt() * 50.0) } }
            LabeledSlider("Radio range", c.radioRange.toFloat(), 30f..400f, "${c.radioRange.toInt()} m") { v -> vm.update { it.copy(radioRange = (v / 5).roundToInt() * 5.0) } }
            LabeledSlider("Low-battery phones", c.lowBatteryPercent.toFloat(), 0f..80f, "${c.lowBatteryPercent}%") { v -> vm.update { it.copy(lowBatteryPercent = v.roundToInt()) } }
            LabeledSlider("Gateways", c.gateways.toFloat(), 1f..20f, "${c.gateways}") { v -> vm.update { it.copy(gateways = v.roundToInt()) } }
            LabeledSlider("SOS messages", c.messages.toFloat(), 1f..200f, "${c.messages}") { v -> vm.update { it.copy(messages = v.roundToInt()) } }
            OutlinedTextField(
                value = c.seed.toString(),
                onValueChange = { s -> s.toLongOrNull()?.let { seed -> vm.update { it.copy(seed = seed) } } ?: if (s.isEmpty()) vm.update { it.copy(seed = 0) } else Unit },
                label = { Text("Random seed") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::run, enabled = !ui.running, modifier = Modifier.weight(1f)) {
                    if (ui.running) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Run simulation")
                }
                OutlinedButton(onClick = vm::exportCsv) { Text("Export CSV") }
            }
        }

        ui.outcome?.let { outcome ->
            SectionCard(title = "Propagation of SOS #1") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Algorithm.entries.forEach { a ->
                        FilterChip(selected = ui.animAlgorithm == a, onClick = { vm.selectAnimation(a) },
                            label = { Text(shortName(a)) })
                    }
                }
                val result = outcome.results.first { it.algorithm == ui.animAlgorithm }
                PropagationAnimation(outcome, result)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(MeBlue to "source", GatewayGreen to "gateway", LowBatteryOrange to "low battery",
                        SosRed to "reached", RouteGold to "path").forEach { (col, txt) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(col, androidx.compose.foundation.shape.CircleShape))
                            Spacer(Modifier.width(3.dp))
                            Text(txt, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(
                    "V = ${outcome.world.nodes.size}, E = ${outcome.world.edges.size}, avg degree = ${"%.1f".format(2.0 * outcome.world.edges.size / outcome.world.nodes.size)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SectionCard(title = "Results") { ResultsTable(outcome.results) }
        }

        SectionCard(title = "Scalability (V = 100 … 1000)") {
            Button(onClick = vm::runScalability, enabled = !ui.scaleRunning, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.scaleRunning) "Running… ${ui.scaleProgress}/${Simulator.SCALE_SIZES.size}" else "Run scalability test")
            }
            if (ui.scaleRunning) LinearProgressIndicator(
                progress = { ui.scaleProgress / Simulator.SCALE_SIZES.size.toFloat() }, modifier = Modifier.fillMaxWidth(),
            )
            if (ui.scale.isNotEmpty()) {
                Text("Routing runtime (ms) vs V", fontWeight = FontWeight.SemiBold)
                LineChart(ui.scale) { it.runtimeMs }
                Text("Total transmissions vs V", fontWeight = FontWeight.SemiBold)
                LineChart(ui.scale) { it.transmissions.toDouble() }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AlgoColors.forEach { (a, col) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp, 4.dp).background(col))
                            Spacer(Modifier.width(4.dp))
                            Text(shortName(a), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(display, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun PropagationAnimation(outcome: SimOutcome, result: AlgoResult) {
    val world = outcome.world
    val area = world.config.areaSize.toFloat()
    val trace = result.trace
    val endTime = (trace.maxOfOrNull { it.time } ?: 0.0).toFloat().coerceAtLeast(1f)
    val progress = remember(outcome, result.algorithm) { Animatable(0f) }
    LaunchedEffect(outcome, result.algorithm) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 5000, easing = LinearEasing))
    }
    val now = progress.value * endTime
    val reached = HashSet<Int>()
    val active = ArrayList<ActiveDot>()
    val flightMs = 50f
    for (e in trace) {
        val t = e.time.toFloat()
        if (t <= now) reached += e.to
        else if (t - flightMs <= now && active.size < 400) active += ActiveDot(e.from, e.to, ((now - (t - flightMs)) / flightMs).coerceIn(0f, 1f), SosRed)
    }
    val source = world.sources.firstOrNull()
    val small = world.nodes.size > 300
    val nodes = world.nodes.mapIndexed { i, n ->
        val color = when {
            i == source -> MeBlue
            n.gateway -> GatewayGreen
            i in reached -> SosRed
            n.battery < 20 -> LowBatteryOrange
            else -> Color(0xFF90A4AE)
        }
        val r = when {
            i == source || n.gateway -> if (small) 4f else 5.5f
            else -> if (small) 1.6f else 2.6f
        }
        DrawNode(n.x.toFloat() / area, n.y.toFloat() / area, color, radius = r)
    }
    val done = progress.value >= 1f
    val highlight = if (done) result.firstPath.zipWithNext().toSet() else emptySet()
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        GraphCanvas(
            Modifier.fillMaxWidth().aspectRatio(1f), nodes, world.edges, highlight, RouteGold, active,
            edgeColor = Color(0xFFE0E0E0), labels = false,
        )
    }
    Text(
        "${result.algorithm.label}: ${trace.size} transmissions for this SOS · " +
            if (result.firstPath.isEmpty()) "not delivered" else "${result.firstPath.size - 1} hops",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ResultsTable(results: List<AlgoResult>) {
    val rows: List<Pair<String, (AlgoResult) -> String>> = listOf(
        "Delivered" to { r -> "${(r.deliveryRatio * 100).roundToInt()}%" },
        "Avg hops" to { r -> "%.2f".format(r.avgHops) },
        "Tx count" to { r -> "${r.transmissions}" },
        "Avg latency" to { r -> "%.0f ms".format(r.avgLatencyMs) },
        "Battery used (Σ%)" to { r -> "%.0f".format(r.batteryDrained) },
        "Min battery" to { r -> "%.1f%%".format(r.minBattery) },
        "Runtime" to { r -> "%.1f ms".format(r.runtimeMs) },
    )
    Column {
        Row {
            Text("", Modifier.weight(1.4f))
            results.forEach {
                Text(
                    shortName(it.algorithm),
                    Modifier.weight(1f), fontWeight = FontWeight.Bold, color = AlgoColors.getValue(it.algorithm), fontSize = 13.sp,
                )
            }
        }
        rows.forEachIndexed { idx, (label, f) ->
            Row(
                Modifier.fillMaxWidth().background(if (idx % 2 == 0) Color(0xFFF5F5F5) else Color.Transparent).padding(vertical = 6.dp),
            ) {
                Text(label, Modifier.weight(1.4f), fontSize = 12.sp)
                results.forEach { Text(f(it), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun LineChart(points: List<ScalePoint>, value: (ScalePoint) -> Double) {
    val xs = points.map { it.v }.distinct().sorted()
    val maxY = (points.maxOfOrNull(value) ?: 1.0).coerceAtLeast(1e-6)
    val axis = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val left = 44.dp.toPx(); val bottom = size.height - 22.dp.toPx(); val top = 8.dp.toPx(); val right = size.width - 12.dp.toPx()
        drawLine(axis, Offset(left, top), Offset(left, bottom), 2f)
        drawLine(axis, Offset(left, bottom), Offset(right, bottom), 2f)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 10.sp.toPx(); isAntiAlias = true }
        val xPaint = android.graphics.Paint(paint).apply { textAlign = android.graphics.Paint.Align.CENTER }
        fun px(v: Int) = left + (v - xs.first()).toFloat() / (xs.last() - xs.first()).coerceAtLeast(1) * (right - left)
        fun py(y: Double) = bottom - (y / maxY).toFloat() * (bottom - top)
        xs.forEach { v -> drawContext.canvas.nativeCanvas.drawText("$v", px(v), bottom + 16.dp.toPx(), xPaint) }
        for (k in 0..4) {
            val y = maxY * k / 4
            val label = if (maxY >= 100) "%.0f".format(y) else "%.2f".format(y)
            drawContext.canvas.nativeCanvas.drawText(label, 2f, py(y) + 4.dp.toPx(), paint)
            drawLine(axis.copy(alpha = 0.2f), Offset(left, py(y)), Offset(right, py(y)), 1f)
        }
        for (a in Algorithm.entries) {
            val series = points.filter { it.algorithm == a }.sortedBy { it.v }
            val color = AlgoColors.getValue(a)
            series.zipWithNext().forEach { (p, q) ->
                drawLine(color, Offset(px(p.v), py(value(p))), Offset(px(q.v), py(value(q))), 2.dp.toPx())
            }
            series.forEach { drawCircle(color, 3.5.dp.toPx(), Offset(px(it.v), py(value(it)))); drawCircle(Color.White, 1.5.dp.toPx(), Offset(px(it.v), py(value(it)))) }
        }
    }
}

private fun shortName(a: Algorithm) = when (a) {
    Algorithm.FLOODING -> "Flooding"
    Algorithm.BFS -> "BFS"
    Algorithm.DIJKSTRA -> "Dijkstra"
}
