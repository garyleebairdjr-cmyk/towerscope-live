package io.towerscope.calc

import io.towerscope.data.AppSettings
import io.towerscope.model.CellObservation
import io.towerscope.model.GeoFix
import io.towerscope.model.NetworkStatus
import io.towerscope.model.RadioType
import io.towerscope.model.TowerLocation
import io.towerscope.model.TowerLookup
import kotlin.math.max
import kotlin.math.sqrt

enum class Confidence(val label: String) { HIGH("High"), MEDIUM("Medium"), LOW("Low") }

enum class DistanceMethod(val label: String, val explanation: String) {
    GPS_DATABASE(
        "GPS ↔ tower database",
        "Great-circle distance between your GPS fix and the tower's surveyed position in the cell database. Accuracy depends on how well the tower has been surveyed (see range & samples).",
    ),
    BROADCAST(
        "GPS ↔ broadcast position",
        "CDMA base stations broadcast their own coordinates; this is the distance from your GPS fix to that position.",
    ),
    TIMING_ADVANCE(
        "Timing advance",
        "Round-trip radio timing measured by the tower. Each LTE step is ≈78 m of one-way distance (GSM ≈554 m). Reflections can only make it longer, never shorter.",
    ),
    PATH_LOSS(
        "Path-loss model",
        "Backs out path loss (assumed transmit power − measured signal) and inverts the Okumura-Hata / COST-231 model for your environment. Good to within a factor of ~2.",
    ),
    FREE_SPACE(
        "Free-space limit",
        "The distance at which this path loss would occur with nothing in the way. Real distance is almost always shorter — treat it as an upper bound.",
    ),
}

data class DistanceEstimate(
    val method: DistanceMethod,
    val meters: Double,
    val uncertaintyM: Double?,
    val confidence: Confidence,
    val detail: String,
)

data class DecodedField(val label: String, val value: String, val hint: String? = null)

data class CellAnalysis(
    val obs: CellObservation,
    val band: BandInfo?,
    val wavelengthM: Double?,
    val decoded: List<DecodedField>,
    val tower: TowerLocation?,
    val distances: List<DistanceEstimate>,
    val bestDistance: DistanceEstimate?,
    val bearingDeg: Double?,
    val transmitReferenceDbm: Double,
    val pathLossDb: Double?,
    val throughput: ThroughputEstimate?,
    val score: Int?,
    val grade: Grade,
    val fresnelRadiusM: Double?,
    val radioHorizonKm: Double,
    val elevationDeg: Double?,
    val dopplerHz: Double?,
    val noiseFloorPerReDbm: Double,
    val noiseLimitedSnrDb: Double?,
)

object Analyzer {
    fun analyze(
        obs: CellObservation,
        lookup: TowerLookup?,
        fix: GeoFix?,
        settings: AppSettings,
        network: NetworkStatus?,
    ): CellAnalysis {
        val id = obs.identity
        val s = obs.signal
        val band = Bands.forCell(id.radio, id.channel, id.bands, id.mcc)
        val freq = band?.dlMHz

        val tower: TowerLocation? = when {
            lookup is TowerLookup.Found -> lookup.location
            id.broadcastLat != null && id.broadcastLon != null ->
                TowerLocation(id.broadcastLat, id.broadcastLon, null, null, "Network broadcast")
            else -> null
        }

        val distances = mutableListOf<DistanceEstimate>()

        // 1) GPS ↔ known tower position
        if (tower != null && fix != null) {
            val d = Geo.distanceM(fix.lat, fix.lon, tower.lat, tower.lon)
            val unc = sqrt((fix.accuracyM ?: 20.0).let { it * it } + (tower.rangeM?.let { minOf(it, 2000.0) * 0.25 } ?: 150.0).let { it * it })
            val conf = when {
                tower.source == "Network broadcast" -> Confidence.HIGH
                (tower.samples ?: 0) >= 20 -> Confidence.HIGH
                (tower.samples ?: 0) >= 3 -> Confidence.MEDIUM
                else -> Confidence.LOW
            }
            distances += DistanceEstimate(
                if (tower.source == "Network broadcast") DistanceMethod.BROADCAST else DistanceMethod.GPS_DATABASE,
                d, unc, conf,
                "Tower position from ${tower.source}" + (tower.samples?.let { ", $it samples" } ?: "") +
                    "; GPS ±${(fix.accuracyM ?: 0.0).fmt(0)} m",
            )
        }

        // 2) Timing advance
        val taDistance: Pair<Double, Double>? = when {
            s.timingAdvanceMicros != null && s.timingAdvanceMicros > 0 -> {
                val d = RfModels.timingAdvanceMicrosToM(s.timingAdvanceMicros)
                d to 75.0
            }
            s.timingAdvance != null -> RfModels.timingAdvanceStepM(id.radio)?.let { step ->
                (s.timingAdvance * step + step / 2) to step / 2
            }
            else -> null
        }
        taDistance?.let { (d, unc) ->
            distances += DistanceEstimate(
                DistanceMethod.TIMING_ADVANCE, d, unc, Confidence.HIGH,
                if (s.timingAdvanceMicros != null) "TA ${s.timingAdvanceMicros} µs" else "TA ${s.timingAdvance} × ${RfModels.timingAdvanceStepM(id.radio)!!.fmt(1)} m",
            )
        }

        // 3) Path loss models
        val txRef = RfModels.transmitReferenceDbm(id.radio, settings.referenceEirpDbm)
        val measured = when (id.radio) {
            RadioType.LTE, RadioType.NR -> s.rsrp ?: s.dbm
            RadioType.WCDMA, RadioType.TDSCDMA -> s.rscp ?: s.dbm
            RadioType.GSM -> s.rssi ?: s.dbm
            else -> s.dbm
        }
        val pathLoss = measured?.let { txRef - it }
        if (pathLoss != null && freq != null && pathLoss > 40) {
            val hata = RfModels.hataDistanceKm(pathLoss, freq, settings.towerHeightM, settings.handsetHeightM, settings.environment) * 1000
            distances += DistanceEstimate(
                DistanceMethod.PATH_LOSS, hata.coerceIn(10.0, 80_000.0), hata * 0.5, Confidence.LOW,
                "PL ${pathLoss.fmt(0)} dB @ ${Fmt.mhz(freq)}, ${settings.environment.label.lowercase()}, tower ${settings.towerHeightM.fmt(0)} m" +
                    if (freq > 2000) " (COST-231 extrapolated above 2 GHz)" else "",
            )
            val fs = RfModels.freeSpaceDistanceKm(pathLoss, freq) * 1000
            distances += DistanceEstimate(
                DistanceMethod.FREE_SPACE, fs.coerceIn(10.0, 500_000.0), null, Confidence.LOW,
                "Upper bound: FSPL ${pathLoss.fmt(0)} dB @ ${Fmt.mhz(freq)}",
            )
        }

        val best = distances.firstOrNull { it.method == DistanceMethod.GPS_DATABASE || it.method == DistanceMethod.BROADCAST }
            ?: distances.firstOrNull { it.method == DistanceMethod.TIMING_ADVANCE }
            ?: distances.firstOrNull { it.method == DistanceMethod.PATH_LOSS }

        val bearing = if (tower != null && fix != null) Geo.bearingDeg(fix.lat, fix.lon, tower.lat, tower.lon) else null

        val throughput = Throughput.estimate(
            id.radio, band, s, id.bandwidthKhz,
            if (obs.role == io.towerscope.model.ConnectionRole.PRIMARY) network?.cellBandwidthsKhz.orEmpty() else emptyList(),
            pathLoss, settings.handsetTxPowerDbm,
        )

        val score = SignalQuality.score(id.radio, s)
        val noisePerRe = RfModels.noiseFloorDbm(if (id.radio == RadioType.NR) 30_000.0 else 15_000.0, 7.0)
        val bestM = best?.meters

        return CellAnalysis(
            obs = obs,
            band = band,
            wavelengthM = freq?.let { RfModels.wavelengthM(it) },
            decoded = decode(obs),
            tower = tower,
            distances = distances,
            bestDistance = best,
            bearingDeg = bearing,
            transmitReferenceDbm = txRef,
            pathLossDb = pathLoss?.toDouble(),
            throughput = throughput,
            score = score,
            grade = SignalQuality.gradeForScore(score),
            fresnelRadiusM = if (bestM != null && freq != null) RfModels.fresnelRadiusM(bestM / 1000, freq) else null,
            radioHorizonKm = RfModels.radioHorizonKm(settings.towerHeightM, settings.handsetHeightM),
            elevationDeg = bestM?.let { RfModels.elevationAngleDeg(settings.towerHeightM - settings.handsetHeightM, it) },
            dopplerHz = if (fix?.speedMps != null && freq != null) RfModels.dopplerHz(fix.speedMps, freq) else null,
            noiseFloorPerReDbm = noisePerRe,
            noiseLimitedSnrDb = (s.rsrp ?: if (id.radio == RadioType.LTE || id.radio == RadioType.NR) s.dbm else null)?.let { it - noisePerRe },
        )
    }

    /** Break network identifiers into their engineering components. */
    fun decode(obs: CellObservation): List<DecodedField> {
        val id = obs.identity
        val out = mutableListOf<DecodedField>()
        id.mcc?.let { out += DecodedField("MCC", "$it", Mcc.country(it)) }
        id.mnc?.let { out += DecodedField("MNC", it.toString().padStart(2, '0'), "Mobile network code") }
        when (id.radio) {
            RadioType.LTE -> {
                id.area?.let { out += DecodedField("TAC", "$it", Fmt.hex(it) + " · tracking area") }
                id.cellId?.let { eci ->
                    out += DecodedField("ECI", "$eci", Fmt.hex(eci) + " · 28-bit E-UTRAN cell ID")
                    out += DecodedField("eNB ID", "${eci shr 8}", "Tower (eNodeB) identifier = ECI ÷ 256")
                    out += DecodedField("Sector / cell", "${eci and 0xFF}", "Local cell ID = ECI mod 256")
                }
                id.pci?.let { pciFields(it, out) }
                id.channel?.let { out += DecodedField("EARFCN", "$it", "E-UTRA absolute radio channel") }
            }
            RadioType.NR -> {
                id.area?.let { out += DecodedField("TAC", "$it", Fmt.hex(it) + " · tracking area") }
                id.cellId?.let { nci ->
                    out += DecodedField("NCI", "$nci", Fmt.hex(nci) + " · 36-bit NR cell ID")
                    out += DecodedField("gNB ID", "${nci shr 12}", "Assuming a 24-bit gNB ID (operator-specific, 22–32 bits)")
                    out += DecodedField("Sector / cell", "${nci and 0xFFF}", "Local cell ID (12-bit, same assumption)")
                }
                id.pci?.let { pciFields(it, out) }
                id.channel?.let { out += DecodedField("NR-ARFCN", "$it", "NR absolute radio channel") }
            }
            RadioType.WCDMA, RadioType.TDSCDMA -> {
                id.area?.let { out += DecodedField("LAC", "$it", Fmt.hex(it) + " · location area") }
                id.cellId?.let { cid ->
                    out += DecodedField("UTRAN CID", "$cid", Fmt.hex(cid))
                    out += DecodedField("RNC ID", "${cid shr 16}", "Radio network controller = CID ÷ 65536")
                    out += DecodedField("Cell", "${cid and 0xFFFF}", "Cell within the RNC")
                }
                id.pci?.let { out += DecodedField(if (id.radio == RadioType.WCDMA) "PSC" else "CPID", "$it", "Primary scrambling code") }
                id.channel?.let { out += DecodedField("UARFCN", "$it", "UTRA absolute radio channel") }
            }
            RadioType.GSM -> {
                id.area?.let { out += DecodedField("LAC", "$it", Fmt.hex(it) + " · location area") }
                id.cellId?.let { out += DecodedField("CID", "$it", Fmt.hex(it)) }
                id.pci?.let { bsic ->
                    out += DecodedField("BSIC", "$bsic", "NCC ${bsic shr 3} · BCC ${bsic and 7}")
                }
                id.channel?.let { out += DecodedField("ARFCN", "$it", "Absolute radio channel") }
            }
            RadioType.CDMA -> {
                id.cdmaSystemId?.let { out += DecodedField("SID", "$it", "System ID") }
                id.area?.let { out += DecodedField("NID", "$it", "Network ID") }
                id.cellId?.let { out += DecodedField("BID", "$it", "Base station ID") }
            }
            RadioType.UNKNOWN -> {}
        }
        return out
    }

    private fun pciFields(pci: Int, out: MutableList<DecodedField>) {
        out += DecodedField("PCI", "$pci", "Physical cell ID (0–${if (pci > 503) 1007 else 503})")
        out += DecodedField("PCI group / PSS", "${pci / 3} / ${pci % 3}", "PCI = 3 × SSS group + PSS. PSS often maps to the sector (0/1/2)")
    }

    fun nearestKnown(fix: GeoFix, towers: List<io.towerscope.model.DatabaseTower>): Pair<io.towerscope.model.DatabaseTower, Double>? =
        towers.map { it to Geo.distanceM(fix.lat, fix.lon, it.location.lat, it.location.lon) }.minByOrNull { it.second }

    fun combinedThroughput(serving: CellAnalysis?, nsa: CellAnalysis?): Pair<Double, Double>? {
        val a = serving?.throughput ?: return nsa?.throughput?.let { it.dlMbps to it.ulMbps }
        val b = nsa?.throughput ?: return a.dlMbps to a.ulMbps
        return (a.dlMbps + b.dlMbps) to max(a.ulMbps, b.ulMbps)
    }
}

/** Minimal MCC → country table for the most common networks. */
object Mcc {
    private val table = mapOf(
        202 to "Greece", 204 to "Netherlands", 206 to "Belgium", 208 to "France", 214 to "Spain", 222 to "Italy",
        226 to "Romania", 228 to "Switzerland", 230 to "Czechia", 232 to "Austria", 234 to "United Kingdom", 235 to "United Kingdom",
        238 to "Denmark", 240 to "Sweden", 242 to "Norway", 244 to "Finland", 250 to "Russia", 255 to "Ukraine", 260 to "Poland",
        262 to "Germany", 268 to "Portugal", 272 to "Ireland", 286 to "Türkiye", 302 to "Canada", 310 to "United States",
        311 to "United States", 312 to "United States", 313 to "United States", 314 to "United States", 315 to "United States",
        316 to "United States", 334 to "Mexico", 404 to "India", 405 to "India", 420 to "Saudi Arabia", 424 to "UAE",
        425 to "Israel", 440 to "Japan", 450 to "South Korea", 454 to "Hong Kong", 460 to "China", 466 to "Taiwan",
        505 to "Australia", 510 to "Indonesia", 515 to "Philippines", 520 to "Thailand", 525 to "Singapore", 530 to "New Zealand",
        602 to "Egypt", 621 to "Nigeria", 655 to "South Africa", 722 to "Argentina", 724 to "Brazil", 730 to "Chile", 732 to "Colombia",
    )

    fun country(mcc: Int): String = table[mcc] ?: "Mobile country code"
}
