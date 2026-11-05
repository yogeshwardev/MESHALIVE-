package com.meshalive.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshalive.algorithms.Algorithm
import com.meshalive.app.MeshAliveApp
import com.meshalive.app.sim.Coverage
import com.meshalive.app.sim.PlanInput
import com.meshalive.app.sim.PlanResult
import com.meshalive.app.sim.ScalePoint
import com.meshalive.app.sim.SimConfig
import com.meshalive.app.sim.SimOutcome
import com.meshalive.app.sim.Simulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SimUiState(
    val config: SimConfig = SimConfig(),
    val running: Boolean = false,
    val outcome: SimOutcome? = null,
    val scale: List<ScalePoint> = emptyList(),
    val scaleProgress: Int = 0,
    val scaleRunning: Boolean = false,
    val animAlgorithm: Algorithm = Algorithm.DIJKSTRA,
    val message: String? = null,
)

data class CurvePoint(val users: Int, val meshPct: Double, val carriersPct: Double)

data class PlanUi(
    val input: PlanInput = PlanInput(areaKm2 = 1.0, appUsers = 1000, gateways = 3, carriers = 4),
    val running: Boolean = false,
    val result: PlanResult? = null,
    val curve: List<CurvePoint> = emptyList(),
)

class SimulatorViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MeshAliveApp
    private val _ui = MutableStateFlow(SimUiState(config = SimConfig(params = app.settings.current.costParams)))
    val ui: StateFlow<SimUiState> = _ui.asStateFlow()

    fun update(transform: (SimConfig) -> SimConfig) {
        _ui.value = _ui.value.copy(config = transform(_ui.value.config))
    }

    fun selectAnimation(a: Algorithm) { _ui.value = _ui.value.copy(animAlgorithm = a) }

    fun run() {
        if (_ui.value.running) return
        val cfg = _ui.value.config.copy(params = app.settings.current.costParams)
        _ui.value = _ui.value.copy(running = true)
        viewModelScope.launch {
            val out = withContext(Dispatchers.Default) { Simulator.run(cfg) }
            _ui.value = _ui.value.copy(running = false, outcome = out)
        }
    }

    fun runScalability() {
        if (_ui.value.scaleRunning) return
        val cfg = _ui.value.config.copy(params = app.settings.current.costParams)
        _ui.value = _ui.value.copy(scaleRunning = true, scaleProgress = 0, scale = emptyList())
        viewModelScope.launch {
            val pts = withContext(Dispatchers.Default) {
                Simulator.scalability(cfg) { done -> _ui.value = _ui.value.copy(scaleProgress = done) }
            }
            _ui.value = _ui.value.copy(scaleRunning = false, scale = pts)
        }
    }

    fun exportCsv() {
        val s = _ui.value
        if (s.outcome == null && s.scale.isEmpty()) {
            _ui.value = s.copy(message = "Run the simulation first")
            return
        }
        val csv = Simulator.toCsv(s.outcome, s.scale)
        val name = "meshalive_sim_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".csv"
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) { save(name, csv) }
            _ui.value = _ui.value.copy(message = msg)
        }
    }

    private fun save(name: String, csv: String): String = Downloads.saveText(app, name, "text/csv", csv)

    // ---------------------------------------------------------------- district planner

    private val _plan = MutableStateFlow(PlanUi())
    val plan: StateFlow<PlanUi> = _plan.asStateFlow()

    fun updatePlan(transform: (PlanInput) -> PlanInput) {
        _plan.value = _plan.value.copy(input = transform(_plan.value.input))
    }

    /** Runs the coverage model for the inputs, plus an adoption curve (coverage vs. number of users). */
    fun runPlan() {
        if (_plan.value.running) return
        val input = _plan.value.input
        _plan.value = _plan.value.copy(running = true)
        viewModelScope.launch {
            val (result, curve) = withContext(Dispatchers.Default) {
                val r = Coverage.plan(input)
                val steps = listOf(0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0).map { (input.appUsers * it).toInt().coerceAtLeast(10) }
                r to steps.map { u -> val x = Coverage.plan(input.copy(appUsers = u)); CurvePoint(u, x.meshPct, x.withCarriersPct) }
            }
            _plan.value = _plan.value.copy(running = false, result = result, curve = curve)
        }
    }

    fun consumeMessage() { _ui.value = _ui.value.copy(message = null) }
}
