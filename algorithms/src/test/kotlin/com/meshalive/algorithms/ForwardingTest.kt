package com.meshalive.algorithms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardingTest {

    private val graph = Graph().addNode("G", hasInternet = true)
        .addEdge("A", "B", 10.0).addEdge("B", "G", 10.0)

    private fun header(id: String = "m1", ttl: Int = 15, hops: List<String> = listOf("A")) =
        PacketHeader(msgId = id, src = "A", dest = ANY_GATEWAY, ttl = ttl, hops = hops)

    @Test
    fun duplicateIsDropped() {
        val engine = ForwardingEngine(DijkstraStrategy())
        val first = engine.onReceive(graph, "B", false, header(), cameFrom = "A", now = 1000)
        val second = engine.onReceive(graph, "B", false, header(), cameFrom = "A", now = 1001)
        assertTrue(first is Decision.Forward)
        assertEquals(Decision.Drop("duplicate"), second)
    }

    @Test
    fun duplicateCacheExpires() {
        val cache = DuplicateCache(ttlMs = 100)
        assertTrue(cache.markIfNew("x", now = 0))
        assertFalse(cache.markIfNew("x", now = 50))
        assertTrue(cache.markIfNew("x", now = 500))
    }

    @Test
    fun ttlDecrementsEachHop() {
        val engine = ForwardingEngine(BfsStrategy)
        val d = engine.onReceive(graph, "B", false, header(ttl = 5), cameFrom = "A") as Decision.Forward
        assertEquals(4, d.newTtl)
        assertEquals(listOf("G"), d.to)
        assertEquals(listOf("A", "B"), d.newHops)
    }

    @Test
    fun ttlExpiryDropsPacket() {
        val engine = ForwardingEngine(BfsStrategy)
        val d = engine.onReceive(graph, "B", false, header(ttl = 1), cameFrom = "A")
        assertEquals(Decision.Drop("ttl expired"), d)
    }

    @Test
    fun gatewayDelivers() {
        val engine = ForwardingEngine(DijkstraStrategy())
        val d = engine.onReceive(graph, "G", true, header(hops = listOf("A", "B")), cameFrom = "B")
        assertEquals(Decision.Deliver, d)
    }

    @Test
    fun noRouteMeansStore() {
        val isolated = Graph().addEdge("A", "B").addNode("G", hasInternet = true)
        val engine = ForwardingEngine(DijkstraStrategy())
        val d = engine.onReceive(isolated, "B", false, header(), cameFrom = "A")
        assertTrue(d is Decision.Store)
    }

    @Test
    fun originatorDoesNotDecrementTtl() {
        val engine = ForwardingEngine(BfsStrategy)
        val d = engine.onReceive(graph, "A", false, header(ttl = 15, hops = emptyList()), cameFrom = null)
        d as Decision.Forward
        assertEquals(15, d.newTtl)
        assertEquals(listOf("B"), d.to)
    }

    @Test
    fun floodingDoesNotSendBackAlongPath() {
        val engine = ForwardingEngine(FloodingStrategy)
        val d = engine.onReceive(graph, "B", false, header(), cameFrom = "A") as Decision.Forward
        assertEquals(listOf("G"), d.to)
    }

    @Test
    fun priorityQueueOrdersSosBeforeMedicalBeforeChat() {
        val q = PacketPriorityQueue<Pair<String, Int>> { it.second }
        q.offer("chat1" to 2); q.offer("med" to 1); q.offer("sos1" to 0); q.offer("chat2" to 2); q.offer("sos2" to 0)
        val order = generateSequence { q.poll() }.map { it.first }.toList()
        assertEquals(listOf("sos1", "sos2", "med", "chat1", "chat2"), order)
    }
}
