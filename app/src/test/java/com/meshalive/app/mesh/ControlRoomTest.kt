package com.meshalive.app.mesh

import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlRoomTest {
    private val ts = 1_700_000_000_000L
    private val dispatch = Command(cmd = "dispatch", msgId = "abc123", note = "Boat 7 coming", ts = ts)
    private val alert = Command(cmd = "alert", title = "Evacuate now", text = "நீர் மட்டம் உயர்கிறது", level = "DANGER", ts = ts)

    @Test
    fun signatureMatchesIndependentHmacReference() {
        // Reference values computed with Python's hmac/hashlib (same algorithm as browser WebCrypto).
        assertEquals("ee66b34e79af5b521da13053a1c995882ad69943b5b749170bfb9b88f8df023e", ControlRoom.sign("meshalive-demo", dispatch))
        assertEquals("7096a321dfdc77617276d1b984bd2ab2157e19321daa365f852dde7f97fc883b", ControlRoom.sign("meshalive-demo", alert))
    }

    @Test
    fun validCommandVerifies() {
        val signed = dispatch.copy(sig = ControlRoom.sign("meshalive-demo", dispatch))
        assertTrue(ControlRoom.verify("meshalive-demo", signed, now = ts + 60_000))
    }

    @Test
    fun wrongPassphraseIsRejected() {
        val signed = dispatch.copy(sig = ControlRoom.sign("attacker", dispatch))
        assertFalse(ControlRoom.verify("meshalive-demo", signed, now = ts))
    }

    @Test
    fun tamperedCommandIsRejected() {
        val signed = alert.copy(sig = ControlRoom.sign("meshalive-demo", alert))
        assertFalse(ControlRoom.verify("meshalive-demo", signed.copy(text = "All clear"), now = ts))
    }

    @Test
    fun staleOrUnsignedCommandIsRejected() {
        val signed = dispatch.copy(sig = ControlRoom.sign("meshalive-demo", dispatch))
        assertFalse(ControlRoom.verify("meshalive-demo", signed, now = ts + 7 * 60 * 60_000L))
        assertFalse(ControlRoom.verify("meshalive-demo", dispatch, now = ts))
    }

    @Test
    fun parsesNtfyPollResponse() {
        // Shape of https://ntfy.sh/<topic>/json?poll=1 output: one JSON object per line.
        val cmd = """{"cmd":"dispatch","msgId":"abc123","note":"Boat 7 coming","title":"","text":"","level":"WARNING","ts":1700000000000,"sig":"ee66"}"""
        val body = listOf(
            """{"id":"open1","time":1700000000,"event":"open","topic":"t-cmd"}""",
            """{"id":"Xk9q2","time":1700000001,"event":"message","topic":"t-cmd","message":${MeshJson.encodeToString(String.serializer(), cmd)}}""",
            """{"id":"junk1","time":1700000002,"event":"message","topic":"t-cmd","message":"hello from a stranger"}""",
            "",
        ).joinToString("\n")
        val polled = ControlRoom.parse(body)
        assertEquals(listOf("Xk9q2", "junk1"), polled.map { it.id })
        assertEquals("abc123", polled[0].command?.msgId)
        assertEquals("Boat 7 coming", polled[0].command?.note)
        assertEquals(null, polled[1].command)
    }
}
