package io.towerscope.calc

import io.towerscope.data.HistoryRecord
import io.towerscope.data.SignalRecord
import io.towerscope.data.SpeedRecord
import io.towerscope.model.RadioType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt

enum class DayPeriod(val label: String, val hours: IntRange) {
    NIGHT("night (midnight–6 am)", 0..5),
    MORNING("morning (6 am–noon)", 6..11),
    AFTERNOON("afternoon (noon–6 pm)", 12..17),
    EVENING("evening (6 pm–midnight)", 18..23);

    companion object {
        fun of(hour: Int): DayPeriod = entries.first { hour in it.hours }
    }
}

/** An area of about 100 m where the user has readings. */
data class PlaceStat(
    val lat: Double,
    val lon: Double,
    val signalSamples: Int,
    val medianSignalDbm: Double?,
    val speedTests: Int,
    val medianDownMbps: Double?,
    val distanceFromHereM: Double?,
    val bearingFromHereDeg: Double?,
) {
    val isHere: Boolean get() = distanceFromHereM != null && distanceFromHereM <= Insights.HERE_RADIUS_M
}

data class PeriodStat(val period: DayPeriod, val signalSamples: Int, val medianSignalDbm: Double?, val speedTests: Int, val medianDownMbps: Double?)

data class TimeOfDay(
    /** True when the stats are for the current area; false when they use all history. */
    val local: Boolean,
    val periods: List<PeriodStat>,
    /** Plain-language finding, or null when there's no clear pattern yet. */
    val finding: String?,
)

data class TowerStat(
    val cellKey: String,
    val radio: RadioType,
    val signalSamples: Int,
    val medianSignalDbm: Double?,
    val speedTests: Int,
    val medianDownMbps: Double?,
    val lastSeenMs: Long,
)

enum class TrendKind { SIGNAL, SPEED }

data class TrendAlert(val kind: TrendKind, val local: Boolean, val recent: Double, val usual: Double, val message: String)

data class InsightReport(
    val signalCount: Int,
    val speedCount: Int,
    val sinceMs: Long?,
    val places: List<PlaceStat>,
    val timeOfDay: TimeOfDay?,
    val towers: List<TowerStat>,
    val alerts: List<TrendAlert>,
    /** Readings per phone, most first. */
    val devices: List<Pair<String, Int>> = emptyList(),
) {
    val isEmpty: Boolean get() = signalCount == 0 && speedCount == 0
}

/**
 * Heuristics over the on-phone history. Every finding needs a minimum amount of data so that one bad
 * reading or one slow test doesn't produce advice.
 */
object Insights {
    const val HERE_RADIUS_M = 150.0
    private const val CELL_DEG = 0.0009 // ≈100 m of latitude
    private const val MIN_PLACE_SIGNALS = 10
    private const val MIN_PLACE_TESTS = 2
    private const val MIN_TOWER_SIGNALS = 5
    private const val MIN_PERIOD_SIGNALS = 10
    private const val MIN_PERIOD_TESTS = 3
    private const val WEEK_MS = 7L * 24 * 3600 * 1000

    fun report(
        history: List<HistoryRecord>,
        hereLat: Double?,
        hereLon: Double?,
        nowMs: Long,
        currentDevice: String? = null,
    ): InsightReport {
        val signals = history.filterIsInstance<SignalRecord>()
        val speeds = history.filterIsInstance<SpeedRecord>().filter { it.cellular && it.downMbps != null }
        // Different phones' radios read differently, so trends only compare this phone with itself.
        fun mine(r: HistoryRecord) = currentDevice == null || r.device == null || r.device == currentDevice
        return InsightReport(
            signalCount = signals.size,
            speedCount = history.count { it is SpeedRecord },
            sinceMs = history.minOfOrNull { it.timestampMs },
            places = places(signals, speeds, hereLat, hereLon),
            timeOfDay = timeOfDay(signals, speeds, hereLat, hereLon),
            towers = towers(signals, speeds),
            alerts = alerts(signals.filter(::mine), speeds.filter(::mine), hereLat, hereLon, nowMs),
            devices = history.groupBy { it.device ?: "Unknown phone" }.map { it.key to it.value.size }.sortedByDescending { it.second },
        )
    }

    /** Areas with enough readings, best signal first. Limited to 10 km around the user when their position is known. */
    fun places(signals: List<SignalRecord>, speeds: List<SpeedRecord>, hereLat: Double?, hereLon: Double?): List<PlaceStat> {
        val groups = (signals + speeds).filter { it.lat != null && it.lon != null }.groupBy { gridKey(it.lat!!, it.lon!!) }
        return groups.values.mapNotNull { recs ->
            val sig = recs.filterIsInstance<SignalRecord>()
            val spd = recs.filterIsInstance<SpeedRecord>()
            if (sig.size < MIN_PLACE_SIGNALS && spd.size < MIN_PLACE_TESTS) return@mapNotNull null
            val lat = recs.map { it.lat!! }.average()
            val lon = recs.map { it.lon!! }.average()
            val d = if (hereLat != null && hereLon != null) Geo.distanceM(hereLat, hereLon, lat, lon) else null
            PlaceStat(
                lat, lon, sig.size, median(sig.map { it.signalDbm.toDouble() }),
                spd.size, median(spd.mapNotNull { it.downMbps }),
                d, if (hereLat != null && hereLon != null && d != null && d > HERE_RADIUS_M) Geo.bearingDeg(hereLat, hereLon, lat, lon) else null,
            )
        }
            .filter { it.distanceFromHereM == null || it.distanceFromHereM <= 10_000 }
            .sortedWith(compareByDescending<PlaceStat> { it.medianSignalDbm ?: Double.NEGATIVE_INFINITY }.thenByDescending { it.medianDownMbps ?: 0.0 })
    }

    fun timeOfDay(signals: List<SignalRecord>, speeds: List<SpeedRecord>, hereLat: Double?, hereLon: Double?): TimeOfDay? {
        val near = { r: HistoryRecord -> isNear(r, hereLat, hereLon) }
        val localSig = signals.filter(near)
        val localSpd = speeds.filter(near)
        val useLocal = enoughPeriods(localSig, localSpd)
        val sig = if (useLocal) localSig else signals
        val spd = if (useLocal) localSpd else speeds
        if (!enoughPeriods(sig, spd)) return null

        val periods = DayPeriod.entries.map { p ->
            val s = sig.filter { DayPeriod.of(it.localHour) == p }
            val t = spd.filter { DayPeriod.of(it.localHour) == p }
            PeriodStat(p, s.size, median(s.map { it.signalDbm.toDouble() }), t.size, median(t.mapNotNull { it.downMbps }))
        }
        val where = if (useLocal) "here" else "overall"
        val speedFinding = periods.filter { it.speedTests >= MIN_PERIOD_TESTS && it.medianDownMbps != null }.takeIf { it.size >= 2 }?.let { ps ->
            val best = ps.maxBy { it.medianDownMbps!! }
            val worst = ps.minBy { it.medianDownMbps!! }
            val drop = 1 - worst.medianDownMbps!! / best.medianDownMbps!!
            if (drop >= 0.4) "Speeds $where are about ${(drop * 100).roundToInt()}% lower in the ${worst.period.label} than in the " +
                "${best.period.label}. When the signal stays the same, that usually means the tower is busy." else null
        }
        val signalFinding = periods.filter { it.signalSamples >= MIN_PERIOD_SIGNALS && it.medianSignalDbm != null }.takeIf { it.size >= 2 }?.let { ps ->
            val best = ps.maxBy { it.medianSignalDbm!! }
            val worst = ps.minBy { it.medianSignalDbm!! }
            val diff = best.medianSignalDbm!! - worst.medianSignalDbm!!
            if (diff >= 6) "Signal $where is about ${diff.roundToInt()} dB weaker in the ${worst.period.label} than in the ${best.period.label}." else null
        }
        return TimeOfDay(useLocal, periods, speedFinding ?: signalFinding)
    }

    /** Cells the user has been on, best typical signal first. */
    fun towers(signals: List<SignalRecord>, speeds: List<SpeedRecord>): List<TowerStat> {
        val tests = speeds.filter { it.cellKey != null }.groupBy { it.cellKey!! }
        return signals.filter { it.cellKey != null }.groupBy { it.cellKey!! }
            .filter { it.value.size >= MIN_TOWER_SIGNALS }
            .map { (key, recs) ->
                val t = tests[key].orEmpty()
                TowerStat(
                    key, recs.first().radio, recs.size, median(recs.map { it.signalDbm.toDouble() }),
                    t.size, median(t.mapNotNull { it.downMbps }), recs.maxOf { it.timestampMs },
                )
            }
            .sortedByDescending { it.medianSignalDbm }
    }

    /** The last 7 days against the 83 days before, for the current area when possible. */
    fun alerts(signals: List<SignalRecord>, speeds: List<SpeedRecord>, hereLat: Double?, hereLon: Double?, nowMs: Long): List<TrendAlert> {
        val out = mutableListOf<TrendAlert>()
        val near = { r: HistoryRecord -> isNear(r, hereLat, hereLon) }
        fun recent(r: HistoryRecord) = nowMs - r.timestampMs <= WEEK_MS
        fun <T : HistoryRecord> scoped(all: List<T>, minRecent: Int, minUsual: Int): Pair<List<T>, Boolean>? {
            val local = all.filter(near)
            if (local.count(::recent) >= minRecent && local.count { !recent(it) } >= minUsual) return local to true
            if (all.count(::recent) >= minRecent && all.count { !recent(it) } >= minUsual) return all to false
            return null
        }

        scoped(signals, 20, 30)?.let { (recs, local) ->
            val now = median(recs.filter(::recent).map { it.signalDbm.toDouble() })!!
            val usual = median(recs.filterNot(::recent).map { it.signalDbm.toDouble() })!!
            if (usual - now >= 6) out += TrendAlert(
                TrendKind.SIGNAL, local, now, usual,
                "Signal ${if (local) "here" else "overall"} has been about ${(usual - now).roundToInt()} dB weaker this week than usual " +
                    "(${now.roundToInt()} vs ${usual.roundToInt()} dBm). A carrier change, a phone update or new construction can cause this.",
            )
        }
        scoped(speeds, 3, 5)?.let { (recs, local) ->
            val now = median(recs.filter(::recent).mapNotNull { it.downMbps })!!
            val usual = median(recs.filterNot(::recent).mapNotNull { it.downMbps })!!
            if (usual > 0 && now <= usual * 0.6) out += TrendAlert(
                TrendKind.SPEED, local, now, usual,
                "Download speeds ${if (local) "here" else "overall"} are about ${((1 - now / usual) * 100).roundToInt()}% lower this week than usual " +
                    "(${Fmt.speed(now)} vs ${Fmt.speed(usual)}).",
            )
        }
        return out
    }

    private fun enoughPeriods(sig: List<SignalRecord>, spd: List<SpeedRecord>): Boolean {
        val sigPeriods = sig.groupBy { DayPeriod.of(it.localHour) }.count { it.value.size >= MIN_PERIOD_SIGNALS }
        val spdPeriods = spd.groupBy { DayPeriod.of(it.localHour) }.count { it.value.size >= MIN_PERIOD_TESTS }
        return sigPeriods >= 2 || spdPeriods >= 2
    }

    private fun isNear(r: HistoryRecord, lat: Double?, lon: Double?): Boolean =
        lat != null && lon != null && r.lat != null && r.lon != null && Geo.distanceM(lat, lon, r.lat!!, r.lon!!) <= 300.0

    private fun gridKey(lat: Double, lon: Double): Long {
        val y = floor(lat / CELL_DEG).toLong()
        val lonDeg = CELL_DEG / cos(lat * PI / 180).coerceAtLeast(0.01)
        val x = floor(lon / lonDeg).toLong()
        return y * 10_000_000L + x
    }

    fun median(xs: List<Double>): Double? {
        if (xs.isEmpty()) return null
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
