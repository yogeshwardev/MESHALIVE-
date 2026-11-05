package com.meshalive.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object MsgStatus {
    const val QUEUED = "Queued"
    const val RELAYING = "Relaying"
    const val DELIVERED = "Delivered"
    const val FAILED = "Failed"
    const val RECEIVED = "Received"
    const val CARRIED = "With carrier"
}

/** A message this phone sent or received (shown on the Messages screen). */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val msgId: String,
    val type: String,
    val outgoing: Boolean,
    val src: String,
    val srcName: String,
    val dest: String,
    val text: String,
    val status: String,
    val hopCount: Int = 0,
    val path: String = "",
    val latencyMs: Long? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val timestamp: Long,
    val urgency: Int = 0,
    val triage: String = "",
    /** Latest word from the control room about this SOS (DISPATCHED / RESCUED). */
    val rescueStatus: String = "",
    val rescueNote: String = "",
    /** For outgoing control-room UPDATE packets: the SOS they answer. */
    val refId: String = "",
)

/** Store-and-forward queue: packets waiting for a route. */
@Entity(tableName = "pending_packets")
data class PendingPacketEntity(
    @PrimaryKey val msgId: String,
    val packetJson: String,
    val priority: Int,
    val cameFrom: String?,
    val enqueuedAt: Long,
)

/** SOS / medical messages that reached this phone while acting as a gateway. */
@Entity(tableName = "gateway_inbox")
data class InboxEntity(
    @PrimaryKey val msgId: String,
    val type: String,
    val src: String,
    val srcName: String,
    val text: String,
    val lat: Double?,
    val lng: Double?,
    val sentAt: Long,
    val receivedAt: Long,
    val hopCount: Int,
    val path: String,
    val webhookStatus: String = "",
    /** True once the alert reached the internet (ntfy push and/or webhook). */
    val cloudPushed: Boolean = false,
    val urgency: Int = 0,
    val triage: String = "",
    val rescueStatus: String = "",
)
