package com.meshalive.algorithms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriageTest {

    @Test
    fun plainSosIsMedium() {
        assertEquals(40, Triage.urgency(Triage.BASE_SOS))
        assertEquals("MEDIUM", Triage.band(40))
    }

    @Test
    fun trappedFamilyWithElderlyAndRisingWaterIsCritical() {
        // trapped (+15), 3 people (+6), elderly (+8), water at waist (+12)
        val u = Triage.urgency(Triage.BASE_SOS, people = 3, elderly = true, trapped = true, water = 2)
        assertEquals(81, u)
        assertEquals("CRITICAL", Triage.band(u))
    }

    @Test
    fun scoreIsCappedAt100() {
        val u = Triage.urgency(Triage.BASE_SOS, people = 20, elderly = true, children = true, injured = true,
            disabled = true, trapped = true, water = 4)
        assertEquals(100, u)
    }

    @Test
    fun outOfRangeInputsAreClamped() {
        assertEquals(Triage.urgency(Triage.BASE_SOS, water = 4), Triage.urgency(Triage.BASE_SOS, water = 99))
        assertEquals(Triage.urgency(Triage.BASE_SOS), Triage.urgency(Triage.BASE_SOS, people = 0))
    }

    @Test
    fun suppliesRequestRanksBelowSos() {
        assertTrue(Triage.urgency(Triage.BASE_SUPPLIES) < Triage.urgency(Triage.BASE_SOS))
    }

    @Test
    fun queueSendsMostUrgentFirstAndChatsLast() {
        data class Msg(val name: String, val chat: Boolean, val urgency: Int)
        val q = PacketPriorityQueue<Msg> { Triage.queueKey(it.chat, it.urgency) }
        q.offer(Msg("chat", true, 0))
        q.offer(Msg("food", false, 15))
        q.offer(Msg("critical", false, 90))
        q.offer(Msg("medium", false, 40))
        val order = generateSequence { q.poll() }.map { it.name }.toList()
        assertEquals(listOf("critical", "medium", "food", "chat"), order)
    }
}
