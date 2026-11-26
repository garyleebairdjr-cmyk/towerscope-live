package io.towerscope.calc

import io.towerscope.model.RadioType
import io.towerscope.model.SignalMetrics
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min

data class ThroughputEstimate(
    val dlMbps: Double,
    val ulMbps: Double,
    val dlPeakMbps: Double,
    val ulPeakMbps: Double,
    val bandwidthMHz: Double?,
    val bandwidthSource: String,
    val dlLayers: Int,
    val effectiveLayers: Double,
    val sinrDlDb: Double?,
    val sinrDlSource: String,
    val sinrUlDb: Double?,
    val ulAllocationMHz: Double?,
    val spectralEfficiencyDl: Double?,
    val modulationDl: String,
    val method: String,
)

/**
 * Link-level throughput estimates.
 *
 * LTE/NR use the attenuated-Shannon model of 3GPP TR 36.942 (Annex A.2):
 * `eff = α · log2(1 + SINR)`, clipped at the highest practical modulation, applied to the
 * carrier's usable bandwidth after control overhead, TDD split and MIMO rank. These are
 * single-user link capacities — what the radio link supports if the cell isn't congested.
 */
object Throughput {
    private const val ALPHA_DL = 0.6
    private const val ALPHA_UL = 0.5
    private const val MAX_EFF_DL = 5.5 // ≈ practical 256-QAM per layer
    private const val MAX_EFF_UL = 4.2 // ≈ practical 64-QAM
    private const val PEAK_EFF_DL = 7.4 // 256-QAM, code rate ≈ 0.93
    private const val PEAK_EFF_UL = 5.55 // 64-QAM, code rate ≈ 0.93
    private const val MIN_SINR_DB = -10.0
    private const val BS_ANTENNA_GAIN_DBI = 15.0
    private const val BS_FEEDER_LOSS_DB = 2.0
    private const val BS_NOISE_FIGURE_DB = 3.0
    private const val UL_INTERFERENCE_MARGIN_DB = 3.0

    fun modulationFor(sinrDb: Double?): String = when {
        sinrDb == null -> "—"
        sinrDb >= 21 -> "256-QAM"
        sinrDb >= 15 -> "64-QAM"
        sinrDb >= 9 -> "16-QAM"
        sinrDb >= -5 -> "QPSK"
        else -> "Below decoding threshold"
    }

    fun shannonEfficiency(sinrDb: Double, alpha: Double, cap: Double): Double {
        if (sinrDb < MIN_SINR_DB) return 0.0
        return min(alpha * log2(1 + RfModels.dbToLinear(sinrDb)), cap)
    }

    /** Best available downlink SINR and where it came from. */
    fun downlinkSinr(signal: SignalMetrics): Pair<Double, String>? {
        signal.sinr?.let { return it to "Reported by modem" }
        val noiseLimited = signal.rsrp?.let { it - RfModels.noiseFloorDbm(15_000.0, 7.0) }
        signal.rsrq?.let { rsrq ->
            // Empirical RSRQ→SINR mapping for loaded LTE cells, bounded by the noise limit.
            val fromRsrq = 2.0 * (rsrq + 17.0)
            val v = if (noiseLimited != null) min(fromRsrq, noiseLimited) else fromRsrq
            return v.coerceIn(-10.0, 30.0) to "Estimated from RSRQ"
        }
        noiseLimited?.let { return (it - 10.0).coerceIn(-10.0, 25.0) to "Estimated from RSRP (10 dB interference margin)" }
        return null
    }

    fun estimate(
        radio: RadioType,
        band: BandInfo?,
        signal: SignalMetrics,
        reportedBandwidthKhz: Int?,
        aggregatedBandwidthsKhz: List<Int>,
        pathLossDb: Double?,
        handsetTxPowerDbm: Double,
    ): ThroughputEstimate? = when (radio) {
        RadioType.LTE, RadioType.NR -> ofdm(radio, band, signal, reportedBandwidthKhz, aggregatedBandwidthsKhz, pathLossDb, handsetTxPowerDbm)
        RadioType.WCDMA, RadioType.TDSCDMA -> legacy(
            dlPeak = 42.2, ulPeak = 5.76,
            quality = ((signal.ecNo ?: -12.0) + 18.0) / 14.0 * strengthFactor(signal.rscp ?: signal.dbm, -115.0, -75.0),
            method = "HSPA+ category model (DC-HSDPA 42 Mbps / HSUPA 5.76 Mbps) scaled by Ec/No and RSCP",
        )
        RadioType.GSM -> legacy(
            dlPeak = 0.237, ulPeak = 0.118,
            quality = strengthFactor(signal.rssi ?: signal.dbm, -105.0, -70.0),
            method = "EDGE (MCS-9, 4 timeslots DL / 2 UL) scaled by RSSI",
        )
        RadioType.CDMA -> legacy(
            dlPeak = 3.1, ulPeak = 1.8,
            quality = strengthFactor(signal.dbm, -105.0, -70.0),
            method = "EV-DO Rev. A peak rates scaled by signal strength",
        )
        RadioType.UNKNOWN -> null
    }

    private fun strengthFactor(dbm: Int?, lo: Double, hi: Double): Double =
        dbm?.let { ((it - lo) / (hi - lo)).coerceIn(0.05, 1.0) } ?: 0.5

    private fun legacy(dlPeak: Double, ulPeak: Double, quality: Double, method: String): ThroughputEstimate {
        val q = quality.coerceIn(0.03, 1.0)
        return ThroughputEstimate(
            dlMbps = dlPeak * q, ulMbps = ulPeak * q, dlPeakMbps = dlPeak, ulPeakMbps = ulPeak,
            bandwidthMHz = null, bandwidthSource = "Fixed channel", dlLayers = 1, effectiveLayers = 1.0,
            sinrDlDb = null, sinrDlSource = "—", sinrUlDb = null, ulAllocationMHz = null,
            spectralEfficiencyDl = null, modulationDl = "—", method = method,
        )
    }

    private fun ofdm(
        radio: RadioType,
        band: BandInfo?,
        signal: SignalMetrics,
        reportedBandwidthKhz: Int?,
        aggregatedBandwidthsKhz: List<Int>,
        pathLossDb: Double?,
        handsetTxPowerDbm: Double,
    ): ThroughputEstimate {
        val isNr = radio == RadioType.NR
        val (bwMHz, bwSource) = when {
            aggregatedBandwidthsKhz.size > 1 && !isNr ->
                aggregatedBandwidthsKhz.sum() / 1000.0 to "Carrier aggregation: " + aggregatedBandwidthsKhz.joinToString(" + ") { (it / 1000.0).fmt(0) } + " MHz"
            reportedBandwidthKhz != null && reportedBandwidthKhz > 0 ->
                reportedBandwidthKhz / 1000.0 to "Reported by cell"
            aggregatedBandwidthsKhz.size == 1 && !isNr ->
                aggregatedBandwidthsKhz[0] / 1000.0 to "Reported by network"
            isNr && band != null ->
                Bands.typicalNrBandwidthMHz(band.number) to "Typical for ${band.name} (assumed)"
            else -> (if (isNr) 20.0 else 10.0) to "Assumed (not reported)"
        }
        val tdd = band?.duplex == Duplex.TDD
        val dlShare = if (tdd) (if (isNr) 0.74 else 0.70) else 1.0
        val ulShare = if (tdd) (if (isNr) 0.23 else 0.25) else 1.0
        val occupied = if (isNr) 0.95 else 0.90
        val dlOverhead = if (isNr) 0.14 else 0.25
        val ulOverhead = if (isNr) 0.10 else 0.15
        val layers = when {
            isNr && band != null && band.dlMHz >= 24000 -> 2
            isNr && tdd -> 4
            else -> 2
        }
        val dlBwEff = bwMHz * occupied * (1 - dlOverhead) * dlShare
        val ulBwEff = (if (band?.duplex == Duplex.SDL) 0.0 else bwMHz) * occupied * (1 - ulOverhead) * ulShare

        val sinr = downlinkSinr(signal)
        val sinrDb = sinr?.first
        val eff = sinrDb?.let { shannonEfficiency(it, ALPHA_DL, MAX_EFF_DL) }
        val effLayers = if (sinrDb == null) 1.0 else 1.0 + (layers - 1) * ((sinrDb - 3.0) / 15.0).coerceIn(0.0, 1.0)
        val dlPeak = dlBwEff * layers * PEAK_EFF_DL
        val ulPeak = ulBwEff * PEAK_EFF_UL
        val dl = if (eff != null) min(dlBwEff * eff * effLayers, dlPeak) else 0.0

        // Uplink: power-limited link budget, choose the allocation that maximises throughput.
        var ul = 0.0
        var ulSinr: Double? = null
        var ulAlloc: Double? = null
        if (pathLossDb != null && ulBwEff > 0) {
            val rx = handsetTxPowerDbm + BS_ANTENNA_GAIN_DBI - BS_FEEDER_LOSS_DB - pathLossDb
            for (fraction in listOf(1.0, 0.5, 0.25, 0.1, 0.05, 0.02)) {
                val alloc = ulBwEff * fraction
                val noise = RfModels.noiseFloorDbm(alloc * 1e6 / (1 - ulOverhead), BS_NOISE_FIGURE_DB) + UL_INTERFERENCE_MARGIN_DB
                val s = min(rx - noise, 25.0)
                val t = alloc * shannonEfficiency(s, ALPHA_UL, MAX_EFF_UL)
                if (t > ul) {
                    ul = t; ulSinr = s; ulAlloc = alloc
                }
            }
        } else if (sinrDb != null) {
            ul = min(ulBwEff * shannonEfficiency(sinrDb - 6.0, ALPHA_UL, MAX_EFF_UL), ulPeak)
        }

        return ThroughputEstimate(
            dlMbps = dl,
            ulMbps = min(ul, ulPeak),
            dlPeakMbps = dlPeak,
            ulPeakMbps = ulPeak,
            bandwidthMHz = bwMHz,
            bandwidthSource = bwSource,
            dlLayers = layers,
            effectiveLayers = effLayers,
            sinrDlDb = sinrDb,
            sinrDlSource = sinr?.second ?: "Unavailable",
            sinrUlDb = ulSinr,
            ulAllocationMHz = ulAlloc,
            spectralEfficiencyDl = eff,
            modulationDl = modulationFor(sinrDb),
            method = "Attenuated Shannon (3GPP TR 36.942): α=${ALPHA_DL} DL / ${ALPHA_UL} UL, " +
                "${layers}×MIMO, ${((dlOverhead) * 100).fmt(0)} % control overhead" + if (tdd) ", TDD ${(dlShare * 100).fmt(0)}/${(ulShare * 100).fmt(0)} split" else "",
        )
    }

    fun combine(a: ThroughputEstimate, b: ThroughputEstimate): Pair<Double, Double> =
        (a.dlMbps + b.dlMbps) to (a.ulMbps + max(0.0, b.ulMbps))
}
