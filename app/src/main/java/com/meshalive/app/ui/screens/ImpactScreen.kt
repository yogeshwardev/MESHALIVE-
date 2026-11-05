package com.meshalive.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshalive.app.data.Evidence
import com.meshalive.app.sim.PlanResult
import com.meshalive.app.ui.CurvePoint
import com.meshalive.app.ui.MeshViewModel
import com.meshalive.app.ui.SimulatorViewModel
import com.meshalive.app.ui.components.SectionCard
import com.meshalive.app.ui.theme.HeroGradient
import com.meshalive.app.ui.theme.MeBlue
import com.meshalive.app.ui.theme.SafeGreen
import com.meshalive.app.ui.theme.SosRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ImpactScreen(vm: MeshViewModel, simVm: SimulatorViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Evidence", maxLines = 1) })
            Tab(tab == 1, { tab = 1 }, text = { Text("Planner", maxLines = 1) })
            Tab(tab == 2, { tab = 2 }, text = { Text("Routing", maxLines = 1) })
        }
        when (tab) {
            0 -> EvidenceTab(vm)
            1 -> PlannerTab(simVm)
            else -> SimulatorScreen(simVm)
        }
    }
}

// =============================================================================== evidence

@Composable
private fun EvidenceTab(vm: MeshViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val stats by vm.fieldStats.collectAsStateWithLifecycle()
    val e = Evidence.summarize(messages, inbox, stats)
    val since = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(e.since))
    val empty = e.sosSent == 0 && e.receivedAsGateway == 0 && e.relayed == 0

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.fillMaxWidth().background(HeroGradient, RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("FIELD-TRIAL EVIDENCE", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
            Text(
                if (e.sosDelivered > 0) "${e.sosDelivered} SOS delivered through the mesh" else "Recorded on this phone",
                color = Color.White, style = MaterialTheme.typography.headlineSmall,
            )
            Text("Real measurements since $since — no simulation", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
        }

        StatGrid(
            listOf(
                Stat("${e.sosDelivered}/${e.sosSent}", "SOS confirmed delivered", if (e.sosSent > 0) "${e.deliveryRate.roundToInt()}% success" else null, SafeGreen),
                Stat(e.medianDeliveryMs?.let { Evidence.secs(it) } ?: "–", "Median round trip", "SOS → gateway → back", MeBlue),
                Stat("${e.maxHops}", "Most hops achieved", if (e.avgHops > 0) "avg ${"%.1f".format(e.avgHops)}" else null, SosRed),
                Stat("${e.relayed}", "Relayed for others", "this phone helped", Color(0xFF7C3AED)),
                Stat("${e.peersMet}", "Phones met", "distinct devices", MeBlue),
                Stat("${e.receivedAsGateway}", "SOS received as gateway", "${e.peopleInReceivedSos} people", SafeGreen),
                Stat("${e.rescueUpdatesReceived}", "Rescue replies received", "control room → victim", SosRed),
                Stat("${e.carrierHandoffs}", "Rescue-carrier handoffs", "${e.alertsReceived} alerts received", Color(0xFF0E7490)),
            )
        )

        if (empty) SectionCard(title = "How to run a field test") {
            Text("1. Install MeshAlive on 3+ phones. Turn on airplane mode, then Bluetooth, on all but one.", style = MaterialTheme.typography.bodyMedium)
            Text("2. Keep one phone online as the gateway. Spread the others 10–30 m apart (or cut links on the Network tab).", style = MaterialTheme.typography.bodyMedium)
            Text("3. Send SOS from the farthest phone. Every delivery, hop count and round-trip time is recorded here.", style = MaterialTheme.typography.bodyMedium)
            Text("4. Export the report and attach it to your submission or pilot review.", style = MaterialTheme.typography.bodyMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { vm.exportEvidence() }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export report")
            }
            OutlinedButton(onClick = { vm.resetFieldStats() }, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Text("Reset")
            }
        }
        Text(
            "The report (Markdown) lists every SOS with status, hops, round-trip time and path, plus totals.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class Stat(val value: String, val label: String, val sub: String?, val color: Color)

@Composable
private fun StatGrid(stats: List<Stat>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.chunked(2).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { s ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)).padding(16.dp),
                    ) {
                        Box(Modifier.size(8.dp).background(s.color, CircleShape))
                        Spacer(Modifier.height(8.dp))
                        Text(s.value, style = MaterialTheme.typography.headlineMedium, color = s.color)
                        Text(s.label, style = MaterialTheme.typography.titleSmall)
                        if (s.sub != null) Text(s.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// =============================================================================== planner

@Composable
private fun PlannerTab(vm: SimulatorViewModel) {
    val plan by vm.plan.collectAsStateWithLifecycle()
    val p = plan.input
    LaunchedEffect(Unit) { if (plan.result == null) vm.runPlan() }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Plan a district rollout", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Enter an area and the number of people with the app. MeshAlive's real routing engine places the phones, links those within radio range and checks who can reach the control room.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard(title = "Assumptions") {
            PlanSlider("Area", p.areaKm2.toFloat(), 0.25f..25f, "%.2f km²".format(p.areaKm2)) { v -> vm.updatePlan { it.copy(areaKm2 = (v * 4).roundToInt() / 4.0) } }
            PlanSlider("People with the app", p.appUsers.toFloat(), 100f..10000f, "%,d".format(p.appUsers)) { v -> vm.updatePlan { it.copy(appUsers = (v / 50).roundToInt() * 50) } }
            PlanSlider("Phones that still have signal", p.gateways.toFloat(), 0f..20f, "${p.gateways}") { v -> vm.updatePlan { it.copy(gateways = v.roundToInt()) } }
            PlanSlider("Rescue boats / teams with a phone", p.carriers.toFloat(), 0f..20f, "${p.carriers}") { v -> vm.updatePlan { it.copy(carriers = v.roundToInt()) } }
            PlanSlider("Phone-to-phone range", p.rangeM.toFloat(), 15f..100f, "${p.rangeM.roundToInt()} m") { v -> vm.updatePlan { it.copy(rangeM = v.roundToInt().toDouble()) } }
            PlanSlider("People per phone (household)", p.householdSize.toFloat(), 1f..8f, "%.1f".format(p.householdSize)) { v -> vm.updatePlan { it.copy(householdSize = (v * 2).roundToInt() / 2.0) } }
            Text(
                "Density: ${"%,d".format((p.appUsers / p.areaKm2).roundToInt())} app users per km²",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = vm::runPlan, enabled = !plan.running, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                if (plan.running) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Calculate coverage") }
            }
        }

        plan.result?.let { r ->
            CoverageCard(r)
            if (plan.curve.isNotEmpty()) SectionCard(title = "How many people need the app?") {
                AdoptionChart(plan.curve, r.input.appUsers)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(MeBlue, "Mesh only")
                    LegendDot(SafeGreen, "Mesh + rescue carriers")
                }
                Text(
                    "Coverage rises sharply once enough neighbours have the app: that is the adoption target for the district awareness drive.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CostCard(r)
        }
    }
}

@Composable
private fun PlanSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(shown, style = MaterialTheme.typography.titleSmall)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun CoverageCard(r: PlanResult) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                val trackColor = MaterialTheme.colorScheme.surfaceVariant
                Canvas(Modifier.size(132.dp)) {
                    val w = 11.dp.toPx()
                    val gap = 15.dp.toPx()
                    val outer = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                    val inner = androidx.compose.ui.geometry.Size(size.width - w - 2 * gap, size.height - w - 2 * gap)
                    val oTL = Offset(w / 2, w / 2)
                    val iTL = Offset(w / 2 + gap, w / 2 + gap)
                    drawArc(trackColor, -90f, 360f, false, oTL, outer, style = Stroke(w))
                    drawArc(SafeGreen, -90f, (360 * r.withCarriersPct / 100).toFloat(), false, oTL, outer, style = Stroke(w, cap = StrokeCap.Round))
                    drawArc(trackColor, -90f, 360f, false, iTL, inner, style = Stroke(w))
                    drawArc(MeBlue, -90f, (360 * r.meshPct / 100).toFloat(), false, iTL, inner, style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${r.withCarriersPct.roundToInt()}%", style = MaterialTheme.typography.headlineSmall, color = SafeGreen)
                    Text("reachable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("%,d people".format(r.peopleCovered), style = MaterialTheme.typography.headlineSmall)
                Text("of %,d can get an SOS to the control room".format(r.peopleTotal), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LegendDot(MeBlue, "Mesh alone: ${r.meshPct.roundToInt()}%")
                LegendDot(SafeGreen, "With rescue carriers: ${r.withCarriersPct.roundToInt()}%")
                Text("${r.clusters} phone clusters · ${"%.1f".format(r.avgNeighbours)} neighbours each",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AdoptionChart(points: List<CurvePoint>, current: Int) {
    val axis = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val left = 36.dp.toPx(); val bottom = size.height - 24.dp.toPx(); val top = 8.dp.toPx(); val right = size.width - 8.dp.toPx()
        val maxU = points.maxOf { it.users }.toFloat()
        fun px(u: Int) = left + u / maxU * (right - left)
        fun py(pct: Double) = bottom - (pct / 100.0).toFloat() * (bottom - top)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 10.sp.toPx(); isAntiAlias = true }
        for (k in 0..4) {
            val y = py(k * 25.0)
            drawLine(axis.copy(alpha = 0.25f), Offset(left, y), Offset(right, y), 1f)
            drawContext.canvas.nativeCanvas.drawText("${k * 25}%", 0f, y + 4.dp.toPx(), paint)
        }
        val xPaint = android.graphics.Paint(paint).apply { textAlign = android.graphics.Paint.Align.CENTER }
        listOf(points.first(), points[points.size / 2], points.last()).forEach {
            drawContext.canvas.nativeCanvas.drawText("%,d".format(it.users), px(it.users), size.height - 4.dp.toPx(), xPaint)
        }
        drawLine(SosRed.copy(alpha = 0.5f), Offset(px(current), top), Offset(px(current), bottom), 2.dp.toPx())
        for ((sel, color) in listOf<Pair<(CurvePoint) -> Double, Color>>({ it: CurvePoint -> it.meshPct } to MeBlue, { it: CurvePoint -> it.carriersPct } to SafeGreen)) {
            points.zipWithNext().forEach { (a, b) ->
                drawLine(color, Offset(px(a.users), py(sel(a))), Offset(px(b.users), py(sel(b))), 3.dp.toPx(), cap = StrokeCap.Round)
            }
            points.forEach { drawCircle(color, 4.dp.toPx(), Offset(px(it.users), py(sel(it)))) }
        }
    }
    Text("Red line: your current estimate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
}

@Composable
private fun CostCard(r: PlanResult) {
    SectionCard(title = "Cost and who pays") {
        CostRow("Citizens", "₹0", "Free app on the phone they already own. No SIM, data or account needed for the mesh.")
        CostRow("Rescue carriers", "₹0", "The phones NDRF/SDRF crews, boat operators and ambulance drivers already carry.")
        CostRow("Control room", "₹0", "Runs in any browser on an existing control-room PC.")
        CostRow("Rollout", "District", "Awareness drive and volunteer training through the District Disaster Management Authority.")
        Text(
            "Scales by district: the same app and dashboard work anywhere. Enter another district's area and expected users above to plan it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CostRow(who: String, cost: String, how: String) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(who, style = MaterialTheme.typography.titleSmall)
            Text(how, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text(cost, style = MaterialTheme.typography.titleMedium, color = SafeGreen)
    }
}
