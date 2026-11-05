package com.meshalive.algorithms

/**
 * SOS triage: turns "trapped, 3 people, 1 elderly, water at chest" into an urgency
 * score 0..100. The score travels in the packet header (not encrypted), so every relay
 * can order its outgoing min-heap by urgency without reading the message itself.
 */
object Triage {
    /** Water level 0 = none, 1 = knee, 2 = waist, 3 = chest, 4 = roof / neck. */
    private val WATER_POINTS = intArrayOf(0, 5, 12, 20, 30)

    const val BASE_SOS = 40
    const val BASE_MEDICAL = 40
    const val BASE_SUPPLIES = 15

    fun urgency(
        base: Int,
        people: Int = 1,
        elderly: Boolean = false,
        children: Boolean = false,
        injured: Boolean = false,
        disabled: Boolean = false,
        trapped: Boolean = false,
        water: Int = 0,
    ): Int {
        var score = base
        if (trapped) score += 15
        if (injured) score += 15
        score += WATER_POINTS[water.coerceIn(0, WATER_POINTS.size - 1)]
        score += minOf((people.coerceAtLeast(1) - 1) * 3, 15)
        if (elderly) score += 8
        if (children) score += 8
        if (disabled) score += 8
        return score.coerceIn(0, 100)
    }

    fun band(urgency: Int): String = when {
        urgency >= 75 -> "CRITICAL"
        urgency >= 50 -> "HIGH"
        urgency >= 30 -> "MEDIUM"
        else -> "LOW"
    }

    /**
     * Key for the outgoing min-heap (smaller = sent first): every emergency before
     * every chat, and within emergencies the most urgent first.
     */
    fun queueKey(isChat: Boolean, urgency: Int): Int = (if (isChat) 1000 else 0) + (100 - urgency.coerceIn(0, 100))
}
