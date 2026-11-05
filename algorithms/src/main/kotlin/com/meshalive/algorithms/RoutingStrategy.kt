package com.meshalive.algorithms

import java.util.ArrayDeque
import java.util.PriorityQueue

/**
 * Result of a single-source (or multi-source) shortest path computation toward a
 * set of targets. For every node that can reach a target: the neighbour to
 * forward to, the path cost and the hop count.
 */
class RouteTable(
    val nextHop: Map<String, String>,
    val cost: Map<String, Double>,
    val hops: Map<String, Int>,
    val targets: Set<String>,
) {
    fun reachable(node: String): Boolean = node in cost

    /** Reconstructs the path node -> ... -> target by following next hops. */
    fun pathFrom(node: String): List<String> {
        if (!reachable(node)) return emptyList()
        val path = mutableListOf(node)
        var cur = node
        var guard = 0
        while (cur !in targets) {
            cur = nextHop[cur] ?: return emptyList()
            path += cur
            if (++guard > cost.size + 1) return emptyList()
        }
        return path
    }
}

/** Weights of the battery-aware edge cost. */
data class CostParams(
    val alpha: Double = 1.0,
    val beta: Double = 50.0,
    val gamma: Double = 1.0,
) {
    /**
     * Cost of sending u -> v: alpha * normalisedRTT + beta * (1 / max(battery_v, 5)) + gamma.
     * normalisedRTT = rtt / maxRtt in [0, 1]. The +gamma makes every hop cost
     * something, so the cost is strictly positive (a precondition of Dijkstra).
     */
    fun edgeCost(rttMs: Double, maxRtt: Double, batteryV: Int): Double {
        val normRtt = if (maxRtt > 0) rttMs / maxRtt else 0.0
        return alpha * normRtt + beta * (1.0 / maxOf(batteryV, 5)) + gamma
    }
}

/** Common interface for the three routing algorithms; selectable in Settings. */
interface RoutingStrategy {
    val name: String

    /**
     * The neighbours of [self] a packet for [dest] should be forwarded to.
     * [dest] is a node id or [ANY_GATEWAY]. [cameFrom] is the neighbour that
     * delivered the packet to us (null if we originated it).
     * An empty list means "no route right now" -> store and forward.
     */
    fun nextHops(graph: Graph, self: String, dest: String, cameFrom: String?): List<String>

    /** Full path [self .. target] the packet would take, or empty (for display). */
    fun route(graph: Graph, self: String, dest: String): List<String> = emptyList()
}

fun targetsFor(graph: Graph, dest: String): Set<String> =
    if (dest == ANY_GATEWAY) graph.gateways() else if (graph.contains(dest)) setOf(dest) else emptySet()

/** 1. Flooding: forward to every neighbour except the one it came from. O(deg) per node. */
object FloodingStrategy : RoutingStrategy {
    override val name = "Flooding"

    override fun nextHops(graph: Graph, self: String, dest: String, cameFrom: String?): List<String> =
        graph.neighbours(self).filter { it != cameFrom }

    override fun route(graph: Graph, self: String, dest: String): List<String> =
        BfsStrategy.route(graph, self, dest)
}

/** 2. BFS: fewest hops to the nearest target. Multi-source BFS from all targets, O(V + E). */
object BfsStrategy : RoutingStrategy {
    override val name = "BFS"

    fun computeRoutes(graph: Graph, targets: Set<String>): RouteTable {
        val next = HashMap<String, String>()
        val dist = HashMap<String, Double>()
        val hops = HashMap<String, Int>()
        val queue = ArrayDeque<String>()
        for (t in targets) if (graph.contains(t)) {
            hops[t] = 0; dist[t] = 0.0; queue.add(t)
        }
        while (queue.isNotEmpty()) {
            val v = queue.poll()
            val h = hops.getValue(v)
            for (u in graph.neighbours(v)) {
                if (u !in hops) {
                    hops[u] = h + 1
                    dist[u] = (h + 1).toDouble()
                    next[u] = v // u forwards toward v
                    queue.add(u)
                }
            }
        }
        return RouteTable(next, dist, hops, targets)
    }

    override fun nextHops(graph: Graph, self: String, dest: String, cameFrom: String?): List<String> {
        val table = computeRoutes(graph, targetsFor(graph, dest))
        return listOfNotNull(table.nextHop[self])
    }

    override fun route(graph: Graph, self: String, dest: String): List<String> =
        computeRoutes(graph, targetsFor(graph, dest)).pathFrom(self)
}

/**
 * 3. Battery-aware Dijkstra with a binary min-heap: O((V + E) log V).
 *
 * "Nearest gateway" trick: imagine a virtual super-node S joined to every
 * gateway with cost 0. Running Dijkstra once from S over reversed edges gives
 * every node its cost and next hop toward its nearest gateway in a single run.
 * Seeding the heap with all gateways at distance 0 is exactly that.
 */
class DijkstraStrategy(var params: CostParams = CostParams()) : RoutingStrategy {
    override val name = "Battery-aware Dijkstra"

    fun computeRoutes(graph: Graph, targets: Set<String>): RouteTable {
        val maxRtt = graph.maxRtt()
        val dist = HashMap<String, Double>()
        val hops = HashMap<String, Int>()
        val next = HashMap<String, String>()
        val settled = HashSet<String>()
        // Heap entries are (distance, node). Lazy deletion instead of decrease-key.
        val heap = PriorityQueue<Pair<Double, String>>(compareBy { it.first })

        // Super-node S -> every gateway with cost 0.
        for (t in targets) if (graph.contains(t)) {
            dist[t] = 0.0; hops[t] = 0; heap.add(0.0 to t)
        }
        while (heap.isNotEmpty()) {
            val (d, v) = heap.poll()!!
            if (!settled.add(v)) continue // stale entry
            if (d > dist.getValue(v)) continue
            val batteryV = graph.node(v)?.battery ?: 100
            // Reverse relaxation: for neighbour u, the forward edge is u -> v,
            // and its cost depends on the battery of the receiver v.
            for (u in graph.neighbours(v)) {
                if (u in settled) continue
                val w = params.edgeCost(graph.rtt(u, v) ?: 0.0, maxRtt, batteryV)
                val nd = d + w
                if (nd < (dist[u] ?: Double.POSITIVE_INFINITY)) {
                    dist[u] = nd
                    hops[u] = hops.getValue(v) + 1
                    next[u] = v
                    heap.add(nd to u)
                }
            }
        }
        return RouteTable(next, dist, hops, targets)
    }

    override fun nextHops(graph: Graph, self: String, dest: String, cameFrom: String?): List<String> {
        val table = computeRoutes(graph, targetsFor(graph, dest))
        return listOfNotNull(table.nextHop[self])
    }

    override fun route(graph: Graph, self: String, dest: String): List<String> =
        computeRoutes(graph, targetsFor(graph, dest)).pathFrom(self)
}

enum class Algorithm(val label: String) {
    FLOODING("Flooding"),
    BFS("BFS (fewest hops)"),
    DIJKSTRA("Battery-aware Dijkstra");

    fun strategy(params: CostParams = CostParams()): RoutingStrategy = when (this) {
        FLOODING -> FloodingStrategy
        BFS -> BfsStrategy
        DIJKSTRA -> DijkstraStrategy(params)
    }
}
