package com.meshalive.app.mesh

import com.meshalive.app.data.InboxEntity
import com.meshalive.app.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.meshalive.algorithms.Triage
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gateway -> internet. Publishes each SOS to a public ntfy.sh topic (free, no account,
 * anyone can watch it live in a browser or the ntfy app) and optionally to a custom webhook.
 */
object CloudRelay {

    data class Result(val ok: Boolean, val status: String)

    suspend fun push(e: InboxEntity, s: Settings, gatewayName: String): Result = withContext(Dispatchers.IO) {
        val parts = mutableListOf<String>()
        var anyOk = false
        var anyTried = false
        if (s.cloudEnabled && s.cloudTopic.isNotBlank()) {
            anyTried = true
            val r = postJson("https://ntfy.sh/", ntfyBody(e, s.cloudTopic, gatewayName))
            if (r.first) anyOk = true
            parts += if (r.first) "Live alert sent ✓" else "Alert push failed (${r.second})"
            // Machine-readable copy for the district control-room dashboard.
            postJson("https://ntfy.sh/", buildJsonObject {
                put("topic", dataTopic(s.cloudTopic))
                put("message", webhookBody(e, gatewayName).toString())
            })
        }
        if (s.webhookUrl.isNotBlank()) {
            anyTried = true
            val r = postJson(s.webhookUrl, webhookBody(e, gatewayName))
            if (r.first) anyOk = true
            parts += if (r.first) "Webhook ✓" else "Webhook failed (${r.second})"
        }
        if (!anyTried) Result(true, "Cloud forwarding off") else Result(anyOk, parts.joinToString(" · "))
    }

    /** Sends a test alert so you can verify internet + topic before a demo. */
    suspend fun test(s: Settings, gatewayName: String): Result = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("topic", s.cloudTopic)
            put("title", "MeshAlive gateway online")
            put("message", "$gatewayName is connected to the internet and will forward SOS alerts from the offline mesh to this channel.")
            put("priority", 3)
            put("tags", JsonArray(listOf(JsonPrimitive("satellite_antenna"), JsonPrimitive("white_check_mark"))))
        }
        val r = postJson("https://ntfy.sh/", body)
        Result(r.first, if (r.first) "Test alert delivered to ntfy.sh/${s.cloudTopic}" else "Failed: ${r.second}")
    }

    private fun ntfyBody(e: InboxEntity, topic: String, gatewayName: String): JsonObject {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val loc = if (e.lat != null && e.lng != null) "\n📍 %.5f, %.5f".format(e.lat, e.lng) else "\n📍 location unavailable"
        return buildJsonObject {
            put("topic", topic)
            val band = if (e.urgency > 0) "${Triage.band(e.urgency)} ${e.urgency} · " else ""
            put("title", if (e.type == "SAFE") "✅ ${e.srcName} is safe" else "${if (e.type == "SOS") "🆘" else "🚑"} $band${e.type} from ${e.srcName}")
            put(
                "message",
                "${e.text}" + (if (e.triage.isNotBlank()) "\n👥 ${e.triage}" else "") + "$loc\n🔗 Relayed offline over ${e.hopCount} hop(s): ${e.path}\n" +
                    "🛰 Uploaded by gateway $gatewayName at ${time.format(Date(e.receivedAt))}",
            )
            put("priority", when {
                e.type == "SAFE" -> 2
                e.urgency >= 50 || (e.urgency == 0 && e.type == "SOS") -> 5
                else -> 4
            })
            put("tags", JsonArray(listOf(JsonPrimitive("rotating_light"), JsonPrimitive(e.type.lowercase()))))
            if (e.lat != null && e.lng != null) {
                val maps = "https://www.google.com/maps/search/?api=1&query=${e.lat},${e.lng}"
                put("click", maps)
                put("actions", JsonArray(listOf(buildJsonObject {
                    put("action", "view"); put("label", "Open map"); put("url", maps)
                })))
            }
        }
    }

    private fun webhookBody(e: InboxEntity, gatewayName: String) = buildJsonObject {
        put("msgId", e.msgId); put("type", e.type); put("from", e.srcName); put("nodeId", e.src)
        put("message", e.text); put("lat", e.lat); put("lng", e.lng)
        put("sentAt", e.sentAt); put("receivedAt", e.receivedAt)
        put("hops", e.hopCount); put("path", e.path); put("gateway", gatewayName)
        put("urgency", e.urgency); put("triage", e.triage)
    }

    /** Channel the control-room dashboard listens on: one JSON object per SOS. */
    fun dataTopic(topic: String) = "$topic-data"

    private fun postJson(url: String, body: JsonObject): Pair<Boolean, String> = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        conn.disconnect()
        (code in 200..299) to "HTTP $code"
    } catch (e: Exception) {
        false to (e.message ?: e.javaClass.simpleName)
    }
}
