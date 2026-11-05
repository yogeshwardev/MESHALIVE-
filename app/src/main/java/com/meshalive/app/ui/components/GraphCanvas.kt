package com.meshalive.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** A packet in flight along edge from -> to, at progress t in [0, 1]. */
data class ActiveDot(val from: Int, val to: Int, val t: Float, val color: Color, val big: Boolean = false)

data class DrawNode(val x: Float, val y: Float, val color: Color, val label: String? = null, val radius: Float = 10f, val ring: Boolean = false)

/**
 * Generic graph renderer. Node positions are normalised to [0, 1].
 * [highlight] edges are drawn thick (the route), [active] edges are "packets in flight".
 */
@Composable
fun GraphCanvas(
    modifier: Modifier,
    nodes: List<DrawNode>,
    edges: List<Pair<Int, Int>>,
    highlight: Set<Pair<Int, Int>> = emptySet(),
    highlightColor: Color = Color(0xFFFFB300),
    active: List<ActiveDot> = emptyList(),
    edgeColor: Color = Color(0xFFB0BEC5),
    labels: Boolean = true,
) {
    Canvas(modifier) {
        // DrawNode.radius is in dp; everything else is scaled by density too.
        val pad = 14.dp.toPx()
        fun pos(i: Int) = Offset(pad + nodes[i].x * (size.width - 2 * pad), pad + nodes[i].y * (size.height - 2 * pad))
        val edgeWidth = if (nodes.size > 300) 0.4.dp.toPx() else 1.dp.toPx()
        for ((a, b) in edges) {
            if (a !in nodes.indices || b !in nodes.indices) continue
            drawLine(edgeColor, pos(a), pos(b), strokeWidth = edgeWidth)
        }
        for ((a, b) in highlight) {
            if (a !in nodes.indices || b !in nodes.indices) continue
            drawLine(highlightColor, pos(a), pos(b), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
        }
        for (d in active) {
            if (d.from !in nodes.indices || d.to !in nodes.indices) continue
            val pa = pos(d.from); val pb = pos(d.to)
            val p = Offset(pa.x + (pb.x - pa.x) * d.t, pa.y + (pb.y - pa.y) * d.t)
            drawLine(d.color.copy(alpha = 0.5f), pa, p, strokeWidth = (if (d.big) 3.0 else 1.5).dp.toPx())
            if (d.big) drawCircle(d.color.copy(alpha = 0.25f), radius = 11.dp.toPx(), center = p)
            drawCircle(d.color, radius = (if (d.big) 6.0 else 2.5).dp.toPx(), center = p)
        }
        nodes.forEachIndexed { i, n ->
            val c = pos(i)
            val r = n.radius.dp.toPx()
            drawCircle(n.color, radius = r, center = c)
            if (n.ring) drawCircle(Color.Black, radius = r + 2.dp.toPx(), center = c, style = Stroke(width = 1.5.dp.toPx()))
            if (labels && n.label != null) drawLabel(n.label, c.x, c.y + r + 13.dp.toPx())
        }
    }
}

private fun DrawScope.drawLabel(text: String, x: Float, y: Float) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 11.sp.toPx()
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

/**
 * Fruchterman-Reingold force-directed layout for the live mesh (no GPS needed).
 * O(iterations * V^2), fine for the tens of phones in a real mesh.
 */
object ForceLayout {
    fun layout(ids: List<String>, edges: List<Pair<Int, Int>>, iterations: Int = 150): List<Pair<Float, Float>> {
        val n = ids.size
        if (n == 0) return emptyList()
        if (n == 1) return listOf(0.5f to 0.5f)
        val x = DoubleArray(n); val y = DoubleArray(n)
        ids.forEachIndexed { i, id ->
            val r = Random(id.hashCode())
            x[i] = r.nextDouble(); y[i] = r.nextDouble()
        }
        val k = sqrt(1.0 / n)
        var temp = 0.1
        repeat(iterations) {
            val dx = DoubleArray(n); val dy = DoubleArray(n)
            for (i in 0 until n) for (j in i + 1 until n) {
                val ddx = x[i] - x[j]; val ddy = y[i] - y[j]
                val d = max(sqrt(ddx * ddx + ddy * ddy), 0.01)
                val f = k * k / d
                dx[i] += ddx / d * f; dy[i] += ddy / d * f
                dx[j] -= ddx / d * f; dy[j] -= ddy / d * f
            }
            for ((a, b) in edges) {
                val ddx = x[a] - x[b]; val ddy = y[a] - y[b]
                val d = max(sqrt(ddx * ddx + ddy * ddy), 0.01)
                val f = d * d / k
                dx[a] -= ddx / d * f; dy[a] -= ddy / d * f
                dx[b] += ddx / d * f; dy[b] += ddy / d * f
            }
            for (i in 0 until n) {
                val d = max(sqrt(dx[i] * dx[i] + dy[i] * dy[i]), 1e-9)
                x[i] += dx[i] / d * min(d, temp); y[i] += dy[i] / d * min(d, temp)
            }
            temp *= 0.97
        }
        val minX = x.min(); val maxX = x.max(); val minY = y.min(); val maxY = y.max()
        val sx = max(maxX - minX, 1e-6); val sy = max(maxY - minY, 1e-6)
        return (0 until n).map { (((x[it] - minX) / sx) * 0.8 + 0.1).toFloat() to (((y[it] - minY) / sy) * 0.8 + 0.1).toFloat() }
    }
}
