package com.meshalive.app.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.meshalive.algorithms.ANY_GATEWAY
import com.meshalive.algorithms.Algorithm
import com.meshalive.algorithms.BfsStrategy
import com.meshalive.algorithms.Decision
import com.meshalive.algorithms.DijkstraStrategy
import com.meshalive.algorithms.ForwardingEngine
import com.meshalive.algorithms.Graph
import com.meshalive.algorithms.NodeInfo
import com.meshalive.algorithms.PacketHeader
import com.meshalive.algorithms.PacketPriorityQueue
import com.meshalive.algorithms.Triage
import com.meshalive.algorithms.targetsFor
import com.meshalive.app.data.InboxEntity
import com.meshalive.app.data.MeshDatabase
import com.meshalive.app.data.MessageEntity
import com.meshalive.app.data.MsgStatus
import com.meshalive.app.data.PendingPacketEntity
import com.meshalive.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.Executors

data class NeighbourUi(val nodeId: String, val name: String, val rttMs: Double?)

/** A phone this one can see over the radio (connected or not, blocked or not). */
data class PeerUi(val nodeId: String, val name: String, val connected: Boolean, val blocked: Boolean, val rttMs: Double?)

enum class LogKind { LINK, LSA, ROUTE, DELIVER, ACK, DROP, STORE, CLOUD, INFO }

data class LogEvent(val time: Long, val kind: LogKind, val text: String)

/** One packet crossing one link, for the live animation on the network map. */
data class Flight(val from: String, val to: String, val type: PacketType, val at: Long = System.currentTimeMillis())

data class MeshState(
    val running: Boolean = false,
    val neighbours: List<NeighbourUi> = emptyList(),
    val peers: List<PeerUi> = emptyList(),
    val graph: Graph = Graph(),
    val names: Map<String, String> = emptyMap(),
    val routeToGateway: List<String> = emptyList(),
    val gatewayReachable: Boolean = false,
    val battery: Int = 100,
    val reportedBattery: Int = 100,
    val hasInternet: Boolean = false,
    val isGateway: Boolean = false,
    val error: String? = null,
    val relayedCount: Int = 0,
)

/**
 * The mesh networking layer. Owns the Nearby Connections client, the link-state
 * database (LSDB) built from LSAs, RTT measurement, and packet forwarding.
 *
 * All state mutation happens on one single-threaded dispatcher, so the maps below
 * need no locks.
 */
class MeshManager(
    private val context: Context,
    private val db: MeshDatabase,
    private val settingsRepo: SettingsRepository,
    val device: DeviceStatus,
    private val notifier: Notifier,
    private val stats: com.meshalive.app.data.StatsStore,
) {
    private val tag = "MeshManager"
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var loops: Job? = null
    private var collectorsStarted = false

    private val client: ConnectionsClient by lazy { Nearby.getConnectionsClient(context) }

    private val settings get() = settingsRepo.current
    val myId: String get() = settings.nodeId

    // ---- radio bookkeeping ----
    private class Neighbour(val endpointId: String, val nodeId: String, var name: String) {
        var rttEwma: Double? = null
    }

    private class Discovered(val endpointId: String, val nodeId: String, var name: String, val foundAt: Long) {
        var lastAttempt = 0L
        var attempts = 0
    }

    private class LsaRecord(val lsa: Lsa, val lastHeard: Long)

    private val endpoints = HashMap<String, Neighbour>()     // endpointId -> neighbour (handshake or connected)
    private val connected = HashSet<String>()                 // endpointIds fully connected
    private val discovered = HashMap<String, Discovered>()    // endpointIds seen by discovery
    private val pendingRequests = HashSet<String>()           // endpointIds we requested
    private val lsdb = HashMap<String, LsaRecord>()           // nodeId -> latest LSA
    private val names = HashMap<String, String>()
    private var mySeq = 0L
    private var graph = Graph()
    private var lastRadioActivity = System.currentTimeMillis()
    private var relayed = 0

    private val engine = ForwardingEngine(settings.algorithm.strategy(settings.costParams))
    private val outQueue = PacketPriorityQueue<Pair<Packet, List<String>>> { Triage.queueKey(it.first.type == PacketType.CHAT || it.first.type == PacketType.SAFE, it.first.urgency) }

    /** (msgId, carrier nodeId) pairs already handed over, so custody is transferred once per carrier. */
    private val handedToCarrier = HashSet<Pair<String, String>>()

    /** Recent control-room alerts, re-shared with every phone that comes into range (alert gossip). */
    private val recentAlerts = ArrayList<Packet>()

    /** Control-room command channel position (ntfy message id) and commands already applied. */
    private var cmdSince = "15m"
    private val seenCommands = HashSet<String>()

    private var crypto: Crypto? = null
    private var cryptoPass: String? = null

    private val _state = MutableStateFlow(MeshState())
    val state: StateFlow<MeshState> = _state.asStateFlow()

    private val logBuffer = ArrayDeque<LogEvent>()
    private val _log = MutableStateFlow<List<LogEvent>>(emptyList())
    val log: StateFlow<List<LogEvent>> = _log.asStateFlow()

    private val _flights = MutableSharedFlow<Flight>(extraBufferCapacity = 128)
    val flights: SharedFlow<Flight> = _flights.asSharedFlow()

    private fun crypto(): Crypto {
        val pass = settings.passphrase
        if (crypto == null || cryptoPass != pass) {
            crypto = Crypto(pass); cryptoPass = pass
        }
        return crypto!!
    }

    private fun log(kind: LogKind, text: String) {
        logBuffer.addFirst(LogEvent(System.currentTimeMillis(), kind, text))
        while (logBuffer.size > 400) logBuffer.removeLast()
        _log.value = logBuffer.toList()
    }

    // ===================================================================== lifecycle

    fun start() {
        scope.launch {
            if (_state.value.running) return@launch
            device.startInternetMonitor()
            names[myId] = settings.displayName
            _state.value = _state.value.copy(running = true, error = null)
            log(LogKind.INFO, "Relay started as ${settings.displayName} · routing = ${settings.algorithm.label}")
            startNearby()
            loops = scope.launch { runLoops() }
            if (collectorsStarted) return@launch
            collectorsStarted = true
            scope.launch {
                settingsRepo.settings.map { listOf(it.algorithm, it.costParams, it.gatewayMode, it.autoGateway, it.carrierMode) }
                    .distinctUntilChanged().collect {
                        val alg = settings.algorithm
                        val gw = isGateway()
                        engine.strategy = alg.strategy(settings.costParams)
                        log(LogKind.INFO, "Routing = ${alg.label}" + (if (gw) " · this phone is a GATEWAY" else "") +
                            if (settings.carrierMode) " · RESCUE CARRIER mode" else "")
                        rebuildGraph()
                        sendLsa()
                        retryPending()
                        if (gw) retryCloud()
                    }
            }
            scope.launch {
                settingsRepo.settings.map { it.blocked }.distinctUntilChanged().collect { applyBlocks(it) }
            }
            scope.launch {
                var first = true
                settingsRepo.settings.map { it.displayName }.distinctUntilChanged().collect { name ->
                    names[myId] = name
                    if (!first) {
                        // Re-advertise under the new name.
                        runCatching { client.stopAdvertising() }
                        client.startAdvertising(endpointName, SERVICE_ID, lifecycleCallback,
                            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
                        sendLsa()
                    }
                    first = false
                }
            }
            scope.launch {
                settingsRepo.settings.map { it.batteryOverride }.distinctUntilChanged().collect { sendLsa(); publish() }
            }
            scope.launch {
                device.hasInternet.collect { online ->
                    if (settings.autoGateway) {
                        // Signal came back (or was lost): this phone may have become a gateway.
                        log(LogKind.CLOUD, if (online) "📶 Internet available — uploading any SOS this phone carries" else "📵 Internet lost")
                        rebuildGraph()
                        sendLsa()
                        retryPending()
                    }
                    publish()
                    if (online && isGateway()) retryCloud()
                }
            }
        }
    }

    fun stop() {
        scope.launch {
            loops?.cancel()
            runCatching {
                client.stopAdvertising(); client.stopDiscovery(); client.stopAllEndpoints()
            }
            endpoints.clear(); connected.clear(); discovered.clear(); pendingRequests.clear()
            _state.value = _state.value.copy(running = false, neighbours = emptyList(), peers = emptyList())
            log(LogKind.INFO, "Relay stopped")
        }
    }

    /** Restart advertising/discovery, e.g. after the user turns Bluetooth on. */
    fun restartRadio() {
        scope.launch {
            runCatching { client.stopAdvertising(); client.stopDiscovery() }
            discovered.clear()
            startNearby()
            log(LogKind.LINK, "Radio restarted")
        }
    }

    private val endpointName: String get() = "$myId|${settings.displayName.replace("|", " ")}"

    private fun startNearby() {
        val strategy = Strategy.P2P_CLUSTER
        _state.value = _state.value.copy(error = null)
        try {
            client.startAdvertising(endpointName, SERVICE_ID, lifecycleCallback,
                AdvertisingOptions.Builder().setStrategy(strategy).build())
                .addOnFailureListener { e -> reportError("Advertising failed: ${friendly(e)}") }
            startDiscovery()
        } catch (e: Exception) {
            reportError("Nearby unavailable: ${e.message}")
        }
    }

    private fun startDiscovery() {
        client.startDiscovery(SERVICE_ID, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
            .addOnFailureListener { e -> reportError("Discovery failed: ${friendly(e)}") }
    }

    private fun friendly(e: Exception): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("8002") || msg.contains("8001") -> "already running"
            msg.contains("MISSING_PERMISSION", true) || msg.contains("8032") -> "permission missing"
            msg.contains("BLUETOOTH", true) || msg.contains("8007") -> "turn on Bluetooth"
            else -> msg
        }
    }

    private fun reportError(msg: String) {
        if (msg.contains("already running")) return
        Log.w(tag, msg)
        scope.launch { log(LogKind.INFO, "⚠ $msg") }
        _state.value = _state.value.copy(error = msg)
    }

    // ===================================================================== Nearby callbacks

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            scope.launch {
                lastRadioActivity = System.currentTimeMillis()
                val parts = info.endpointName.split("|", limit = 2)
                val remoteId = parts[0]
                if (remoteId == myId) return@launch
                val name = parts.getOrElse(1) { remoteId.take(6) }
                names[remoteId] = name
                discovered[endpointId] = Discovered(endpointId, remoteId, name, System.currentTimeMillis())
                log(LogKind.LINK, "Found $name nearby" + if (remoteId in settings.blocked) " (link blocked)" else "")
                maybeConnect(discovered.getValue(endpointId))
                publish()
            }
        }

        override fun onEndpointLost(endpointId: String) {
            scope.launch {
                discovered.remove(endpointId)?.let { if (endpointId !in connected) log(LogKind.LINK, "Lost sight of ${it.name}") }
                pendingRequests -= endpointId
                publish()
            }
        }
    }

    private fun isConnectedTo(nodeId: String) = connected.any { endpoints[it]?.nodeId == nodeId }

    /**
     * Connection policy: the phone with the smaller nodeId asks first; the other waits
     * 5 s and then asks too (in case it was not discovered). Failed attempts back off
     * exponentially and are retried by the periodic loop, so links self-heal.
     */
    private fun maybeConnect(d: Discovered) {
        val now = System.currentTimeMillis()
        if (d.nodeId in settings.blocked) return
        if (endpoints.containsKey(d.endpointId) || d.endpointId in pendingRequests || isConnectedTo(d.nodeId)) return
        if (myId > d.nodeId && now - d.foundAt < 5_000) return
        val backoff = minOf(2_000L shl minOf(d.attempts, 4), 30_000L)
        if (d.attempts > 0 && now - d.lastAttempt < backoff) return
        d.lastAttempt = now
        d.attempts++
        pendingRequests += d.endpointId
        client.requestConnection(endpointName, d.endpointId, lifecycleCallback)
            .addOnFailureListener { e ->
                scope.launch {
                    pendingRequests -= d.endpointId
                    val m = e.message ?: ""
                    if (!m.contains("8003")) log(LogKind.LINK, "Connect to ${d.name} failed (${friendly(e)}), retrying")
                }
            }
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            scope.launch {
                val parts = info.endpointName.split("|", limit = 2)
                val nodeId = parts[0]
                val name = parts.getOrElse(1) { nodeId.take(6) }
                names[nodeId] = name
                if (nodeId in settings.blocked || isConnectedTo(nodeId)) {
                    runCatching { client.rejectConnection(endpointId) }
                    return@launch
                }
                endpoints[endpointId] = Neighbour(endpointId, nodeId, name)
                // Auto-accept every MeshAlive peer.
                client.acceptConnection(endpointId, payloadCallback)
                    .addOnFailureListener { scope.launch { endpoints.remove(endpointId) } }
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            scope.launch {
                pendingRequests -= endpointId
                val code = result.status.statusCode
                if (code == ConnectionsStatusCodes.STATUS_OK) {
                    connected += endpointId
                    lastRadioActivity = System.currentTimeMillis()
                    val n = endpoints[endpointId]
                    discovered.values.firstOrNull { it.nodeId == n?.nodeId }?.attempts = 0
                    log(LogKind.LINK, "🔗 Connected to ${n?.name ?: endpointId}")
                    n?.let { stats.peerMet(it.nodeId) }
                    notifier.vibrate(longArrayOf(0, 40, 60, 40))
                    n?.let { sendPing(it) }
                    sendLsa()
                    shareAlertsWith(endpointId)
                    onTopologyChanged()
                } else {
                    endpoints.remove(endpointId)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            scope.launch { dropLink(endpointId, "Link to %s lost") }
        }
    }

    private fun dropLink(endpointId: String, message: String) {
        val n = endpoints.remove(endpointId)
        val wasConnected = connected.remove(endpointId)
        pendingRequests -= endpointId
        if (wasConnected) log(LogKind.LINK, "✂ " + message.format(n?.name ?: endpointId))
        // Forget our view of that phone's links too, and let discovery reconnect later.
        discovered[endpointId]?.let { it.attempts = 0; it.lastAttempt = System.currentTimeMillis() }
        onTopologyChanged()
    }

    /** Demo control: cut links to phones the user marked "out of range". */
    private fun applyBlocks(blocked: Set<String>) {
        endpoints.values.filter { it.nodeId in blocked }.map { it.endpointId }.forEach { ep ->
            runCatching { client.disconnectFromEndpoint(ep) }
            dropLink(ep, "Link to %s cut (pretend out of range)")
        }
        discovered.values.filter { it.nodeId !in blocked }.forEach { it.attempts = 0; maybeConnect(it) }
        publish()
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            scope.launch {
                val packet = Packet.fromBytes(bytes) ?: return@launch
                val from = endpoints[endpointId]?.nodeId ?: return@launch
                lastRadioActivity = System.currentTimeMillis()
                handlePacket(packet, from, endpointId)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    // ===================================================================== periodic work

    private suspend fun runLoops() {
        var tick = 0L
        while (scope.isActive) {
            val lsaEvery = settings.lsaIntervalSec.coerceAtLeast(1)
            if (tick % lsaEvery == 0L) sendLsa()
            if (tick % 3 == 0L) connectedNeighbours().forEach { sendPing(it) }
            if (tick % 2 == 0L) discovered.values.toList().forEach { maybeConnect(it) }
            if (tick % 5 == 0L) expireStale()
            if (tick % 30 == 0L) {
                db.messages().failStale(System.currentTimeMillis() - FAIL_AFTER_MS)
                db.pending().deleteOlderThan(System.currentTimeMillis() - PENDING_MAX_AGE_MS)
                retryPending()
                if (isGateway() && device.hasInternet.value) retryCloud()
            }
            // Nothing seen for a while: restart discovery (Nearby sometimes goes quiet).
            if (connected.isEmpty() && System.currentTimeMillis() - lastRadioActivity > 40_000) {
                lastRadioActivity = System.currentTimeMillis()
                runCatching { client.stopDiscovery() }
                discovered.clear()
                startDiscovery()
                log(LogKind.LINK, "No phones in range, scanning again…")
            }
            if (tick % 8 == 0L && isGateway() && device.hasInternet.value && settings.cloudTopic.isNotBlank()) pollControlRoom()
            if (tick % 2 == 0L) publish()
            tick++
            delay(1_000)
        }
    }

    private fun expireStale() {
        val now = System.currentTimeMillis()
        val dead = lsdb.filter { now - it.value.lastHeard > NODE_EXPIRY_MS }.keys
        if (dead.isNotEmpty()) {
            dead.forEach { lsdb.remove(it); log(LogKind.LSA, "${nameOf(it)} expired (silent 30 s)") }
            onTopologyChanged()
        }
    }

    // ===================================================================== LSA + RTT

    fun reportedBattery(): Int = settings.batteryOverride.takeIf { it in 1..100 } ?: device.batteryPercent()

    private fun myLsa(): Lsa = Lsa(
        nodeId = myId,
        name = settings.displayName,
        seqNo = ++mySeq,
        batteryPercent = reportedBattery(),
        hasInternet = isGateway(),
        neighbours = connectedNeighbours().map { NeighbourLink(it.nodeId, it.rttEwma ?: DEFAULT_RTT) },
        isCarrier = settings.carrierMode,
    )

    private fun connectedNeighbours(): List<Neighbour> = endpoints.values.filter { it.endpointId in connected }

    fun isGateway(): Boolean = settings.gatewayMode || (settings.autoGateway && device.hasInternet.value)

    private fun isCarrier(nodeId: String): Boolean = lsdb[nodeId]?.lsa?.isCarrier == true

    /**
     * Store-carry-forward (delay-tolerant networking): when an SOS has no route, give a
     * copy to every connected rescue carrier. The carrier keeps it and delivers it when
     * it later comes into range of a gateway, or gets signal itself.
     */
    private suspend fun handToCarriers(packet: Packet) {
        if (packet.dest != ANY_GATEWAY || !packet.type.isUserMessage) return
        for (n in connectedNeighbours()) {
            if (!isCarrier(n.nodeId) || n.nodeId in packet.hops) continue
            if (!handedToCarrier.add(packet.msgId to n.nodeId)) continue
            sendRaw(n.endpointId, packet)
            log(LogKind.STORE, "🚤 Handed ${packet.type} from ${nameOf(packet.src)} to rescue carrier ${n.name} (custody transfer)")
            stats.carrierHandoff()
            if (packet.src == myId) db.messages().setStatus(packet.msgId, MsgStatus.CARRIED)
        }
    }

    private fun sendLsa() {
        if (connected.isEmpty()) return
        val lsa = myLsa()
        val payload = MeshJson.encodeToString(lsa)
        val p = Packet(
            msgId = "lsa-$myId-${lsa.seqNo}", type = PacketType.LSA, priority = 0, src = myId, dest = "ALL",
            ttl = settings.ttl, hops = listOf(myId), payload = payload, timestamp = System.currentTimeMillis(),
        )
        connected.forEach { sendRaw(it, p) }
    }

    private fun handleLsa(packet: Packet, fromNodeId: String) {
        val lsa = runCatching { MeshJson.decodeFromString(Lsa.serializer(), packet.payload) }.getOrNull() ?: return
        if (lsa.nodeId == myId) return
        val known = lsdb[lsa.nodeId]
        if (known != null && known.lsa.seqNo >= lsa.seqNo && lsa.seqNo > 2) return // old or duplicate
        val topologyChanged = known == null ||
            known.lsa.neighbours.map { it.nodeId }.toSet() != lsa.neighbours.map { it.nodeId }.toSet() ||
            known.lsa.hasInternet != lsa.hasInternet || known.lsa.isCarrier != lsa.isCarrier ||
            (known.lsa.batteryPercent < 20) != (lsa.batteryPercent < 20)
        lsdb[lsa.nodeId] = LsaRecord(lsa, System.currentTimeMillis())
        names[lsa.nodeId] = lsa.name
        if (known == null) log(LogKind.LSA, "Learned ${lsa.name} (${lsa.neighbours.size} links, ${lsa.batteryPercent}% battery${if (lsa.hasInternet) ", GATEWAY" else ""}${if (lsa.isCarrier) ", RESCUE CARRIER" else ""})")
        else if (topologyChanged) log(LogKind.LSA, "${lsa.name} changed: ${lsa.neighbours.size} links, ${lsa.batteryPercent}%${if (lsa.hasInternet) ", gateway" else ""}")
        // Forward (controlled flooding by sequence number) to everyone except the sender.
        if (packet.ttl > 1) {
            val fwd = packet.copy(ttl = packet.ttl - 1, hops = packet.hops + myId)
            connectedNeighbours().filter { it.nodeId != fromNodeId && it.nodeId !in packet.hops }
                .forEach { sendRaw(it.endpointId, fwd) }
        }
        rebuildGraph()
        if (topologyChanged) retryPending() else publish()
    }

    private fun sendPing(n: Neighbour) {
        val now = System.currentTimeMillis()
        val body = MeshJson.encodeToString(PingBody(now))
        sendRaw(n.endpointId, Packet("ping-$myId-$now", PacketType.PING, 0, myId, n.nodeId, 1, listOf(myId), payload = body, timestamp = now))
    }

    private fun handlePing(packet: Packet, endpointId: String) {
        val now = System.currentTimeMillis()
        sendRaw(endpointId, packet.copy(msgId = "pong-${packet.msgId}", type = PacketType.PONG, src = myId, dest = packet.src, timestamp = now))
    }

    private fun handlePong(packet: Packet, endpointId: String) {
        val body = runCatching { MeshJson.decodeFromString(PingBody.serializer(), packet.payload) }.getOrNull() ?: return
        val sample = (System.currentTimeMillis() - body.sentAt).toDouble().coerceAtLeast(1.0)
        val n = endpoints[endpointId] ?: return
        // EWMA smoothing like TCP's SRTT: srtt = (1 - a) * srtt + a * sample, a = 1/8
        n.rttEwma = n.rttEwma?.let { 0.875 * it + 0.125 * sample } ?: sample
    }

    // ===================================================================== graph

    private fun rebuildGraph() {
        val g = Graph()
        val now = System.currentTimeMillis()
        g.addNode(NodeInfo(myId, reportedBattery(), isGateway()))
        for ((id, rec) in lsdb) {
            if (now - rec.lastHeard <= NODE_EXPIRY_MS) g.addNode(NodeInfo(id, rec.lsa.batteryPercent, rec.lsa.hasInternet))
        }
        connectedNeighbours().forEach { n ->
            if (!g.contains(n.nodeId)) g.addNode(NodeInfo(n.nodeId, 100, false))
            g.addEdge(myId, n.nodeId, n.rttEwma ?: DEFAULT_RTT)
        }
        val mine = connectedNeighbours().map { it.nodeId }.toSet()
        for ((id, rec) in lsdb) {
            if (!g.contains(id)) continue
            for (link in rec.lsa.neighbours) {
                // My own links come from live measurements; ignore stale claims of links to me.
                if (link.nodeId == myId) continue
                if (g.contains(link.nodeId)) g.addEdge(id, link.nodeId, link.linkCost)
            }
        }
        // Drop nodes no longer reachable from me at all (stale islands).
        val seen = HashSet<String>().apply { add(myId) }
        val stack = ArrayDeque<String>().apply { add(myId) }
        while (stack.isNotEmpty()) for (v in g.neighbours(stack.removeLast())) if (seen.add(v)) stack.add(v)
        g.nodeIds.toList().filter { it !in seen && it !in mine }.forEach { g.removeNode(it) }
        graph = g
        publish()
    }

    private fun onTopologyChanged() {
        rebuildGraph()
        sendLsa()
        retryPending()
    }

    private fun publish() {
        val g = graph
        val route = runCatching { engine.strategy.route(g, myId, ANY_GATEWAY) }.getOrDefault(emptyList())
        val blocked = settings.blocked
        val peerIds = LinkedHashSet<String>()
        connectedNeighbours().forEach { peerIds += it.nodeId }
        discovered.values.forEach { peerIds += it.nodeId }
        blocked.filter { names.containsKey(it) }.forEach { peerIds += it }
        val peers = peerIds.map { id ->
            val n = connectedNeighbours().firstOrNull { it.nodeId == id }
            PeerUi(id, names[id] ?: id.take(6), n != null, id in blocked, n?.rttEwma)
        }
        _state.value = _state.value.copy(
            neighbours = connectedNeighbours().map { NeighbourUi(it.nodeId, names[it.nodeId] ?: it.name, it.rttEwma) },
            peers = peers,
            graph = g.copy(),
            names = HashMap(names),
            routeToGateway = route,
            gatewayReachable = isGateway() || route.isNotEmpty(),
            battery = device.batteryPercent(),
            reportedBattery = reportedBattery(),
            hasInternet = device.hasInternet.value,
            isGateway = isGateway(),
            relayedCount = relayed,
        )
    }

    fun nameOf(id: String): String = when (id) {
        myId -> settings.displayName
        ANY_GATEWAY -> "nearest gateway"
        else -> names[id] ?: id.take(6)
    }

    /** Human explanation of the routing decision, for the live log. */
    private fun explain(dest: String): String = runCatching {
        when (settings.algorithm) {
            Algorithm.FLOODING -> "flooding to all neighbours"
            Algorithm.BFS -> {
                val t = BfsStrategy.computeRoutes(graph, targetsFor(graph, dest))
                "BFS: ${t.hops[myId]} hop(s) to target"
            }
            Algorithm.DIJKSTRA -> {
                val t = DijkstraStrategy(settings.costParams).computeRoutes(graph, targetsFor(graph, dest))
                "Dijkstra: path cost %.2f, %d hop(s) — %s".format(
                    t.cost[myId] ?: 0.0, t.hops[myId] ?: 0, t.pathFrom(myId).joinToString("→") { nameOf(it) })
            }
        }
    }.getOrDefault("")

    // ===================================================================== sending

    private fun sendRaw(endpointId: String, packet: Packet) {
        runCatching { client.sendPayload(endpointId, Payload.fromBytes(packet.toBytes())) }
            .onFailure { Log.w(tag, "send failed: ${it.message}") }
        if (packet.type.isUserMessage || packet.type == PacketType.ACK || packet.type == PacketType.ALERT) {
            endpoints[endpointId]?.let { _flights.tryEmit(Flight(myId, it.nodeId, packet.type)) }
        }
    }

    private fun endpointFor(nodeId: String): String? =
        connectedNeighbours().firstOrNull { it.nodeId == nodeId }?.endpointId

    private fun enqueue(packet: Packet, to: List<String>) {
        outQueue.offer(packet to to)
        drainQueue()
    }

    /** Sends queued packets in priority order: SOS, then MEDICAL, then CHAT. */
    private fun drainQueue() {
        while (true) {
            val (packet, to) = outQueue.poll() ?: break
            val missing = to.filter { endpointFor(it) == null }
            to.mapNotNull { endpointFor(it) }.forEach { sendRaw(it, packet) }
            if (missing.size == to.size) scope.launch { store(packet, packet.hops.dropLast(1).lastOrNull()) }
        }
    }

    private suspend fun store(packet: Packet, cameFrom: String?) {
        db.pending().upsert(PendingPacketEntity(packet.msgId, packet.toJson(), packet.priority, cameFrom, System.currentTimeMillis()))
    }

    /** Store-and-forward: whenever the graph changes, try every queued packet again. */
    private fun retryPending() {
        scope.launch {
            for (p in db.pending().all()) {
                val packet = Packet.fromJson(p.packetJson)
                if (packet == null) { db.pending().delete(p.msgId); continue }
                if (packet.type == PacketType.ACK) {
                    if (routeAck(packet, fromStore = true)) db.pending().delete(p.msgId)
                    continue
                }
                if (packet.dest == myId || (packet.dest == ANY_GATEWAY && isGateway())) {
                    // This phone became the destination while the packet waited (e.g. gateway switched on).
                    db.pending().delete(p.msgId)
                    deliver(if (packet.hops.lastOrNull() == myId) packet else packet.copy(hops = packet.hops + myId))
                    continue
                }
                when (val d = engine.route(graph, myId, header(packet), p.cameFrom, decrement = false)) {
                    is Decision.Forward -> {
                        db.pending().delete(p.msgId)
                        if (packet.src == myId) db.messages().setStatus(packet.msgId, MsgStatus.RELAYING)
                        log(LogKind.STORE, "📤 Stored ${packet.type} from ${nameOf(packet.src)} released → ${d.to.joinToString { nameOf(it) }}")
                        enqueue(packet.copy(ttl = d.newTtl, hops = d.newHops), d.to)
                    }
                    is Decision.Drop -> db.pending().delete(p.msgId)
                    is Decision.Store -> handToCarriers(packet)
                    else -> Unit
                }
            }
        }
    }

    private fun header(p: Packet) = PacketHeader(p.msgId, p.src, p.dest, p.ttl, p.hops)

    // ===================================================================== receiving

    private suspend fun handlePacket(packet: Packet, fromNodeId: String, endpointId: String) {
        lsdb[fromNodeId]?.let { lsdb[fromNodeId] = LsaRecord(it.lsa, System.currentTimeMillis()) }
        if (packet.type.isUserMessage || packet.type == PacketType.ACK || packet.type == PacketType.ALERT) _flights.tryEmit(Flight(fromNodeId, myId, packet.type))
        when (packet.type) {
            PacketType.LSA -> handleLsa(packet, fromNodeId)
            PacketType.PING -> handlePing(packet, endpointId)
            PacketType.PONG -> handlePong(packet, endpointId)
            PacketType.ACK -> {
                if (!engine.duplicates.markIfNew(packet.msgId)) return
                routeAck(packet, fromStore = false)
            }
            PacketType.ALERT -> handleAlert(packet, fromNodeId)
            else -> handleData(packet, fromNodeId)
        }
    }

    private suspend fun handleData(packet: Packet, cameFrom: String?) {
        val label = "${packet.type} from ${nameOf(packet.src)}" + if (packet.urgency > 0) " [urgency ${packet.urgency}]" else ""
        when (val d = engine.onReceive(graph, myId, isGateway(), header(packet), cameFrom)) {
            is Decision.Drop -> {
                if (d.reason != "duplicate" || settings.algorithm != Algorithm.FLOODING) log(LogKind.DROP, "🗑 Dropped $label: ${d.reason}")
            }
            is Decision.Deliver -> deliver(packet.copy(hops = if (packet.hops.lastOrNull() == myId) packet.hops else packet.hops + myId))
            is Decision.Forward -> {
                if (packet.src == myId) {
                    db.messages().setStatus(packet.msgId, MsgStatus.RELAYING)
                    log(LogKind.ROUTE, "🚀 Sending $label → ${d.to.joinToString { nameOf(it) }} · ${explain(packet.dest)}")
                } else {
                    relayed++
                    stats.relayed()
                    log(LogKind.ROUTE, "↪ Relaying $label (TTL ${d.newTtl}) → ${d.to.joinToString { nameOf(it) }} · ${explain(packet.dest)}")
                    if (packet.type != PacketType.CHAT) notifier.relayed(packet.type.name, nameOf(packet.src), d.to.joinToString { nameOf(it) })
                }
                enqueue(packet.copy(ttl = d.newTtl, hops = d.newHops), d.to)
            }
            is Decision.Store -> {
                if (packet.src == myId) db.messages().setStatus(packet.msgId, MsgStatus.QUEUED)
                log(LogKind.STORE, "📦 No route for $label — stored, will retry when the mesh changes")
                val stored = packet.copy(ttl = d.newTtl, hops = d.newHops)
                store(stored, cameFrom)
                handToCarriers(stored)
            }
        }
    }

    private suspend fun deliver(packet: Packet) {
        val content = crypto().decrypt(packet.payload)?.let {
            runCatching { MeshJson.decodeFromString(Content.serializer(), it) }.getOrNull()
        } ?: Content(nameOf(packet.src), "[cannot decrypt - different passphrase]")
        val now = System.currentTimeMillis()
        val pathNames = packet.hops.joinToString(" → ") { nameOf(it) }
        val hops = packet.hops.size - 1
        log(LogKind.DELIVER, "🎯 Delivered ${packet.type} from ${content.name} after $hops hop(s): $pathNames")

        if (packet.type == PacketType.UPDATE) {
            // Word from the control room about my SOS, carried back through the mesh.
            val info = content.rescue
            if (info != null) {
                db.messages().setRescue(info.forMsgId, info.status, content.text)
                log(LogKind.DELIVER, "🚤 Control room: ${RescueStatus.label(info.status)}" + if (content.text.isNotBlank()) " — ${content.text}" else "")
                notifier.rescueUpdate(RescueStatus.label(info.status), content.text)
            }
            if (packet.src != myId) sendAck(packet)
            return
        }
        if (packet.src == myId) {
            // I am the gateway and sent it myself: delivered with zero hops.
            db.messages().markDelivered(packet.msgId, 0, pathNames, 0)
        } else {
            db.messages().upsert(
                MessageEntity(
                    msgId = packet.msgId, type = packet.type.name, outgoing = false, src = packet.src,
                    srcName = content.name, dest = packet.dest, text = content.text, status = MsgStatus.RECEIVED,
                    hopCount = hops, path = pathNames, latencyMs = (now - packet.timestamp).coerceAtLeast(0),
                    lat = packet.lat, lng = packet.lng, timestamp = packet.timestamp,
                    urgency = packet.urgency, triage = content.triage?.summary() ?: "",
                )
            )
            if (packet.type == PacketType.CHAT) notifier.chatReceived(content.name, content.text, hops)
        }
        if (packet.dest == ANY_GATEWAY) {
            val inbox = InboxEntity(
                msgId = packet.msgId, type = packet.type.name, src = packet.src, srcName = content.name,
                text = content.text, lat = packet.lat, lng = packet.lng, sentAt = packet.timestamp,
                receivedAt = now, hopCount = hops, path = pathNames,
                urgency = packet.urgency, triage = content.triage?.summary() ?: "",
            )
            if (db.inbox().insert(inbox) != -1L) {
                if (packet.type != PacketType.SAFE) {
                    val head = if (packet.urgency > 0) "${Triage.band(packet.urgency)} ${packet.type}" else packet.type.name
                    val body = if (inbox.triage.isNotBlank()) content.text + "\n" + inbox.triage else content.text
                    notifier.sosReceived(head, content.name, body, packet.lat, packet.lng, hops)
                }
                pushToCloud(inbox)
            }
        }
        if (packet.src != myId) sendAck(packet)
    }

    private suspend fun pushToCloud(inbox: InboxEntity) {
        val s = settings
        if (!s.cloudEnabled && s.webhookUrl.isBlank()) return
        if (!device.hasInternet.value) {
            db.inbox().setCloudStatus(inbox.msgId, "Waiting for internet…", false)
            log(LogKind.CLOUD, "☁ No internet yet — alert from ${inbox.srcName} will upload automatically")
            return
        }
        val r = CloudRelay.push(inbox, s, s.displayName)
        db.inbox().setCloudStatus(inbox.msgId, r.status, r.ok)
        log(LogKind.CLOUD, if (r.ok) "☁ Uploaded ${inbox.type} from ${inbox.srcName} to the internet · ${r.status}" else "☁ Upload failed: ${r.status}")
    }

    private fun retryCloud() {
        scope.launch {
            if (!device.hasInternet.value) return@launch
            db.inbox().notPushed().forEach { pushToCloud(it) }
        }
    }

    suspend fun sendCloudTest(): String {
        val s = settings
        if (s.cloudTopic.isBlank()) return "Set an alert channel first"
        val r = CloudRelay.test(s, s.displayName)
        withContext(dispatcher) { log(LogKind.CLOUD, "☁ ${r.status}") }
        return r.status
    }

    private suspend fun sendAck(delivered: Packet) {
        val now = System.currentTimeMillis()
        val body = AckBody(delivered.msgId, delivered.hops, settings.displayName, now)
        val payload = MeshJson.encodeToString(body)
        val ack = Packet(
            msgId = Packet.idFor(myId, now, payload), type = PacketType.ACK, priority = 0, src = myId,
            dest = delivered.src, ttl = settings.ttl, hops = listOf(myId), payload = payload, timestamp = now,
        )
        engine.duplicates.markIfNew(ack.msgId)
        log(LogKind.ACK, "↩ ACK to ${nameOf(delivered.src)} along reverse path")
        routeAck(ack, fromStore = false)
    }

    /**
     * ACKs are source-routed back along the reverse of the original hops. If that
     * neighbour is gone, fall back to the routing strategy; otherwise store.
     * Returns true if the ACK was consumed (delivered or sent on).
     */
    private suspend fun routeAck(ack: Packet, fromStore: Boolean): Boolean {
        val body = runCatching { MeshJson.decodeFromString(AckBody.serializer(), ack.payload) }.getOrNull() ?: return true
        if (ack.dest == myId) {
            val msg = db.messages().get(body.ackFor)
            if (msg != null && msg.status != MsgStatus.DELIVERED) {
                val latency = System.currentTimeMillis() - msg.timestamp
                val hops = body.route.size - 1
                db.messages().markDelivered(body.ackFor, hops, body.route.joinToString(" → ") { nameOf(it) }, latency)
                if (msg.type == PacketType.UPDATE.name) {
                    log(LogKind.ACK, "✅ ${msg.srcName}'s phone confirmed the rescue update ($hops hop(s), $latency ms)")
                    if (device.hasInternet.value) ControlRoom.postEvent(settings.cloudTopic, buildJsonObject {
                        put("kind", "notified"); put("msgId", msg.refId); put("hops", hops); put("latencyMs", latency)
                        put("at", System.currentTimeMillis())
                    })
                } else {
                    log(LogKind.ACK, "✅ ${msg.type} confirmed by ${body.gatewayName}: $hops hop(s), round trip $latency ms")
                    notifier.delivered(msg.type, body.gatewayName, hops, latency)
                }
            }
            return true
        }
        if (ack.ttl <= 0) return true
        val idx = body.route.indexOf(myId)
        val reverseNext = if (idx > 0) body.route[idx - 1] else null
        val next = reverseNext?.takeIf { endpointFor(it) != null }
            ?: engine.strategy.nextHops(graph, myId, ack.dest, null).firstOrNull()
        val out = ack.copy(ttl = ack.ttl - 1, hops = ack.hops + myId)
        val ep = next?.let { endpointFor(it) }
        return if (ep != null) {
            if (ack.src != myId) log(LogKind.ACK, "↩ Passing ACK for ${nameOf(ack.dest)} → ${nameOf(next)}")
            sendRaw(ep, out); true
        } else {
            if (!fromStore) store(ack, null)
            false
        }
    }

    // ===================================================================== control room <-> mesh

    /** Gateway: fetch signed commands from the control-room dashboard and push them into the mesh. */
    private suspend fun pollControlRoom() {
        val polled = ControlRoom.poll(settings.cloudTopic, cmdSince)
        for (p in polled) {
            cmdSince = p.id
            if (!seenCommands.add(p.id)) continue
            val c = p.command ?: continue
            if (!ControlRoom.verify(settings.passphrase, c)) {
                log(LogKind.CLOUD, "⛔ Rejected an unsigned or forged control-room command (${c.cmd})")
                continue
            }
            when (c.cmd) {
                "dispatch" -> sendRescueUpdate(c.msgId, RescueStatus.DISPATCHED, c.note)
                "rescued" -> sendRescueUpdate(c.msgId, RescueStatus.RESCUED, c.note)
                "alert" -> broadcastAlert("alert-${p.id}", AlertBody(c.title, c.text, c.level, "District Control Room", c.ts))
            }
        }
    }

    /** Sends the control room's answer back through the mesh to the phone that sent the SOS. */
    private suspend fun sendRescueUpdate(sosMsgId: String, status: String, note: String) {
        val sos = db.inbox().get(sosMsgId)
        if (sos == null) return // another gateway received this SOS; it will answer
        if (sos.rescueStatus == status) return
        db.inbox().setRescue(sosMsgId, status)
        log(LogKind.CLOUD, "📡 Control room → ${sos.srcName}: ${RescueStatus.label(status)}" + if (note.isNotBlank()) " ($note)" else "")
        if (sos.src == myId) {
            db.messages().setRescue(sosMsgId, status, note)
            notifier.rescueUpdate(RescueStatus.label(status), note)
            return
        }
        val now = System.currentTimeMillis()
        val payload = crypto().encrypt(MeshJson.encodeToString(Content(settings.displayName, note, rescue = RescueInfo(sosMsgId, status))))
        val packet = Packet(
            msgId = Packet.idFor(myId, now, payload), type = PacketType.UPDATE, priority = 0,
            src = myId, dest = sos.src, ttl = settings.ttl, payload = payload, timestamp = now, urgency = 95,
        )
        db.messages().upsert(
            MessageEntity(
                msgId = packet.msgId, type = PacketType.UPDATE.name, outgoing = true, src = myId, srcName = sos.srcName,
                dest = sos.src, text = note, status = MsgStatus.QUEUED, timestamp = now, refId = sosMsgId,
            )
        )
        handleData(packet, cameFrom = null)
    }

    /** Floods an evacuation / warning alert to every phone in the mesh. */
    private suspend fun broadcastAlert(id: String, body: AlertBody) {
        val payload = crypto().encrypt(MeshJson.encodeToString(body))
        val packet = Packet(
            msgId = id, type = PacketType.ALERT, priority = 0, src = myId, dest = "ALL",
            ttl = settings.ttl, hops = listOf(myId), payload = payload, timestamp = System.currentTimeMillis(), urgency = 100,
        )
        handleAlert(packet, fromNodeId = null)
    }

    /** ALERT packets are flooded with duplicate suppression and TTL, and remembered for late joiners. */
    private suspend fun handleAlert(packet: Packet, fromNodeId: String?) {
        if (!engine.duplicates.markIfNew(packet.msgId)) return
        val body = crypto().decrypt(packet.payload)?.let {
            runCatching { MeshJson.decodeFromString(AlertBody.serializer(), it) }.getOrNull()
        } ?: return
        db.messages().upsert(
            MessageEntity(
                msgId = packet.msgId, type = PacketType.ALERT.name, outgoing = false, src = packet.src,
                srcName = body.issuedBy, dest = "ALL", text = body.title + "\n" + body.text, status = body.level,
                hopCount = packet.hops.size - 1, path = packet.hops.joinToString(" → ") { nameOf(it) },
                timestamp = body.issuedAt, urgency = 100,
            )
        )
        log(LogKind.DELIVER, "📢 ${body.level} from ${body.issuedBy}: ${body.title} (${packet.hops.size - 1} hop(s))")
        notifier.alert(body.level, body.title, body.text)
        stats.alertReceived()
        recentAlerts.removeAll { System.currentTimeMillis() - it.timestamp > ALERT_KEEP_MS }
        recentAlerts += packet
        if (packet.ttl > 1) {
            val fwd = packet.copy(ttl = packet.ttl - 1, hops = if (packet.hops.lastOrNull() == myId) packet.hops else packet.hops + myId)
            connectedNeighbours().filter { it.nodeId != fromNodeId && it.nodeId !in packet.hops }.forEach { sendRaw(it.endpointId, fwd) }
        }
    }

    private fun shareAlertsWith(endpointId: String) {
        recentAlerts.removeAll { System.currentTimeMillis() - it.timestamp > ALERT_KEEP_MS }
        recentAlerts.forEach { a ->
            val fwd = if (a.hops.lastOrNull() == myId) a else a.copy(hops = a.hops + myId, ttl = (a.ttl - 1).coerceAtLeast(1))
            sendRaw(endpointId, fwd)
        }
    }

    // ===================================================================== public API

    /** Creates, encrypts and routes a new user message. */
    fun sendMessage(type: PacketType, text: String, dest: String, lat: Double?, lng: Double?, triage: TriageInfo? = null) {
        scope.launch {
            val now = System.currentTimeMillis()
            val urgency = triage?.urgency ?: 0
            val payload = crypto().encrypt(MeshJson.encodeToString(Content(settings.displayName, text, triage)))
            val packet = Packet(
                msgId = Packet.idFor(myId, now, payload), type = type,
                priority = Triage.queueKey(type == PacketType.CHAT, urgency),
                src = myId, dest = dest, ttl = settings.ttl, hops = emptyList(), lat = lat, lng = lng,
                payload = payload, timestamp = now, urgency = urgency,
            )
            db.messages().upsert(
                MessageEntity(
                    msgId = packet.msgId, type = type.name, outgoing = true, src = myId, srcName = settings.displayName,
                    dest = dest, text = text, status = MsgStatus.QUEUED, lat = lat, lng = lng, timestamp = now,
                    urgency = urgency, triage = triage?.summary() ?: "",
                )
            )
            handleData(packet, cameFrom = null)
        }
    }

    suspend fun clearData() = withContext(dispatcher) {
        recentAlerts.clear()
        db.messages().clear(); db.pending().clear(); db.inbox().clear()
        engine.duplicates.clear()
        handedToCarrier.clear()
        logBuffer.clear(); _log.value = emptyList()
        relayed = 0
    }

    companion object {
        const val SERVICE_ID = "com.meshalive.app.MESH"
        const val NODE_EXPIRY_MS = 30_000L
        const val DEFAULT_RTT = 100.0
        const val FAIL_AFTER_MS = 5 * 60_000L
        const val PENDING_MAX_AGE_MS = 6 * 60 * 60_000L
        const val ALERT_KEEP_MS = 12 * 60 * 60_000L
    }
}
