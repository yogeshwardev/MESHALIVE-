package com.meshalive.app.data

import android.content.Context
import com.meshalive.algorithms.Algorithm
import com.meshalive.algorithms.CostParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.random.Random

data class Settings(
    val nodeId: String,
    val displayName: String,
    val algorithm: Algorithm = Algorithm.DIJKSTRA,
    val alpha: Double = 1.0,
    val beta: Double = 50.0,
    val gamma: Double = 1.0,
    val ttl: Int = 15,
    val lsaIntervalSec: Int = 5,
    val webhookUrl: String = "",
    val passphrase: String = DEFAULT_PASSPHRASE,
    val gatewayMode: Boolean = false,
    val sosText: String = DEFAULT_SOS_TEXT,
    /** ntfy.sh topic the gateway pushes SOS alerts to (free, no account). */
    val cloudTopic: String = "",
    val cloudEnabled: Boolean = true,
    /** Phones we pretend are out of radio range (demo topology control). */
    val blocked: Set<String> = emptySet(),
    /** Demo override of the battery level reported in LSAs; -1 = real battery. */
    val batteryOverride: Int = -1,
    /** Rescue boat / NDRF / ambulance phone: accepts custody of SOS it cannot route yet. */
    val carrierMode: Boolean = false,
    /** Any phone that gets internet uploads the SOS it carries (acts as a gateway automatically). */
    val autoGateway: Boolean = true,
    /** "en" or "ta" (Tamil). */
    val lang: String = "en",
    // My situation, sent with every SOS
    val people: Int = 1,
    val elderly: Boolean = false,
    val children: Boolean = false,
    val injured: Boolean = false,
    val disabled: Boolean = false,
    val trapped: Boolean = false,
    val water: Int = 0,
    /** First-run welcome flow completed. */
    val onboarded: Boolean = false,
) {
    val costParams: CostParams get() = CostParams(alpha, beta, gamma)
    val cloudUrl: String get() = "https://ntfy.sh/$cloudTopic"

    companion object {
        const val DEFAULT_PASSPHRASE = "meshalive-demo"
        const val DEFAULT_SOS_TEXT = "SOS! I need urgent help at my location."
    }
}

/** Small key-value settings store backed by SharedPreferences, exposed as a StateFlow. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("meshalive_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    private fun load(): Settings {
        var nodeId = prefs.getString("nodeId", null)
        if (nodeId == null) {
            nodeId = UUID.randomUUID().toString()
            prefs.edit().putString("nodeId", nodeId).apply()
        }
        var topic = prefs.getString("cloudTopic", null)
        if (topic.isNullOrBlank()) {
            val chars = "abcdefghijkmnpqrstuvwxyz23456789"
            topic = "meshalive-" + (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
            prefs.edit().putString("cloudTopic", topic).apply()
        }
        val defaultName = "Phone-" + nodeId.take(4).uppercase()
        return Settings(
            nodeId = nodeId,
            displayName = prefs.getString("displayName", defaultName) ?: defaultName,
            algorithm = runCatching { Algorithm.valueOf(prefs.getString("algorithm", "DIJKSTRA")!!) }
                .getOrDefault(Algorithm.DIJKSTRA),
            alpha = prefs.getFloat("alpha", 1f).toDouble(),
            beta = prefs.getFloat("beta", 50f).toDouble(),
            gamma = prefs.getFloat("gamma", 1f).toDouble(),
            ttl = prefs.getInt("ttl", 15),
            lsaIntervalSec = prefs.getInt("lsaInterval", 5),
            webhookUrl = prefs.getString("webhook", "") ?: "",
            passphrase = prefs.getString("passphrase", Settings.DEFAULT_PASSPHRASE) ?: Settings.DEFAULT_PASSPHRASE,
            gatewayMode = prefs.getBoolean("gatewayMode", false),
            sosText = prefs.getString("sosText", Settings.DEFAULT_SOS_TEXT) ?: Settings.DEFAULT_SOS_TEXT,
            cloudTopic = topic,
            cloudEnabled = prefs.getBoolean("cloudEnabled", true),
            blocked = prefs.getStringSet("blocked", emptySet())?.toSet() ?: emptySet(),
            batteryOverride = prefs.getInt("batteryOverride", -1),
            carrierMode = prefs.getBoolean("carrierMode", false),
            autoGateway = prefs.getBoolean("autoGateway", true),
            lang = prefs.getString("lang", "en") ?: "en",
            people = prefs.getInt("people", 1),
            elderly = prefs.getBoolean("elderly", false),
            children = prefs.getBoolean("children", false),
            injured = prefs.getBoolean("injured", false),
            disabled = prefs.getBoolean("disabled", false),
            trapped = prefs.getBoolean("trapped", false),
            water = prefs.getInt("water", 0),
            onboarded = prefs.getBoolean("onboarded", false),
        )
    }

    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        val topic = s.cloudTopic.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        prefs.edit()
            .putString("displayName", s.displayName.ifBlank { "Phone-" + s.nodeId.take(4).uppercase() }.take(24))
            .putString("algorithm", s.algorithm.name)
            .putFloat("alpha", s.alpha.toFloat())
            .putFloat("beta", s.beta.toFloat())
            .putFloat("gamma", s.gamma.toFloat())
            .putInt("ttl", s.ttl.coerceIn(1, 64))
            .putInt("lsaInterval", s.lsaIntervalSec.coerceIn(1, 60))
            .putString("webhook", s.webhookUrl.trim())
            .putString("passphrase", s.passphrase.ifEmpty { Settings.DEFAULT_PASSPHRASE })
            .putBoolean("gatewayMode", s.gatewayMode)
            .putString("sosText", s.sosText)
            .putString("cloudTopic", topic)
            .putBoolean("cloudEnabled", s.cloudEnabled)
            .putStringSet("blocked", HashSet(s.blocked))
            .putInt("batteryOverride", if (s.batteryOverride in 1..100) s.batteryOverride else -1)
            .putBoolean("carrierMode", s.carrierMode)
            .putBoolean("autoGateway", s.autoGateway)
            .putString("lang", if (s.lang == "ta") "ta" else "en")
            .putInt("people", s.people.coerceIn(1, 99))
            .putBoolean("elderly", s.elderly)
            .putBoolean("children", s.children)
            .putBoolean("injured", s.injured)
            .putBoolean("disabled", s.disabled)
            .putBoolean("trapped", s.trapped)
            .putInt("water", s.water.coerceIn(0, 4))
            .putBoolean("onboarded", s.onboarded)
            .apply()
        _settings.value = load()
    }
}
