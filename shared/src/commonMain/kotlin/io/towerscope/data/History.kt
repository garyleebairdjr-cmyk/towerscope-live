package io.towerscope.data

import io.towerscope.calc.Geo
import io.towerscope.model.RadioType

/**
 * Long-term history of signal readings and speed tests, kept only on the phone so the app can learn
 * where and when the connection is good. Each record carries the exact GPS position (when known), the
 * local hour, and the cell in use.
 */
sealed interface HistoryRecord {
    val timestampMs: Long
    /** Local hour of day (0–23) when recorded, so time-of-day patterns survive time-zone changes. */
    val localHour: Int
    val lat: Double?
    val lon: Double?
    /** Cell key (radio:mcc:mnc:area:cellId), or null when not on cellular / unknown. */
    val cellKey: String?
    /** Phone that took the reading (e.g. "samsung SM-A165M"). Radios differ, so trends compare like with like. */
    val device: String?
}

/** How much location to keep with each reading. */
enum class HistoryLocation(val label: String, val description: String) {
    EXACT("Exact", "GPS position as measured. Best for finding good spots, but it's a record of where you've been."),
    APPROXIMATE("Approximate", "Rounded to about 100 m. Enough to tell home from work, not which room."),
    NONE("None", "No location. Still learns which towers and times of day work best for you.");

    fun apply(lat: Double?, lon: Double?): Pair<Double?, Double?> = when (this) {
        EXACT -> lat to lon
        APPROXIMATE -> lat?.let { roundTo(it) } to lon?.let { roundTo(it) }
        NONE -> null to null
    }

    private fun roundTo(v: Double) = kotlin.math.round(v * 1000) / 1000 // 0.001° ≈ 110 m
}

data class SignalRecord(
    override val timestampMs: Long,
    override val localHour: Int,
    override val lat: Double?,
    override val lon: Double?,
    override val cellKey: String?,
    val radio: RadioType,
    /** RSRP for LTE/NR, RSCP for 3G, dBm otherwise. */
    val signalDbm: Int,
    val sinrDb: Double?,
    val rsrqDb: Int?,
    override val device: String? = null,
) : HistoryRecord

data class SpeedRecord(
    override val timestampMs: Long,
    override val localHour: Int,
    override val lat: Double?,
    override val lon: Double?,
    override val cellKey: String?,
    val downMbps: Double?,
    val upMbps: Double?,
    val pingMs: Double?,
    /** False when the test ran over Wi-Fi or another non-cellular link. */
    val cellular: Boolean,
    override val device: String? = null,
) : HistoryRecord

/** Line-based storage the platform provides (a private file on Android). */
interface HistoryFile {
    fun readAll(): String?
    fun append(text: String)
    fun replace(text: String)
    fun clear()

    /** Whether the history goes into the phone's cloud backup (and so survives reinstalling or a new phone). */
    val backupSupported: Boolean get() = false
    val backedUp: Boolean get() = false
    fun setBackedUp(on: Boolean) {}
}

/** The same record with its position reduced to [level]. */
fun HistoryRecord.withLocation(level: HistoryLocation): HistoryRecord {
    val (la, lo) = level.apply(lat, lon)
    return when (this) {
        is SignalRecord -> copy(lat = la, lon = lo)
        is SpeedRecord -> copy(lat = la, lon = lo)
    }
}

/** Encodes records as tab-separated lines; tolerant of bad or future-format lines. */
object HistoryCodec {
    fun encode(r: HistoryRecord): String = when (r) {
        is SignalRecord -> listOf(
            "S", r.timestampMs, r.localHour, r.lat.c(), r.lon.c(), r.cellKey ?: "", r.radio.name,
            r.signalDbm, r.sinrDb?.toString() ?: "", r.rsrqDb?.toString() ?: "", clean(r.device),
        ).joinToString("\t")
        is SpeedRecord -> listOf(
            "T", r.timestampMs, r.localHour, r.lat.c(), r.lon.c(), r.cellKey ?: "",
            r.downMbps?.toString() ?: "", r.upMbps?.toString() ?: "", r.pingMs?.toString() ?: "", if (r.cellular) "1" else "0",
            clean(r.device),
        ).joinToString("\t")
    }

    fun decode(line: String): HistoryRecord? = runCatching {
        val p = line.split('\t')
        val t = p[1].toLong()
        val hour = p[2].toInt().coerceIn(0, 23)
        val lat = p[3].toDoubleOrNull()
        val lon = p[4].toDoubleOrNull()
        val cell = p[5].ifEmpty { null }
        val device = p.getOrNull(10)?.ifEmpty { null }
        when (p[0]) {
            "S" -> SignalRecord(
                t, hour, lat, lon, cell, RadioType.valueOf(p[6]), p[7].toInt(), p[8].toDoubleOrNull(), p[9].toIntOrNull(), device,
            )
            "T" -> SpeedRecord(
                t, hour, lat, lon, cell, p[6].toDoubleOrNull(), p[7].toDoubleOrNull(), p[8].toDoubleOrNull(), p[9] == "1", device,
            )
            else -> null
        }
    }.getOrNull()

    private fun clean(s: String?) = s?.replace('\t', ' ')?.replace('\n', ' ')?.trim()?.take(60) ?: ""

    private fun Double?.c(): String = this?.let { ((it * 1e6).toLong() / 1e6).toString() } ?: ""
}

/** Loads, appends and prunes history. Holds everything in memory; the file is the durable copy. */
class HistoryStore(private val file: HistoryFile) {
    fun load(nowMs: Long): List<HistoryRecord> {
        val all = file.readAll()?.lineSequence()?.mapNotNull { if (it.isBlank()) null else HistoryCodec.decode(it) }?.toList().orEmpty()
        val kept = prune(all, nowMs)
        // Rewrite when pruning dropped anything, so the file doesn't grow without bound.
        if (kept.size != all.size) file.replace(kept.joinToString("\n", postfix = "\n") { HistoryCodec.encode(it) })
        return kept
    }

    fun append(r: HistoryRecord) = file.append(HistoryCodec.encode(r) + "\n")

    fun rewrite(all: List<HistoryRecord>) = file.replace(all.joinToString("\n", postfix = "\n") { HistoryCodec.encode(it) })

    fun clear() = file.clear()

    companion object {
        const val MAX_AGE_MS = 90L * 24 * 3600 * 1000
        const val MAX_RECORDS = 60_000

        fun prune(all: List<HistoryRecord>, nowMs: Long): List<HistoryRecord> =
            all.filter { nowMs - it.timestampMs <= MAX_AGE_MS }.takeLast(MAX_RECORDS)
    }
}

/**
 * Decides when a signal reading is worth keeping: about once a minute, or sooner when the phone moves
 * to another cell or about 50 m away. Keeps the history useful without filling the phone.
 */
class SignalSampler(
    private val minIntervalMs: Long = 60_000,
    private val minMoveM: Double = 50.0,
    private val minIntervalOnChangeMs: Long = 10_000,
) {
    private var last: SignalRecord? = null

    fun shouldRecord(r: SignalRecord): Boolean {
        val prev = last
        val record = when {
            prev == null -> true
            r.timestampMs - prev.timestampMs >= minIntervalMs -> true
            r.timestampMs - prev.timestampMs < minIntervalOnChangeMs -> false
            r.cellKey != prev.cellKey -> true
            moved(prev, r) -> true
            else -> false
        }
        if (record) last = r
        return record
    }

    private fun moved(a: SignalRecord, b: SignalRecord): Boolean {
        if (a.lat == null || a.lon == null || b.lat == null || b.lon == null) return false
        return Geo.distanceM(a.lat, a.lon, b.lat, b.lon) >= minMoveM
    }
}

/** Fallback storage in the key-value store, capped, for platforms without a file implementation. */
class KeyValueHistoryFile(private val store: io.towerscope.platform.KeyValueStore) : HistoryFile {
    override fun readAll(): String? = store.getString(KEY)
    override fun append(text: String) {
        val all = readAll().orEmpty() + text
        // Over the cap, drop the oldest lines (cut at a line break so no record is left half-written).
        replace(if (all.length <= MAX_CHARS) all else all.takeLast(MAX_CHARS).substringAfter('\n', ""))
    }
    override fun replace(text: String) = store.putString(KEY, text.ifEmpty { null })
    override fun clear() = store.putString(KEY, null)

    private companion object {
        const val KEY = "history_v1"
        const val MAX_CHARS = 400_000
    }
}
