package com.meshalive.algorithms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingTest {

    /**
     *   A - B - C - G1
     *   |           |
     *   D --------- E
     *  G1 is the only gateway. Fewest hops from A: A-B-C-G1 (3) vs A-D-E-G1 (3).
     */
    private fun lineGraph(): Graph = Graph()
        .addNode("A").addNode("B").addNode("C").addNode("G", hasInternet = true)
        .addEdge("A", "B", 10.0).addEdge("B", "C", 10.0).addEdge("C", "G", 10.0)

    // ---------- Flooding ----------

    @Test
    fun flooding_forwardsToAllNeighboursExceptSender() {
        val g = Graph().addEdge("X", "A").addEdge("X", "B").addEdge("X", "C")
        assertEquals(setOf("B", "C"), FloodingStrategy.nextHops(g, "X", ANY_GATEWAY, "A").toSet())
        assertEquals(setOf("A", "B", "C"), FloodingStrategy.nextHops(g, "X", ANY_GATEWAY, null).toSet())
    }

    @Test
    fun flooding_singleNodeHasNoNextHop() {
        val g = Graph().addNode("A")
        assertTrue(FloodingStrategy.nextHops(g, "A", ANY_GATEWAY, null).isEmpty())
    }

    // ---------- BFS ----------

    @Test
    fun bfs_findsFewestHopsOnLine() {
        val t = BfsStrategy.computeRoutes(lineGraph(), setOf("G"))
        assertEquals(3, t.hops["A"])
        assertEquals("B", t.nextHop["A"])
        assertEquals(listOf("A", "B", "C", "G"), t.pathFrom("A"))
    }

    @Test
    fun bfs_prefersShortcutEvenIfSlow() {
        val g = lineGraph().addEdge("A", "G", 500.0)
        val t = BfsStrategy.computeRoutes(g, setOf("G"))
        assertEquals(1, t.hops["A"])
        assertEquals("G", t.nextHop["A"])
    }

    @Test
    fun bfs_picksNearestOfMultipleGateways() {
        // A - B - G1 ;  A - C - D - E - G2
        val g = Graph().addNode("G1", hasInternet = true).addNode("G2", hasInternet = true)
            .addEdge("A", "B").addEdge("B", "G1")
            .addEdge("A", "C").addEdge("C", "D").addEdge("D", "E").addEdge("E", "G2")
        val t = BfsStrategy.computeRoutes(g, g.gateways())
        assertEquals(listOf("A", "B", "G1"), t.pathFrom("A"))
        assertEquals(listOf("D", "E", "G2"), t.pathFrom("D"))
    }

    @Test
    fun bfs_disconnectedGraphHasNoRoute() {
        val g = Graph().addEdge("A", "B").addNode("G", hasInternet = true)
        val t = BfsStrategy.computeRoutes(g, g.gateways())
        assertFalse(t.reachable("A"))
        assertTrue(BfsStrategy.nextHops(g, "A", ANY_GATEWAY, null).isEmpty())
        assertTrue(t.pathFrom("A").isEmpty())
    }

    @Test
    fun bfs_singleGatewayNodeIsItsOwnTarget() {
        val g = Graph().addNode("G", hasInternet = true)
        val t = BfsStrategy.computeRoutes(g, g.gateways())
        assertEquals(0, t.hops["G"])
        assertEquals(listOf("G"), t.pathFrom("G"))
    }

    @Test
    fun bfs_noGatewaysMeansNothingReachable() {
        val t = BfsStrategy.computeRoutes(lineGraph().addNode("G", hasInternet = false), emptySet())
        assertTrue(t.cost.isEmpty())
    }

    // ---------- Dijkstra ----------

    @Test
    fun dijkstra_matchesHandComputedCosts() {
        // All batteries 100 => battery term = 50/100 = 0.5 ; maxRtt = 10
        // each hop cost = 1*(10/10) + 0.5 + 1 = 2.5
        val t = DijkstraStrategy().computeRoutes(lineGraph(), setOf("G"))
        assertEquals(2.5, t.cost.getValue("C"), 1e-9)
        assertEquals(5.0, t.cost.getValue("B"), 1e-9)
        assertEquals(7.5, t.cost.getValue("A"), 1e-9)
        assertEquals(listOf("A", "B", "C", "G"), t.pathFrom("A"))
    }

    @Test
    fun dijkstra_prefersLowRttPathWhenHopsEqual() {
        //  S - F - G (fast, 10 ms) ;  S - L - G (slow, 100 ms)
        val g = Graph().addNode("G", hasInternet = true)
            .addEdge("S", "F", 10.0).addEdge("F", "G", 10.0)
            .addEdge("S", "L", 100.0).addEdge("L", "G", 100.0)
        val t = DijkstraStrategy().computeRoutes(g, g.gateways())
        assertEquals("F", t.nextHop["S"])
    }

    @Test
    fun dijkstra_avoidsLowBatteryNode() {
        //  S - W(5%) - G   vs   S - H1 - H2 - G (all 100%)
        // via W:  cost(S->W)=1*1+50/5+1 = 12, cost(W->G)=1+0.5+1 = 2.5 -> 14.5
        // via H:  3 hops of 2.5 = 7.5  -> Dijkstra takes the longer, healthier path
        val g = Graph()
            .addNode("S").addNode("W", battery = 5).addNode("H1").addNode("H2")
            .addNode("G", hasInternet = true)
            .addEdge("S", "W", 10.0).addEdge("W", "G", 10.0)
            .addEdge("S", "H1", 10.0).addEdge("H1", "H2", 10.0).addEdge("H2", "G", 10.0)
        val dijkstra = DijkstraStrategy().computeRoutes(g, g.gateways())
        assertEquals(listOf("S", "H1", "H2", "G"), dijkstra.pathFrom("S"))
        assertEquals(7.5, dijkstra.cost.getValue("S"), 1e-9)
        // BFS on the same graph happily drains the weak phone.
        assertEquals(listOf("S", "W", "G"), BfsStrategy.computeRoutes(g, g.gateways()).pathFrom("S"))
    }

    @Test
    fun dijkstra_betaZeroIgnoresBattery() {
        val g = Graph()
            .addNode("S").addNode("W", battery = 5).addNode("H1").addNode("H2")
            .addNode("G", hasInternet = true)
            .addEdge("S", "W", 10.0).addEdge("W", "G", 10.0)
            .addEdge("S", "H1", 10.0).addEdge("H1", "H2", 10.0).addEdge("H2", "G", 10.0)
        val t = DijkstraStrategy(CostParams(beta = 0.0)).computeRoutes(g, g.gateways())
        assertEquals(listOf("S", "W", "G"), t.pathFrom("S"))
    }

    @Test
    fun dijkstra_superNodeGivesNearestGatewayForEveryNode() {
        // G1 - A - B - C - D - G2
        val g = Graph().addNode("G1", hasInternet = true).addNode("G2", hasInternet = true)
            .addEdge("G1", "A").addEdge("A", "B").addEdge("B", "C").addEdge("C", "D").addEdge("D", "G2")
        val t = DijkstraStrategy().computeRoutes(g, g.gateways())
        assertEquals("G1", t.pathFrom("A").last())
        assertEquals("G1", t.pathFrom("B").last())
        assertEquals("G2", t.pathFrom("C").last())
        assertEquals("G2", t.pathFrom("D").last())
    }

    @Test
    fun dijkstra_disconnectedGraph() {
        val g = Graph().addEdge("A", "B").addEdge("C", "G").addNode("G", hasInternet = true)
        val t = DijkstraStrategy().computeRoutes(g, g.gateways())
        assertFalse(t.reachable("A"))
        assertTrue(t.reachable("C"))
        assertTrue(DijkstraStrategy().nextHops(g, "A", ANY_GATEWAY, null).isEmpty())
    }

    @Test
    fun dijkstra_singleNode() {
        val g = Graph().addNode("A")
        val t = DijkstraStrategy().computeRoutes(g, g.gateways())
        assertTrue(t.cost.isEmpty())
    }

    @Test
    fun dijkstra_unicastToSpecificNode() {
        val g = lineGraph()
        assertEquals(listOf("G", "C", "B", "A"), DijkstraStrategy().route(g, "G", "A"))
        assertEquals(listOf("B"), DijkstraStrategy().nextHops(g, "A", "C", null))
    }
}
