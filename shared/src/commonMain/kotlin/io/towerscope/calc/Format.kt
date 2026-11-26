package io.towerscope.calc

import io.towerscope.data.UnitSystem
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/** Locale-free fixed-decimal formatting (Kotlin common has no String.format). */
fun Double.fmt(decimals: Int = 1): String {
    if (isNaN() || isInfinite()) return "—"
    val factor = 10.0.pow(decimals)
    val scaled = round(abs(this) * factor).toLong()
    val f = factor.toLong()
    val body = if (decimals > 0) "${scaled / f}." + (scaled % f).toString().padStart(decimals, '0') else "${scaled / f}"
    return if (this < 0 && scaled != 0L) "-$body" else body
}

fun Float.fmt(decimals: Int = 1): String = toDouble().fmt(decimals)

fun Int.signed(): String = if (this > 0) "+$this" else "$this"

object Fmt {
    fun distance(m: Double?, units: UnitSystem): String {
        if (m == null) return "—"
        return when (units) {
            UnitSystem.METRIC -> if (m < 1000) "${m.fmt(0)} m" else "${(m / 1000).fmt(if (m < 10_000) 2 else 1)} km"
            UnitSystem.IMPERIAL -> {
                val ft = m * 3.28084
                if (ft < 1000) "${ft.fmt(0)} ft" else "${(m / METERS_PER_MILE).fmt(if (m < 16_000) 2 else 1)} mi"
            }
        }
    }

    /** Miles with precision suited to short tower distances (e.g. 0.024 mi, 1.42 mi, 12.3 mi). */
    fun miles(m: Double?): String {
        if (m == null) return "—"
        val mi = m / METERS_PER_MILE
        return "${mi.fmt(if (mi < 0.1) 3 else if (mi < 10) 2 else 1)} mi"
    }

    fun kilometres(m: Double?): String {
        if (m == null) return "—"
        val km = m / 1000
        return "${km.fmt(if (km < 0.1) 3 else if (km < 10) 2 else 1)} km"
    }

    /** The distance in the other unit system, for side-by-side display. */
    fun distanceAlt(m: Double?, units: UnitSystem): String =
        if (units == UnitSystem.METRIC) miles(m) else kilometres(m)

    fun groundSpeed(mps: Double, units: UnitSystem): String =
        if (units == UnitSystem.METRIC) "${(mps * 3.6).fmt(0)} km/h" else "${(mps * 2.236936).fmt(0)} mph"

    const val METERS_PER_MILE = 1609.344

    /** Value and unit separately, for large numeric displays. */
    fun distanceParts(m: Double?, units: UnitSystem): Pair<String, String> {
        val s = distance(m, units)
        val i = s.lastIndexOf(' ')
        return if (i < 0) s to "" else s.substring(0, i) to s.substring(i + 1)
    }

    fun speed(mbps: Double?): String = when {
        mbps == null -> "—"
        mbps < 1 -> "${(mbps * 1000).fmt(0)} kbps"
        mbps < 10 -> "${mbps.fmt(2)} Mbps"
        mbps < 100 -> "${mbps.fmt(1)} Mbps"
        mbps < 1000 -> "${mbps.fmt(0)} Mbps"
        else -> "${(mbps / 1000).fmt(2)} Gbps"
    }

    fun speedParts(mbps: Double?): Pair<String, String> {
        val s = speed(mbps)
        val i = s.lastIndexOf(' ')
        return if (i < 0) s to "" else s.substring(0, i) to s.substring(i + 1)
    }

    fun mhz(v: Double?): String = when {
        v == null -> "—"
        v >= 10_000 -> "${(v / 1000).fmt(2)} GHz"
        else -> "${v.fmt(1)} MHz"
    }

    fun height(m: Double, units: UnitSystem): String =
        if (units == UnitSystem.METRIC) "${m.fmt(0)} m" else "${(m * 3.28084).fmt(0)} ft"

    fun coord(v: Double): String = v.fmt(5) + "°"

    fun ago(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 2 -> "just now"
            s < 60 -> "${s}s ago"
            s < 3600 -> "${s / 60}m ago"
            else -> "${s / 3600}h ago"
        }
    }

    fun hex(v: Long): String = "0x" + v.toString(16).uppercase()

    fun bytes(n: Long): String = when {
        n < 1024 -> "$n B"
        n < 1024 * 1024 -> "${(n / 1024.0).fmt(0)} KB"
        else -> "${(n / 1048576.0).fmt(1)} MB"
    }
}
