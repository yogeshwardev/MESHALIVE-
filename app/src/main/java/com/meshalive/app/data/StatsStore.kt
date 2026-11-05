package com.meshalive.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FieldStats(
    val since: Long = 0,
    val relayed: Int = 0,
    val peersMet: Int = 0,
    val alertsReceived: Int = 0,
    val carrierHandoffs: Int = 0,
)

/** Field-trial counters that survive restarts: evidence for "has it been tested, with what result?". */
class StatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("meshalive_stats", Context.MODE_PRIVATE)
    private val _stats = MutableStateFlow(load())
    val stats: StateFlow<FieldStats> = _stats.asStateFlow()

    private fun load(): FieldStats {
        if (!prefs.contains("since")) prefs.edit().putLong("since", System.currentTimeMillis()).apply()
        return FieldStats(
            since = prefs.getLong("since", System.currentTimeMillis()),
            relayed = prefs.getInt("relayed", 0),
            peersMet = prefs.getStringSet("peers", emptySet())?.size ?: 0,
            alertsReceived = prefs.getInt("alerts", 0),
            carrierHandoffs = prefs.getInt("handoffs", 0),
        )
    }

    @Synchronized
    private fun bump(key: String) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
        _stats.value = load()
    }

    fun relayed() = bump("relayed")
    fun alertReceived() = bump("alerts")
    fun carrierHandoff() = bump("handoffs")

    @Synchronized
    fun peerMet(nodeId: String) {
        val set = prefs.getStringSet("peers", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (set.add(nodeId)) {
            prefs.edit().putStringSet("peers", set).apply()
            _stats.value = load()
        }
    }

    @Synchronized
    fun reset() {
        prefs.edit().clear().putLong("since", System.currentTimeMillis()).apply()
        _stats.value = load()
    }
}
