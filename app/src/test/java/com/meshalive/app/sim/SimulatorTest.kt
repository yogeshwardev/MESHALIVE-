package com.meshalive.app.sim

import com.meshalive.algorithms.Algorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatorTest {

    @Test
    fun sameSeedGivesSameWorld() {
        val a = Simulator.buildWorld(SimConfig(nodes = 100, seed = 7))
        val b = Simulator.buildWorld(SimConfig(nodes = 100, seed = 7))
        assertEquals(a.edges, b.edges)
        assertEquals(a.sources, b.sources)
    }

    @Test
    fun floodingUsesMostTransmissions() {
        val out = Simulator.run(SimConfig(nodes = 300, areaSize = 800.0, radioRange = 120.0, messages = 10, seed = 3))
        val byAlg = out.results.associateBy { it.algorithm }
        val flood = byAlg.getValue(Algorithm.FLOODING)
        val bfs = byAlg.getValue(Algorithm.BFS)
        val dij = byAlg.getValue(Algorithm.DIJKSTRA)
        assertTrue(flood.transmissions > bfs.transmissions)
        assertTrue(flood.transmissions > dij.transmissions)
        assertTrue(dij.minBattery >= 0.0)
    }

    @Test
    fun csvHasHeaderAndRows() {
        val out = Simulator.run(SimConfig(nodes = 50, messages = 3))
        val csv = Simulator.toCsv(out, emptyList())
        assertTrue(csv.contains("algorithm,delivery_ratio"))
        assertEquals(3, csv.lines().count { it.startsWith("Flooding") || it.startsWith("BFS") || it.startsWith("Battery") })
    }
}
