package com.meshalive.app.mesh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * A signed instruction from the district control room dashboard.
 * cmd = "dispatch" | "rescued" (about one SOS, by msgId) or "alert" (broadcast to everyone).
 */
@Serializable
data class Command(
    val cmd: String,
    val msgId: String = "",
    val note: String = "",
    val title: String = "",
    val text: String = "",
    val level: String = "WARNING",
    val ts: Long = 0,
    val sig: String = "",
)

/**
 * Control room <-> gateway channel. The dashboard publishes signed commands to
 * ntfy.sh/<channel>-cmd; any gateway with internet polls it and pushes them into the
 * offline mesh. Signature = HMAC-SHA256(key = mesh passphrase, canonical fields), so a
 * stranger who only knows the channel name cannot inject a fake evacuation order.
 */
object ControlRoom {
    private const val MAX_AGE_MS = 6 * 60 * 60_000L

    fun cmdTopic(topic: String) = "$topic-cmd"

    fun canonical(c: Command): String =
        listOf(c.cmd, c.msgId, c.note, c.title, c.text, c.level, c.ts.toString()).joinToString("|")

    fun sign(passphrase: String, c: Command): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(passphrase.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(canonical(c).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun verify(passphrase: String, c: Command, now: Long = System.currentTimeMillis()): Boolean {
        if (c.sig.isBlank() || abs(now - c.ts) > MAX_AGE_MS) return false
        val expected = sign(passphrase, c)
        // constant-time compare
        if (expected.length != c.sig.length) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (expected[i].code xor c.sig[i].code)
        return diff == 0
    }

    /** One ntfy message from the command channel. */
    data class Polled(val id: String, val command: Command?)

    /** Fetches commands published after [since] (an ntfy message id, or a duration like "30m"). */
    suspend fun poll(topic: String, since: String): List<Polled> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://ntfy.sh/${cmdTopic(topic)}/json?poll=1&since=" + URLEncoder.encode(since, "UTF-8"))
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parse(body)
        }.getOrDefault(emptyList())
    }

    /** Parses ntfy's newline-delimited JSON poll response. */
    fun parse(body: String): List<Polled> = body.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        val o = runCatching { MeshJson.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@mapNotNull null
        if (o["event"]?.jsonPrimitive?.content != "message") return@mapNotNull null
        val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val msg = o["message"]?.jsonPrimitive?.content ?: ""
        Polled(id, runCatching { MeshJson.decodeFromString(Command.serializer(), msg) }.getOrNull())
    }.toList()

    /** Tells the dashboard something happened (e.g. the victim's phone confirmed the rescue update). */
    suspend fun postEvent(topic: String, event: JsonObject) = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("topic", CloudRelay.dataTopic(topic))
                put("message", event.toString())
            }
            val conn = URL("https://ntfy.sh/").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode.also { conn.disconnect() }
        }
    }
}
