package com.meshalive.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What this phone has demonstrated in the field, for judges, pilots and the submission. */
data class EvidenceSummary(
    val sosSent: Int,
    val sosDelivered: Int,
    val medianDeliveryMs: Long?,
    val fastestDeliveryMs: Long?,
    val avgHops: Double,
    val maxHops: Int,
    val rescueUpdatesReceived: Int,
    val receivedAsGateway: Int,
    val uploadedAsGateway: Int,
    val peopleInReceivedSos: Int,
    val relayed: Int,
    val peersMet: Int,
    val alertsReceived: Int,
    val carrierHandoffs: Int,
    val since: Long,
) {
    val deliveryRate: Double get() = if (sosSent == 0) 0.0 else 100.0 * sosDelivered / sosSent
}

object Evidence {
    private val emergencyTypes = setOf("SOS", "MEDICAL", "SAFE")

    fun summarize(messages: List<MessageEntity>, inbox: List<InboxEntity>, stats: FieldStats): EvidenceSummary {
        val sent = messages.filter { it.outgoing && it.type in emergencyTypes }
        // Sent by a gateway to itself (0 hops) proves nothing about the mesh: count only real hops.
        val delivered = sent.filter { it.status == MsgStatus.DELIVERED && it.hopCount > 0 }
        val times = delivered.mapNotNull { it.latencyMs }.sorted()
        val people = inbox.filter { it.type != "SAFE" }.sumOf { peopleOf(it.triage) }
        return EvidenceSummary(
            sosSent = sent.count { !(it.status == MsgStatus.DELIVERED && it.hopCount == 0) },
            sosDelivered = delivered.size,
            medianDeliveryMs = times.getOrNull(times.size / 2)?.let { if (times.size % 2 == 0 && times.size > 1) (it + times[times.size / 2 - 1]) / 2 else it },
            fastestDeliveryMs = times.firstOrNull(),
            avgHops = if (delivered.isEmpty()) 0.0 else delivered.map { it.hopCount }.average(),
            maxHops = (delivered.map { it.hopCount } + inbox.map { it.hopCount }).maxOrNull() ?: 0,
            rescueUpdatesReceived = sent.count { it.rescueStatus.isNotBlank() },
            receivedAsGateway = inbox.count { it.hopCount > 0 },
            uploadedAsGateway = inbox.count { it.cloudPushed && it.hopCount > 0 },
            peopleInReceivedSos = people,
            relayed = stats.relayed,
            peersMet = stats.peersMet,
            alertsReceived = stats.alertsReceived,
            carrierHandoffs = stats.carrierHandoffs,
            since = stats.since,
        )
    }

    fun peopleOf(triage: String): Int = Regex("""(\d+)\s+(person|people)""").find(triage)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    /** Markdown field-trial report, ready to attach to a submission or pilot review. */
    fun report(deviceName: String, e: EvidenceSummary, messages: List<MessageEntity>, inbox: List<InboxEntity>): String = buildString {
        val f = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US)
        appendLine("# MeshAlive field-trial report")
        appendLine()
        appendLine("Device: $deviceName · Period: ${f.format(Date(e.since))} – ${f.format(Date())}")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|---|---|")
        appendLine("| SOS sent through the mesh | ${e.sosSent} |")
        appendLine("| Confirmed delivered to a gateway | ${e.sosDelivered} (${"%.0f".format(e.deliveryRate)}%) |")
        appendLine("| Median time SOS → control room → back | ${e.medianDeliveryMs?.let { secs(it) } ?: "–"} |")
        appendLine("| Fastest delivery | ${e.fastestDeliveryMs?.let { secs(it) } ?: "–"} |")
        appendLine("| Average / maximum hops | ${"%.1f".format(e.avgHops)} / ${e.maxHops} |")
        appendLine("| Rescue updates received back | ${e.rescueUpdatesReceived} |")
        appendLine("| SOS received as gateway | ${e.receivedAsGateway} (${e.uploadedAsGateway} uploaded) |")
        appendLine("| People in received SOS | ${e.peopleInReceivedSos} |")
        appendLine("| Messages relayed for others | ${e.relayed} |")
        appendLine("| Distinct phones met | ${e.peersMet} |")
        appendLine("| Control-room alerts received | ${e.alertsReceived} |")
        appendLine("| Rescue-carrier handoffs | ${e.carrierHandoffs} |")
        appendLine()
        appendLine("## Sent")
        appendLine()
        appendLine("| Time | Type | Status | Hops | Round trip | Path |")
        appendLine("|---|---|---|---|---|---|")
        messages.filter { it.outgoing && it.type in emergencyTypes }.forEach {
            appendLine("| ${f.format(Date(it.timestamp))} | ${it.type} | ${it.status} | ${it.hopCount} | ${it.latencyMs?.let { l -> secs(l) } ?: "–"} | ${it.path.replace("|", "/")} |")
        }
        if (inbox.isNotEmpty()) {
            appendLine()
            appendLine("## Received as gateway")
            appendLine()
            appendLine("| Received | Type | From | Urgency | Hops | Path |")
            appendLine("|---|---|---|---|---|---|")
            inbox.forEach {
                appendLine("| ${f.format(Date(it.receivedAt))} | ${it.type} | ${it.srcName.replace("|", "/")} | ${it.urgency} | ${it.hopCount} | ${it.path.replace("|", "/")} |")
            }
        }
    }

    fun secs(ms: Long): String = if (ms < 10_000) "%.1f s".format(ms / 1000.0) else "${ms / 1000} s"
}
