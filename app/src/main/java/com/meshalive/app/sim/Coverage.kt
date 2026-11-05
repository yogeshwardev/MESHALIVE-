package com.meshalive.app.sim

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** District planner inputs. All of them are assumptions the user can change. */
data class PlanInput(
    val areaKm2: Double = 4.0,
    val appUsers: Int = 800,
    /** Phones that still have signal somewhere in the area (acting as gateways). */
    val gateways: Int = 3,
    /** Rescue boats / NDRF teams / ambulances carrying a phone in carrier mode. */
    val carriers: Int = 4,
    /** Bluetooth/Wi-Fi range between two phones, in metres. */
    val rangeM: Double = 40.0,
    /** People per household who are covered by one phone. */
    val householdSize: Double = 4.0,
    val seed: Long = 7,
)

data class PlanResult(
    val input: PlanInput,
    /** Share of phones whose SOS reaches a gateway through the live mesh alone. */
    val meshPct: Double,
    /** Share reached by the mesh or by a rescue carrier passing within range of their cluster. */
    val withCarriersPct: Double,
    val clusters: Int,
    val avgNeighbours: Double,
    val peopleCovered: Int,
    val peopleTotal: Int,
)

/**
 * Coverage planner: drops [PlanInput.appUsers] phones at random in a square district,
 * links phones within radio range, and asks which phones can get an SOS to a gateway:
 *  1. through the mesh (same connected cluster as a gateway), or
 *  2. through a rescue carrier whose route (a straight line across the district) passes
 *     within radio range of any phone in their cluster (store-carry-forward).
 * Union-find keeps it O(V + E) after grid bucketing, so 5,000 phones run in milliseconds.
 */
object Coverage {

    fun plan(p: PlanInput): PlanResult {
        val n = p.appUsers.coerceIn(2, 20_000)
        val side = sqrt(p.areaKm2.coerceAtLeast(0.01)) * 1000.0
        val range = p.rangeM.coerceAtLeast(1.0)
        val rnd = Random(p.seed)
        val xs = DoubleArray(n) { rnd.nextDouble() * side }
        val ys = DoubleArray(n) { rnd.nextDouble() * side }
        val gatewayIdx = (0 until n).shuffled(rnd).take(p.gateways.coerceIn(0, n)).toSet()

        // ---- union-find over radio links (grid bucketing => ~O(V + E)) ----
        val parent = IntArray(n) { it }
        fun find(a: Int): Int { var x = a; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        fun union(a: Int, b: Int) { val ra = find(a); val rb = find(b); if (ra != rb) parent[ra] = rb }
        val cells = HashMap<Long, MutableList<Int>>()
        fun key(cx: Int, cy: Int) = cx.toLong() * 1_000_003L + cy
        for (i in 0 until n) cells.getOrPut(key((xs[i] / range).toInt(), (ys[i] / range).toInt())) { mutableListOf() } += i
        var links = 0L
        for (i in 0 until n) {
            val cx = (xs[i] / range).toInt(); val cy = (ys[i] / range).toInt()
            for (dx in -1..1) for (dy in -1..1) cells[key(cx + dx, cy + dy)]?.forEach { j ->
                if (j > i && hypot(xs[i] - xs[j], ys[i] - ys[j]) <= range) { union(i, j); links++ }
            }
        }

        val gatewayRoots = gatewayIdx.map { find(it) }.toSet()
        val reached = BooleanArray(n) { find(it) in gatewayRoots }

        // ---- rescue carriers: straight routes across the district ----
        val carrierRoots = HashSet<Int>()
        repeat(p.carriers.coerceAtLeast(0)) {
            val (ax, ay) = edgePoint(rnd, side)
            val (bx, by) = edgePoint(rnd, side)
            for (i in 0 until n) if (distToSegment(xs[i], ys[i], ax, ay, bx, by) <= range) carrierRoots += find(i)
        }
        val withCarriers = BooleanArray(n) { reached[it] || find(it) in carrierRoots }

        val users = (0 until n).filter { it !in gatewayIdx }.ifEmpty { (0 until n).toList() }
        val meshPct = 100.0 * users.count { reached[it] } / users.size
        val carrierPct = 100.0 * users.count { withCarriers[it] } / users.size
        val clusters = (0 until n).map { find(it) }.toSet().size
        val people = (n * p.householdSize).roundToInt()
        return PlanResult(
            input = p,
            meshPct = meshPct,
            withCarriersPct = carrierPct,
            clusters = clusters,
            avgNeighbours = 2.0 * links / n,
            peopleCovered = (people * carrierPct / 100.0).roundToInt(),
            peopleTotal = people,
        )
    }

    private fun edgePoint(rnd: Random, side: Double): Pair<Double, Double> {
        val t = rnd.nextDouble() * side
        return when (rnd.nextInt(4)) {
            0 -> t to 0.0
            1 -> t to side
            2 -> 0.0 to t
            else -> side to t
        }
    }

    private fun distToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / len2))
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
