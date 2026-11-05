package com.meshalive.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceTest {
    private fun sos(id: String, status: String, hops: Int, latency: Long?) = MessageEntity(
        msgId = id, type = "SOS", outgoing = true, src = "me", srcName = "Me", dest = "ANY_GATEWAY", text = "help",
        status = status, hopCount = hops, latencyMs = latency, timestamp = 1_000,
    )

    @Test
    fun summarisesDeliveriesAndIgnoresSelfDelivery() {
        val msgs = listOf(
            sos("a", MsgStatus.DELIVERED, 2, 4000),
            sos("b", MsgStatus.DELIVERED, 3, 6000),
            sos("c", MsgStatus.QUEUED, 0, null),
            sos("self", MsgStatus.DELIVERED, 0, 0), // gateway sent to itself: not mesh evidence
        )
        val e = Evidence.summarize(msgs, emptyList(), FieldStats(relayed = 5, peersMet = 3))
        assertEquals(3, e.sosSent)
        assertEquals(2, e.sosDelivered)
        assertEquals(5000L, e.medianDeliveryMs)
        assertEquals(4000L, e.fastestDeliveryMs)
        assertEquals(2.5, e.avgHops, 1e-9)
        assertEquals(3, e.maxHops)
        assertEquals(66.7, e.deliveryRate, 0.1)
        assertEquals(5, e.relayed)
    }

    @Test
    fun countsPeopleInReceivedSos() {
        val inbox = listOf(
            InboxEntity("1", "SOS", "x", "Kumar", "help", null, null, 0, 0, 2, "", triage = "4 people, trapped"),
            InboxEntity("2", "SAFE", "y", "Joseph", "safe", null, null, 0, 0, 1, "", triage = "5 people"),
            InboxEntity("3", "MEDICAL", "z", "Selvam", "injured", null, null, 0, 0, 3, "", triage = "1 person, injured"),
        )
        val e = Evidence.summarize(emptyList(), inbox, FieldStats())
        assertEquals(5, e.peopleInReceivedSos) // SAFE check-ins are not people needing help
        assertEquals(3, e.receivedAsGateway)
        assertEquals(3, e.maxHops)
    }

    @Test
    fun reportContainsMetricsTable() {
        val msgs = listOf(sos("a", MsgStatus.DELIVERED, 2, 4000))
        val e = Evidence.summarize(msgs, emptyList(), FieldStats())
        val r = Evidence.report("Phone-1", e, msgs, emptyList())
        assertTrue(r.contains("| Confirmed delivered to a gateway | 1 (100%) |"))
        assertTrue(r.contains("| Median time SOS → control room → back | 4.0 s |"))
    }
}
