package com.meshalive.app.sim

import com.meshalive.algorithms.Algorithm
import com.meshalive.algorithms.BfsStrategy
import com.meshalive.algorithms.CostParams
import com.meshalive.algorithms.DijkstraStrategy
import com.meshalive.algorithms.Graph
import com.meshalive.algorithms.NodeInfo
import com.meshalive.algorithms.RouteTable
import java.util.PriorityQueue
import kotlin.math.hypot
import kotlin.random.Random

data class SimConfig(
    val nodes: Int = 200,
    val areaSize: Double = 1000.0, // metres, square side
    val radioRange: Double = 120.0, // metres
    val lowBatteryPercent: Int = 20,
    val gateways: Int = 3,
    val messages: Int = 20,
    val seed: Long = 42,
    val ttl: Int = 15,
    val params: CostParams = CostParams(),
)

/** One phone placed in the simulated area. */
data class SimNode(val id: String, val x: Double, val y: Double, val battery: Double, val gateway: Boolean)

/** A single radio transmission used to animate propagation. */
data class TxEvent(val from: Int, val to: Int, val time: Double)

data class AlgoResult(
    val algorithm: Algorithm,
    val delivered: Int,
    val total: Int,
    val avgHops: Double,
    val transmissions: Long,
    val avgLatencyMs: Double,
    val batteryDrained: Double,
    val minBattery: Double,
    val runtimeMs: Double,
    /** Transmissions of the first message, for the animation. */
    val trace: List<TxEvent>,
    /** Final path (node indices) of the first message, empty if undelivered. */
    val firstPath: List<Int>,
) {
    val deliveryRatio: Double get() = if (total == 0) 0.0 else delivered.toDouble() / total
}

data class SimWorld(val config: SimConfig, val nodes: List<SimNode>, val edges: List<Pair<Int, Int>>, val sources: List<Int>)

data class SimOutcome(val world: SimWorld, val results: List<AlgoResult>)

data class ScalePoint(val v: Int, val algorithm: Algorithm, val runtimeMs: Double, val transmissions: Long)

/**
 * Offline simulator: builds a random geometric graph and runs all three routing
 * algorithms on identical copies of it. Pure Kotlin, no Android dependencies.
 */
object Simulator {
    const val TX_COST = 0.5 // battery % spent per transmission by the sender
    const val RX_COST = 0.2 // battery % spent per reception
    const val PROCESSING_MS = 2.0

    fun buildWorld(c: SimConfig): SimWorld {
        val rnd = Random(c.seed)
        val n = c.nodes.coerceAtLeast(2)
        val gatewayIdx = (0 until n).shuffled(rnd).take(c.gateways.coerceIn(0, n)).toSet()
        val nodes = (0 until n).map { i ->
            val low = rnd.nextInt(100) < c.lowBatteryPercent
            val battery = if (low) 5.0 + rnd.nextDouble() * 15 else 40.0 + rnd.nextDouble() * 60
            SimNode("n$i", rnd.nextDouble() * c.areaSize, rnd.nextDouble() * c.areaSize, battery, i in gatewayIdx)
        }
        // Grid bucketing makes edge construction ~O(V + E) instead of O(V^2).
        val cell = c.radioRange.coerceAtLeast(1.0)
        val buckets = HashMap<Long, MutableList<Int>>()
        fun key(cx: Int, cy: Int) = cx.toLong() * 1_000_003L + cy
        nodes.forEachIndexed { i, p -> buckets.getOrPut(key((p.x / cell).toInt(), (p.y / cell).toInt())) { mutableListOf() } += i }
        val edges = ArrayList<Pair<Int, Int>>()
        nodes.forEachIndexed { i, p ->
            val cx = (p.x / cell).toInt(); val cy = (p.y / cell).toInt()
            for (dx in -1..1) for (dy in -1..1) {
                buckets[key(cx + dx, cy + dy)]?.forEach { j ->
                    if (j > i && hypot(p.x - nodes[j].x, p.y - nodes[j].y) <= c.radioRange) edges += i to j
                }
            }
        }
        val candidates = (0 until n).filter { it !in gatewayIdx }.ifEmpty { (0 until n).toList() }
        val sources = List(c.messages) { candidates[rnd.nextInt(candidates.size)] }
        return SimWorld(c, nodes, edges, sources)
    }

    /** Link latency in ms: grows with distance (weaker signal => retries). */
    private fun linkLatency(world: SimWorld, a: Int, b: Int): Double {
        val p = world.nodes[a]; val q = world.nodes[b]
        return 5.0 + 45.0 * hypot(p.x - q.x, p.y - q.y) / world.config.radioRange
    }

    fun run(config: SimConfig): SimOutcome {
        val world = buildWorld(config)
        return SimOutcome(world, Algorithm.entries.map { runAlgorithm(world, it) })
    }

    fun runAlgorithm(world: SimWorld, algorithm: Algorithm): AlgoResult {
        val n = world.nodes.size
        val battery = DoubleArray(n) { world.nodes[it].battery }
        val initialTotal = battery.sum()
        val adj = Array(n) { ArrayList<Int>() }
        world.edges.forEach { (a, b) -> adj[a] += b; adj[b] += a }
        val gateways = world.nodes.indices.filter { world.nodes[it].gateway }.toSet()

        var delivered = 0
        var hopSum = 0L
        var latencySum = 0.0
        var tx = 0L
        var runtimeNs = 0L
        var trace: List<TxEvent> = emptyList()
        var firstPath: List<Int> = emptyList()

        fun alive(i: Int) = battery[i] > 0.0
        fun spend(from: Int, to: Int) {
            tx++
            battery[from] = (battery[from] - TX_COST).coerceAtLeast(0.0)
            battery[to] = (battery[to] - RX_COST).coerceAtLeast(0.0)
        }

        world.sources.forEachIndexed { msgIndex, src ->
            val events = ArrayList<TxEvent>()
            var path: List<Int> = emptyList()
            var latency = 0.0
            var ok = false

            if (algorithm == Algorithm.FLOODING) {
                val t0 = System.nanoTime()
                // Event-driven flood: every node rebroadcasts the first copy it hears,
                // to all neighbours except the sender. Duplicates are suppressed.
                val arrival = DoubleArray(n) { Double.POSITIVE_INFINITY }
                val hopsAt = IntArray(n) { Int.MAX_VALUE }
                val parent = IntArray(n) { -1 }
                val pq = PriorityQueue<Triple<Double, Int, Int>>(compareBy { it.first }) // time, node, from
                pq.add(Triple(0.0, src, -1))
                var firstGateway = -1
                while (pq.isNotEmpty()) {
                    val (t, v, from) = pq.poll()!!
                    if (arrival[v] != Double.POSITIVE_INFINITY) continue // duplicate copy
                    arrival[v] = t
                    parent[v] = from
                    hopsAt[v] = if (from == -1) 0 else hopsAt[from] + 1
                    if (v in gateways && firstGateway == -1) firstGateway = v
                    if (v in gateways || !alive(v) || hopsAt[v] >= world.config.ttl) continue
                    for (u in adj[v]) if (u != from) {
                        spend(v, u)
                        val at = t + linkLatency(world, v, u) + PROCESSING_MS
                        events += TxEvent(v, u, at)
                        if (arrival[u] == Double.POSITIVE_INFINITY) pq.add(Triple(at, u, v))
                    }
                }
                runtimeNs += System.nanoTime() - t0
                if (firstGateway != -1) {
                    ok = true
                    latency = arrival[firstGateway]
                    path = generateSequence(firstGateway) { parent[it].takeIf { p -> p >= 0 } }.toList().reversed()
                }
            } else {
                // Routing table is recomputed per message from the current LSAs,
                // so battery drained by earlier messages influences Dijkstra.
                val graph = Graph()
                for (i in 0 until n) if (alive(i)) graph.addNode(NodeInfo("$i", battery[i].toInt(), i in gateways))
                for ((a, b) in world.edges) if (alive(a) && alive(b)) graph.addEdge("$a", "$b", 2 * linkLatency(world, a, b))
                val targets = gateways.filter { alive(it) }.map { "$it" }.toSet()
                val t0 = System.nanoTime()
                val table: RouteTable = if (algorithm == Algorithm.BFS) BfsStrategy.computeRoutes(graph, targets)
                else DijkstraStrategy(world.config.params).computeRoutes(graph, targets)
                runtimeNs += System.nanoTime() - t0
                val p = table.pathFrom("$src").map { it.toInt() }
                if (p.isNotEmpty() && p.size - 1 <= world.config.ttl) {
                    var t = 0.0
                    for (k in 0 until p.size - 1) {
                        spend(p[k], p[k + 1])
                        t += linkLatency(world, p[k], p[k + 1]) + PROCESSING_MS
                        events += TxEvent(p[k], p[k + 1], t)
                    }
                    ok = true; latency = t; path = p
                }
            }

            if (ok) {
                delivered++
                hopSum += path.size - 1
                latencySum += latency
            }
            if (msgIndex == 0) { trace = events; firstPath = path }
        }

        return AlgoResult(
            algorithm = algorithm,
            delivered = delivered,
            total = world.sources.size,
            avgHops = if (delivered == 0) 0.0 else hopSum.toDouble() / delivered,
            transmissions = tx,
            avgLatencyMs = if (delivered == 0) 0.0 else latencySum / delivered,
            batteryDrained = initialTotal - battery.sum(),
            minBattery = battery.minOrNull() ?: 0.0,
            runtimeMs = runtimeNs / 1e6,
            trace = trace,
            firstPath = firstPath,
        )
    }

    val SCALE_SIZES = listOf(100, 250, 500, 750, 1000)

    fun scalability(base: SimConfig, onProgress: (Int) -> Unit = {}): List<ScalePoint> {
        val out = ArrayList<ScalePoint>()
        // Warm-up so JIT compilation does not distort the first timings.
        val warm = buildWorld(base.copy(nodes = 100, messages = 5))
        Algorithm.entries.forEach { runAlgorithm(warm, it) }
        SCALE_SIZES.forEachIndexed { i, v ->
            val world = buildWorld(base.copy(nodes = v))
            for (a in Algorithm.entries) {
                val r = runAlgorithm(world, a)
                out += ScalePoint(v, a, r.runtimeMs, r.transmissions)
            }
            onProgress(i + 1)
        }
        return out
    }

    fun toCsv(outcome: SimOutcome?, scale: List<ScalePoint>): String = buildString {
        if (outcome != null) {
            val c = outcome.world.config
            appendLine("# MeshAlive simulation: nodes=${c.nodes} area=${c.areaSize} range=${c.radioRange} lowBattery%=${c.lowBatteryPercent} gateways=${c.gateways} messages=${c.messages} seed=${c.seed}")
            appendLine("algorithm,delivery_ratio,avg_hops,transmissions,avg_latency_ms,battery_drained,min_battery,runtime_ms")
            outcome.results.forEach {
                appendLine("${it.algorithm.label},${"%.3f".format(it.deliveryRatio)},${"%.2f".format(it.avgHops)},${it.transmissions},${"%.1f".format(it.avgLatencyMs)},${"%.1f".format(it.batteryDrained)},${"%.1f".format(it.minBattery)},${"%.3f".format(it.runtimeMs)}")
            }
        }
        if (scale.isNotEmpty()) {
            appendLine()
            appendLine("# Scalability")
            appendLine("V,algorithm,runtime_ms,transmissions")
            scale.forEach { appendLine("${it.v},${it.algorithm.label},${"%.3f".format(it.runtimeMs)},${it.transmissions}") }
        }
    }
}
