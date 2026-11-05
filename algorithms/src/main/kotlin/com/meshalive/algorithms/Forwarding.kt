package com.meshalive.algorithms

import java.util.PriorityQueue

/**
 * Duplicate suppression: a hash set of seen message ids with timestamp expiry.
 * markIfNew is O(1) on average; expired entries are purged in amortised O(1)
 * (a full sweep happens at most once per [ttlMs]).
 */
class DuplicateCache(private val ttlMs: Long = 10 * 60 * 1000L) {
    private val seen = HashMap<String, Long>()
    private var lastSweep = 0L

    /** Returns true if [id] has not been seen (and records it), false if it is a duplicate. */
    @Synchronized
    fun markIfNew(id: String, now: Long = System.currentTimeMillis()): Boolean {
        if (now - lastSweep > ttlMs) sweep(now)
        val t = seen[id]
        if (t != null && now - t <= ttlMs) return false
        seen[id] = now
        return true
    }

    @Synchronized
    fun contains(id: String, now: Long = System.currentTimeMillis()): Boolean {
        val t = seen[id] ?: return false
        return now - t <= ttlMs
    }

    @Synchronized
    fun size(): Int = seen.size

    @Synchronized
    fun clear() = seen.clear()

    private fun sweep(now: Long) {
        seen.entries.removeAll { now - it.value > ttlMs }
        lastSweep = now
    }
}

/**
 * Outgoing min-heap ordered by priority (SOS=0 < MEDICAL=1 < CHAT=2), FIFO within
 * the same priority. offer/poll are O(log n).
 */
class PacketPriorityQueue<T>(private val priorityOf: (T) -> Int) {
    private data class Entry<T>(val priority: Int, val seq: Long, val item: T)

    private var counter = 0L
    private val heap = PriorityQueue<Entry<T>>(compareBy<Entry<T>>({ it.priority }, { it.seq }))

    @Synchronized
    fun offer(item: T) {
        heap.add(Entry(priorityOf(item), counter++, item))
    }

    @Synchronized
    fun poll(): T? = heap.poll()?.item

    @Synchronized
    fun peek(): T? = heap.peek()?.item

    val size: Int @Synchronized get() = heap.size
    fun isEmpty(): Boolean = size == 0
}

/** Routing-relevant header of a packet, independent of serialization. */
data class PacketHeader(
    val msgId: String,
    val src: String,
    val dest: String,
    val ttl: Int,
    val hops: List<String>,
)

sealed class Decision {
    data class Drop(val reason: String) : Decision()
    /** This node is the destination (or a gateway receiving an ANY_GATEWAY packet). */
    object Deliver : Decision()
    data class Forward(val to: List<String>, val newTtl: Int, val newHops: List<String>) : Decision()
    /** No route right now: keep it in the store-and-forward queue. */
    data class Store(val newTtl: Int, val newHops: List<String>) : Decision()
}

/**
 * Per-node forwarding logic shared by the real mesh and tests:
 * duplicate check -> delivery check -> TTL decrement -> routing decision.
 */
class ForwardingEngine(
    var strategy: RoutingStrategy,
    val duplicates: DuplicateCache = DuplicateCache(),
) {
    fun onReceive(
        graph: Graph,
        self: String,
        selfIsGateway: Boolean,
        header: PacketHeader,
        cameFrom: String?,
        now: Long = System.currentTimeMillis(),
    ): Decision {
        if (!duplicates.markIfNew(header.msgId, now)) return Decision.Drop("duplicate")
        val hops = if (header.hops.lastOrNull() == self) header.hops else header.hops + self
        if (header.dest == self || (header.dest == ANY_GATEWAY && selfIsGateway)) return Decision.Deliver
        return route(graph, self, header.copy(hops = hops), cameFrom, decrement = cameFrom != null)
    }

    /** Routing step for a packet already accepted (also used to retry stored packets). */
    fun route(
        graph: Graph,
        self: String,
        header: PacketHeader,
        cameFrom: String?,
        decrement: Boolean = true,
    ): Decision {
        val ttl = if (decrement) header.ttl - 1 else header.ttl
        if (ttl <= 0) return Decision.Drop("ttl expired")
        val visited = header.hops.toSet()
        val next = strategy.nextHops(graph, self, header.dest, cameFrom)
            // never bounce back into the path already taken (loop avoidance)
            .filter { it !in visited }
        return if (next.isEmpty()) Decision.Store(ttl, header.hops)
        else Decision.Forward(next, ttl, header.hops)
    }
}
