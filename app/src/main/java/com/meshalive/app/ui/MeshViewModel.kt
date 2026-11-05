package com.meshalive.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshalive.algorithms.ANY_GATEWAY
import com.meshalive.app.MeshAliveApp
import com.meshalive.app.data.InboxEntity
import com.meshalive.app.data.MessageEntity
import com.meshalive.app.data.Settings
import com.meshalive.app.mesh.Flight
import com.meshalive.app.mesh.LogEvent
import com.meshalive.app.mesh.MeshState
import com.meshalive.app.mesh.PacketType
import com.meshalive.app.mesh.TriageInfo
import com.meshalive.algorithms.Triage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeviceFlags(val bluetoothOn: Boolean = true, val locationOn: Boolean = true, val locationPermission: Boolean = true)

class MeshViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MeshAliveApp
    private val mesh = app.mesh

    val state: StateFlow<MeshState> = mesh.state
    val settings: StateFlow<Settings> = app.settings.settings
    val myId: String get() = mesh.myId
    val log: StateFlow<List<LogEvent>> = mesh.log
    val flights: SharedFlow<Flight> = mesh.flights

    val messages: StateFlow<List<MessageEntity>> =
        app.db.messages().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val inbox: StateFlow<List<InboxEntity>> =
        app.db.inbox().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val latestAlert: StateFlow<MessageEntity?> =
        app.db.messages().observeLatestAlert().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val fieldStats: StateFlow<com.meshalive.app.data.FieldStats> = app.stats.stats
    val pendingCount: StateFlow<Int> =
        app.db.pending().observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _flags = MutableStateFlow(DeviceFlags())
    val flags: StateFlow<DeviceFlags> = _flags.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                refreshFlags()
                delay(3_000)
            }
        }
    }

    fun refreshFlags() {
        val d = app.device
        val new = DeviceFlags(d.isBluetoothOn(), d.isLocationOn(), d.hasLocationPermission())
        val wasOff = !_flags.value.bluetoothOn
        _flags.value = new
        if (wasOff && new.bluetoothOn) mesh.restartRadio()
    }

    /** The saved situation report, scored with the given base urgency. */
    fun triageFor(base: Int, adjust: (TriageInfo) -> TriageInfo = { it }): TriageInfo {
        val s = settings.value
        val t = adjust(TriageInfo(s.people, s.elderly, s.children, s.injured, s.disabled, s.trapped, s.water))
        return t.copy(urgency = Triage.urgency(base, t.people, t.elderly, t.children, t.injured, t.disabled, t.trapped, t.water))
    }

    /** One-tap SOS / quick messages to the nearest gateway, with GPS and the situation report. */
    fun sendEmergency(type: PacketType, text: String, base: Int = Triage.BASE_SOS, adjust: (TriageInfo) -> TriageInfo = { it }) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            app.notifier.vibrate(longArrayOf(0, 120, 80, 120))
            val loc = app.device.currentLocation()
            val triage = triageFor(base, adjust)
            mesh.sendMessage(type, text, ANY_GATEWAY, loc?.latitude, loc?.longitude, triage)
            _busy.value = false
            val ta = settings.value.lang == "ta"
            _toast.value = when {
                ta && loc == null -> "${type.name} அனுப்பப்பட்டது (இருப்பிடம் இல்லை)"
                ta -> "${type.name} இருப்பிடத்துடன் அனுப்பப்பட்டது"
                loc == null -> "${type.name} sent · urgency ${triage.urgency} (no GPS fix)"
                else -> "${type.name} sent with location · urgency ${triage.urgency}"
            }
        }
    }

    /** "I'm safe" check-in: tells the control room this household needs no rescue. */
    fun sendSafe() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val loc = app.device.currentLocation()
            val s = settings.value
            val text = if (s.lang == "ta") "நாங்கள் பாதுகாப்பாக உள்ளோம்" else "We are safe"
            mesh.sendMessage(PacketType.SAFE, text, ANY_GATEWAY, loc?.latitude, loc?.longitude, TriageInfo(people = s.people))
            _busy.value = false
            _toast.value = if (s.lang == "ta") "பாதுகாப்பு நிலை அனுப்பப்பட்டது" else "Marked safe — the control room will know"
        }
    }

    fun sendChat(dest: String, text: String) {
        if (text.isBlank()) return
        mesh.sendMessage(PacketType.CHAT, text.trim(), dest, null, null)
    }

    fun setGateway(on: Boolean) = app.settings.update { it.copy(gatewayMode = on) }

    fun updateSettings(transform: (Settings) -> Settings) = app.settings.update(transform)

    fun clearData() {
        viewModelScope.launch {
            mesh.clearData()
            _toast.value = "All messages and queues cleared"
        }
    }

    fun consumeToast() { _toast.value = null }

    /** Demo control: pretend a phone is out of radio range. */
    fun setLinkBlocked(nodeId: String, blocked: Boolean) =
        app.settings.update { it.copy(blocked = if (blocked) it.blocked + nodeId else it.blocked - nodeId) }

    fun sendCloudTest() {
        viewModelScope.launch {
            _toast.value = "Sending test alert…"
            _toast.value = mesh.sendCloudTest()
        }
    }

    fun nameOf(id: String) = mesh.nameOf(id)

    /** Writes the field-trial report (markdown) to Downloads, for the submission or a pilot review. */
    fun exportEvidence() {
        viewModelScope.launch {
            val msgs = messages.value
            val inb = inbox.value
            val e = com.meshalive.app.data.Evidence.summarize(msgs, inb, app.stats.stats.value)
            val text = com.meshalive.app.data.Evidence.report(settings.value.displayName, e, msgs, inb)
            val name = "meshalive_field_report_" +
                java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date()) + ".md"
            _toast.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Downloads.saveText(app, name, "text/markdown", text)
            }
        }
    }

    fun resetFieldStats() {
        app.stats.reset()
        _toast.value = "Field-trial counters reset"
    }
}
