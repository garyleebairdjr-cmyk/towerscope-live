package io.towerscope.calc

import io.towerscope.model.RadioType
import io.towerscope.model.SignalMetrics
import io.towerscope.model.SignalSample

enum class Grade(val label: String, val score: Int) {
    EXCELLENT("Excellent", 4),
    GOOD("Good", 3),
    FAIR("Fair", 2),
    POOR("Poor", 1),
    BAD("Very poor", 0),
    UNKNOWN("Unknown", -1),
}

/** A threshold: values >= [min] get [grade] (thresholds are ordered best → worst). */
data class Threshold(val min: Double, val grade: Grade)

enum class MetricKind(
    val title: String,
    val shortTitle: String,
    val unit: String,
    val rangeMin: Double,
    val rangeMax: Double,
    val thresholds: List<Threshold>,
    val what: String,
    val affects: String,
    val improve: String,
) {
    RSRP(
        "Reference Signal Received Power", "RSRP", "dBm", -140.0, -44.0,
        listOf(Threshold(-80.0, Grade.EXCELLENT), Threshold(-90.0, Grade.GOOD), Threshold(-100.0, Grade.FAIR), Threshold(-110.0, Grade.POOR), Threshold(-200.0, Grade.BAD)),
        what = "The average power of the tower's reference signal, measured on a single 15 kHz resource element. It is the purest measure of how loud the tower is at your location, independent of how busy the cell is.",
        affects = "Distance to the tower, frequency band (low bands travel farther), walls and terrain between you and the antenna, antenna tilt, and whether you are inside the antenna's main beam.",
        improve = "Move toward a window or outdoors, go higher, or move to the side of a building facing the tower. Use the bearing arrow on the tower screen to find that direction.",
    ),
    RSRQ(
        "Reference Signal Received Quality", "RSRQ", "dB", -20.0, -3.0,
        listOf(Threshold(-10.0, Grade.EXCELLENT), Threshold(-13.0, Grade.GOOD), Threshold(-16.0, Grade.FAIR), Threshold(-19.0, Grade.POOR), Threshold(-100.0, Grade.BAD)),
        what = "RSRQ = N × RSRP / RSSI. It compares the wanted reference signal to the total power received across the channel, so it falls when the cell is heavily loaded or neighbouring cells interfere.",
        affects = "Cell load (other users' traffic raises RSSI), interference from neighbouring sites on the same frequency, and being at a cell edge between two towers.",
        improve = "RSRQ is mostly a network property. Moving so one tower clearly dominates — usually closer to it — helps. A poor RSRQ with a strong RSRP usually means the cell is congested or you are between cells.",
    ),
    SINR(
        "Signal to Interference plus Noise Ratio", "SINR", "dB", -10.0, 30.0,
        listOf(Threshold(20.0, Grade.EXCELLENT), Threshold(13.0, Grade.GOOD), Threshold(5.0, Grade.FAIR), Threshold(0.0, Grade.POOR), Threshold(-100.0, Grade.BAD)),
        what = "How far the useful signal stands above interference and noise. SINR decides which modulation (QPSK → 256-QAM) and how many MIMO layers the tower can use, so it is the strongest predictor of speed.",
        affects = "Interference from other cells, thermal noise at weak signal, multipath, and your own movement.",
        improve = "Avoid spots midway between two towers. A clear line toward the serving tower raises SINR more than raw signal strength does.",
    ),
    RSSI(
        "Received Signal Strength Indicator", "RSSI", "dBm", -120.0, -25.0,
        listOf(Threshold(-65.0, Grade.EXCELLENT), Threshold(-75.0, Grade.GOOD), Threshold(-85.0, Grade.FAIR), Threshold(-95.0, Grade.POOR), Threshold(-200.0, Grade.BAD)),
        what = "Total power received across the whole channel bandwidth: the wanted signal, plus interference, plus noise. For 2G this is the main signal metric.",
        affects = "Everything RSRP is affected by, plus cell load and interference — a busy cell raises RSSI without improving the link.",
        improve = "Use RSRP and SINR to judge LTE/5G links; RSSI alone can be misleading.",
    ),
    RSCP(
        "Received Signal Code Power", "RSCP", "dBm", -120.0, -25.0,
        listOf(Threshold(-70.0, Grade.EXCELLENT), Threshold(-85.0, Grade.GOOD), Threshold(-95.0, Grade.FAIR), Threshold(-105.0, Grade.POOR), Threshold(-200.0, Grade.BAD)),
        what = "The power of the 3G pilot channel (CPICH) after despreading — the 3G equivalent of RSRP.",
        affects = "Distance, obstructions and band.",
        improve = "Move toward windows or outdoors.",
    ),
    ECNO(
        "Energy per Chip to Noise", "Ec/No", "dB", -24.0, 0.0,
        listOf(Threshold(-6.0, Grade.EXCELLENT), Threshold(-10.0, Grade.GOOD), Threshold(-14.0, Grade.FAIR), Threshold(-18.0, Grade.POOR), Threshold(-100.0, Grade.BAD)),
        what = "3G pilot quality relative to the total received power — the 3G equivalent of RSRQ/SINR.",
        affects = "Cell load and interference from neighbouring cells.",
        improve = "Move so one cell dominates.",
    ),
    CQI(
        "Channel Quality Indicator", "CQI", "", 0.0, 15.0,
        listOf(Threshold(13.0, Grade.EXCELLENT), Threshold(10.0, Grade.GOOD), Threshold(7.0, Grade.FAIR), Threshold(4.0, Grade.POOR), Threshold(-1.0, Grade.BAD)),
        what = "A 0–15 index your phone reports to the tower, recommending the highest modulation and coding scheme it can decode with ≤10 % errors. Only available while transferring data.",
        affects = "SINR and receiver quality.",
        improve = "Same as SINR.",
    ),
    TIMING_ADVANCE(
        "Timing Advance", "TA", "", 0.0, 1282.0, emptyList(),
        what = "How early the phone must transmit so its signal reaches the tower in its time slot. It's a direct round-trip-time measurement, so it converts to distance (LTE: ≈78 m per step). Only reported while the phone is actively connected.",
        affects = "Radio-path distance to the tower. Reflections can make it read longer than the straight-line distance.",
        improve = "Not something to improve — useful for locating the tower. Start a speed test to force an active connection so TA gets reported.",
    ),
    DBM(
        "Signal Strength", "Signal", "dBm", -130.0, -50.0,
        listOf(Threshold(-80.0, Grade.EXCELLENT), Threshold(-90.0, Grade.GOOD), Threshold(-100.0, Grade.FAIR), Threshold(-110.0, Grade.POOR), Threshold(-200.0, Grade.BAD)),
        what = "The overall signal strength figure the operating system reports for this cell (RSRP for LTE/NR, RSCP for 3G, RSSI for 2G).",
        affects = "Distance, obstructions and band.",
        improve = "Move toward windows or outdoors.",
    );

    fun grade(value: Double?): Grade {
        if (value == null || thresholds.isEmpty()) return Grade.UNKNOWN
        return thresholds.firstOrNull { value >= it.min }?.grade ?: Grade.BAD
    }

    /** 0..1 position of [value] on the display range. */
    fun fraction(value: Double): Float = ((value - rangeMin) / (rangeMax - rangeMin)).coerceIn(0.0, 1.0).toFloat()

    fun valueOf(s: SignalMetrics): Double? = when (this) {
        RSRP -> s.rsrp?.toDouble()
        RSRQ -> s.rsrq?.toDouble()
        SINR -> s.sinr
        RSSI -> s.rssi?.toDouble()
        RSCP -> s.rscp?.toDouble()
        ECNO -> s.ecNo
        CQI -> s.cqi?.toDouble()
        TIMING_ADVANCE -> s.timingAdvance?.toDouble() ?: s.timingAdvanceMicros?.toDouble()
        DBM -> s.dbm?.toDouble()
    }

    fun valueOf(s: SignalSample): Double? = when (this) {
        RSRP -> s.rsrp?.toDouble()
        RSRQ -> s.rsrq?.toDouble()
        SINR -> s.sinr
        RSSI -> s.rssi?.toDouble()
        CQI -> s.cqi?.toDouble()
        TIMING_ADVANCE -> s.timingAdvance?.toDouble()
        DBM, RSCP -> s.dbm?.toDouble()
        ECNO -> null
    }
}

object SignalQuality {
    /** The metrics that make sense for a radio type, in display order. */
    fun metricsFor(radio: RadioType): List<MetricKind> = when (radio) {
        RadioType.LTE -> listOf(MetricKind.RSRP, MetricKind.RSRQ, MetricKind.SINR, MetricKind.RSSI, MetricKind.CQI, MetricKind.TIMING_ADVANCE)
        RadioType.NR -> listOf(MetricKind.RSRP, MetricKind.RSRQ, MetricKind.SINR, MetricKind.TIMING_ADVANCE)
        RadioType.WCDMA, RadioType.TDSCDMA -> listOf(MetricKind.RSCP, MetricKind.ECNO, MetricKind.DBM)
        RadioType.GSM -> listOf(MetricKind.RSSI, MetricKind.TIMING_ADVANCE)
        RadioType.CDMA -> listOf(MetricKind.DBM, MetricKind.ECNO)
        RadioType.UNKNOWN -> listOf(MetricKind.DBM)
    }

    /** Headline metric used for gauges. */
    fun primaryMetric(radio: RadioType): MetricKind = when (radio) {
        RadioType.LTE, RadioType.NR -> MetricKind.RSRP
        RadioType.WCDMA, RadioType.TDSCDMA -> MetricKind.RSCP
        RadioType.GSM -> MetricKind.RSSI
        else -> MetricKind.DBM
    }

    fun primaryValue(radio: RadioType, s: SignalMetrics): Double? =
        primaryMetric(radio).valueOf(s) ?: s.dbm?.toDouble()

    /**
     * Composite 0–100 link score: weighted blend of strength (RSRP), cleanliness (SINR)
     * and quality (RSRQ), each normalised over its practical range.
     */
    fun score(radio: RadioType, s: SignalMetrics): Int? {
        fun norm(v: Double?, lo: Double, hi: Double) = v?.let { ((it - lo) / (hi - lo)).coerceIn(0.0, 1.0) }
        val parts = when (radio) {
            RadioType.LTE, RadioType.NR -> listOf(
                norm(s.rsrp?.toDouble() ?: s.dbm?.toDouble(), -120.0, -75.0) to 0.45,
                norm(s.sinr, -5.0, 25.0) to 0.35,
                norm(s.rsrq?.toDouble(), -20.0, -6.0) to 0.20,
            )
            RadioType.WCDMA, RadioType.TDSCDMA -> listOf(
                norm(s.rscp?.toDouble() ?: s.dbm?.toDouble(), -115.0, -65.0) to 0.6,
                norm(s.ecNo, -20.0, -4.0) to 0.4,
            )
            else -> listOf(norm(s.rssi?.toDouble() ?: s.dbm?.toDouble(), -110.0, -60.0) to 1.0)
        }.filter { it.first != null }
        if (parts.isEmpty()) return null
        val wSum = parts.sumOf { it.second }
        return (parts.sumOf { it.first!! * it.second } / wSum * 100).toInt()
    }

    fun gradeForScore(score: Int?): Grade = when {
        score == null -> Grade.UNKNOWN
        score >= 80 -> Grade.EXCELLENT
        score >= 60 -> Grade.GOOD
        score >= 40 -> Grade.FAIR
        score >= 20 -> Grade.POOR
        else -> Grade.BAD
    }
}

/** LTE 4-bit CQI table (3GPP TS 36.213 Table 7.2.3-1). */
object Cqi {
    data class Entry(val cqi: Int, val modulation: String, val codeRate: Double, val efficiency: Double)

    private val table = listOf(
        Entry(1, "QPSK", 78 / 1024.0, 0.1523), Entry(2, "QPSK", 120 / 1024.0, 0.2344),
        Entry(3, "QPSK", 193 / 1024.0, 0.3770), Entry(4, "QPSK", 308 / 1024.0, 0.6016),
        Entry(5, "QPSK", 449 / 1024.0, 0.8770), Entry(6, "QPSK", 602 / 1024.0, 1.1758),
        Entry(7, "16-QAM", 378 / 1024.0, 1.4766), Entry(8, "16-QAM", 490 / 1024.0, 1.9141),
        Entry(9, "16-QAM", 616 / 1024.0, 2.4063), Entry(10, "64-QAM", 466 / 1024.0, 2.7305),
        Entry(11, "64-QAM", 567 / 1024.0, 3.3223), Entry(12, "64-QAM", 666 / 1024.0, 3.9023),
        Entry(13, "64-QAM", 772 / 1024.0, 4.5234), Entry(14, "64-QAM", 873 / 1024.0, 5.1152),
        Entry(15, "64-QAM", 948 / 1024.0, 5.5547),
    )

    fun entry(cqi: Int): Entry? = table.firstOrNull { it.cqi == cqi }
}
