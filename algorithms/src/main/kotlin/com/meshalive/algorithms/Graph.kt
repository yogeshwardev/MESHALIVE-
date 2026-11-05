package com.meshalive.algorithms

/** Destination value meaning "deliver to whichever gateway is nearest". */
const val ANY_GATEWAY = "ANY_GATEWAY"

/** A phone in the mesh as known from its Link-State Advertisement. */
data class NodeInfo(
    val id: String,
    val battery: Int = 100,
    val hasInternet: Boolean = false,
)

/**
 * Undirected weighted graph of the mesh. Edge weight is the smoothed round-trip
 * time (ms) of the link. Adjacency list representation: O(V + E) space.
 */
class Graph {
    private val nodeMap = LinkedHashMap<String, NodeInfo>()
    private val adj = LinkedHashMap<String, LinkedHashMap<String, Double>>()

    val nodes: Collection<NodeInfo> get() = nodeMap.values
    val nodeIds: Set<String> get() = nodeMap.keys
    val size: Int get() = nodeMap.size

    fun addNode(node: NodeInfo): Graph {
        nodeMap[node.id] = node
        adj.getOrPut(node.id) { LinkedHashMap() }
        return this
    }

    fun addNode(id: String, battery: Int = 100, hasInternet: Boolean = false): Graph =
        addNode(NodeInfo(id, battery, hasInternet))

    /** Adds an undirected link. Unknown endpoints are created with default info. */
    fun addEdge(u: String, v: String, rttMs: Double = 1.0): Graph {
        if (u == v) return this
        if (u !in nodeMap) addNode(u)
        if (v !in nodeMap) addNode(v)
        adj.getValue(u)[v] = rttMs
        adj.getValue(v)[u] = rttMs
        return this
    }

    fun removeNode(id: String) {
        nodeMap.remove(id)
        adj.remove(id)?.keys?.forEach { adj[it]?.remove(id) }
    }

    fun node(id: String): NodeInfo? = nodeMap[id]
    fun contains(id: String): Boolean = id in nodeMap
    fun neighbours(id: String): Set<String> = adj[id]?.keys ?: emptySet()
    fun rtt(u: String, v: String): Double? = adj[u]?.get(v)

    fun gateways(): Set<String> = nodeMap.values.filter { it.hasInternet }.map { it.id }.toSet()

    fun edges(): List<Triple<String, String, Double>> {
        val out = ArrayList<Triple<String, String, Double>>()
        for ((u, m) in adj) for ((v, w) in m) if (u < v) out += Triple(u, v, w)
        return out
    }

    fun edgeCount(): Int = adj.values.sumOf { it.size } / 2

    fun maxRtt(): Double = adj.values.maxOfOrNull { m -> m.values.maxOrNull() ?: 0.0 } ?: 0.0

    fun copy(): Graph {
        val g = Graph()
        nodeMap.values.forEach { g.addNode(it) }
        for ((u, m) in adj) for ((v, w) in m) g.adj.getValue(u)[v] = w
        return g
    }
}
