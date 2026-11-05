package com.meshalive.app.mesh

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
enum class PacketType(val priority: Int) {
    SOS(0), MEDICAL(1), CHAT(2), LSA(0), PING(0), PONG(0), ACK(0),

    /** "I'm safe" check-in, routed to the nearest gateway like an SOS but at low priority. */
    SAFE(2),

    /** Control room → one victim: "rescue team dispatched", "you are marked rescued". */
    UPDATE(0),

    /** Control room → everyone: evacuation / warning, flooded to every phone. */
    ALERT(0);

    val isUserMessage: Boolean get() = this == SOS || this == MEDICAL || this == CHAT || this == SAFE || this == UPDATE
}

@Serializable
data class Packet(
    val msgId: String,
    val type: PacketType,
    val priority: Int,
    val src: String,
    val dest: String,
    val ttl: Int = 15,
    val hops: List<String> = emptyList(),
    val lat: Double? = null,
    val lng: Double? = null,
    /** AES-GCM encrypted (base64) for SOS/MEDICAL/CHAT; plain JSON for control packets. */
    val payload: String,
    val timestamp: Long,
    /** Triage urgency 0..100, in clear so relays can prioritise without decrypting. */
    val urgency: Int = 0,
) {
    fun toBytes(): ByteArray = MeshJson.encodeToString(this).toByteArray(Charsets.UTF_8)

    companion object {
        fun fromBytes(bytes: ByteArray): Packet? =
            runCatching { MeshJson.decodeFromString<Packet>(String(bytes, Charsets.UTF_8)) }.getOrNull()

        fun fromJson(json: String): Packet? = runCatching { MeshJson.decodeFromString<Packet>(json) }.getOrNull()

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        /** msgId = SHA-256(src + timestamp + payload). */
        fun idFor(src: String, timestamp: Long, payload: String): String = sha256(src + timestamp + payload)
    }
}

fun Packet.toJson(): String = MeshJson.encodeToString(this)

val MeshJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class NeighbourLink(val nodeId: String, val linkCost: Double)

/** Link-State Advertisement body. */
@Serializable
data class Lsa(
    val nodeId: String,
    val name: String,
    val seqNo: Long,
    val batteryPercent: Int,
    val hasInternet: Boolean,
    val neighbours: List<NeighbourLink>,
    /** Rescue boat / NDRF / ambulance phone that physically carries messages out of the dead zone. */
    val isCarrier: Boolean = false,
)

/** Plaintext of a user message before encryption. */
@Serializable
data class Content(
    val name: String,
    val text: String,
    val triage: TriageInfo? = null,
    /** For UPDATE packets: which SOS this answers and the new rescue status. */
    val rescue: RescueInfo? = null,
)

@Serializable
data class RescueInfo(val forMsgId: String, val status: String)

/** Body of an ALERT broadcast (encrypted with the mesh key). */
@Serializable
data class AlertBody(
    val title: String,
    val text: String,
    val level: String = "WARNING",
    val issuedBy: String = "District Control Room",
    val issuedAt: Long = System.currentTimeMillis(),
)

object RescueStatus {
    const val DISPATCHED = "DISPATCHED"
    const val RESCUED = "RESCUED"

    fun label(status: String) = when (status) {
        DISPATCHED -> "Rescue team on the way"
        RESCUED -> "Marked rescued"
        else -> status.lowercase().replaceFirstChar { it.uppercase() }
    }
}

/** Structured situation report, so a control room can sort and dispatch without reading free text. */
@Serializable
data class TriageInfo(
    val people: Int = 1,
    val elderly: Boolean = false,
    val children: Boolean = false,
    val injured: Boolean = false,
    val disabled: Boolean = false,
    val trapped: Boolean = false,
    /** 0 none, 1 knee, 2 waist, 3 chest, 4 roof. */
    val water: Int = 0,
    val urgency: Int = 0,
) {
    fun summary(): String = buildList {
        add("$people ${if (people == 1) "person" else "people"}")
        if (trapped) add("trapped")
        if (injured) add("injured")
        if (elderly) add("elderly")
        if (children) add("children")
        if (disabled) add("disabled")
        if (water > 0) add("water at " + WATER_LABELS[water.coerceIn(0, 4)])
    }.joinToString(", ")

    companion object {
        val WATER_LABELS = listOf("none", "knee", "waist", "chest", "roof")
    }
}

/** Body of an ACK: travels back along the reverse of [route]. */
@Serializable
data class AckBody(val ackFor: String, val route: List<String>, val gatewayName: String, val receivedAt: Long)

@Serializable
data class PingBody(val sentAt: Long)
