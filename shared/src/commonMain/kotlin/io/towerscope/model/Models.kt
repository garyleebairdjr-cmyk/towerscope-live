package io.towerscope.model

/** Radio access technology of a cell. */
enum class RadioType(val label: String, val generation: String, val openCellIdName: String) {
    GSM("GSM", "2G", "GSM"),
    CDMA("CDMA", "2G/3G", "CDMA"),
    WCDMA("WCDMA", "3G", "UMTS"),
    TDSCDMA("TD-SCDMA", "3G", "UMTS"),
    LTE("LTE", "4G", "LTE"),
    NR("NR", "5G", "NR"),
    UNKNOWN("Unknown", "—", "");

    companion object {
        fun fromOpenCellId(name: String?): RadioType = when (name?.uppercase()) {
            "GSM" -> GSM
            "CDMA" -> CDMA
            "UMTS" -> WCDMA
            "LTE" -> LTE
            "NR" -> NR
            else -> UNKNOWN
        }
    }
}

/** Static identity of a cell as broadcast by the network. Any field may be unknown. */
data class CellIdentity(
    val radio: RadioType,
    val mcc: Int? = null,
    val mnc: Int? = null,
    /** LAC (2G/3G), TAC (4G/5G) or CDMA network ID. */
    val area: Long? = null,
    /** CID (2G), UTRAN CID (3G), ECI (4G), NCI (5G) or CDMA base station ID. */
    val cellId: Long? = null,
    /** PCI (4G/5G), PSC (3G), BSIC (2G) or CPID (TD-SCDMA). */
    val pci: Int? = null,
    /** ARFCN / UARFCN / EARFCN / NR-ARFCN. */
    val channel: Int? = null,
    val bandwidthKhz: Int? = null,
    val bands: List<Int> = emptyList(),
    val operatorName: String? = null,
    /** CDMA base stations broadcast their own coordinates. */
    val broadcastLat: Double? = null,
    val broadcastLon: Double? = null,
    val cdmaSystemId: Int? = null,
) {
    /** True when the identity is complete enough to look the tower up in a database. */
    val isLookupable: Boolean get() = mcc != null && mnc != null && area != null && cellId != null

    /** Stable key used for history, caching and navigation. */
    val key: String
        get() = if (isLookupable) "${radio.name}:$mcc:$mnc:$area:$cellId"
        else "${radio.name}:pci$pci:ch$channel"

    val mccMnc: String?
        get() = if (mcc != null && mnc != null) "$mcc-${mnc.toString().padStart(2, '0')}" else null
}

/** Signal measurements. Unified fields: for NR, rsrp/rsrq/sinr carry the SS-* values. */
data class SignalMetrics(
    /** Generic signal strength (dBm) as reported by the OS. */
    val dbm: Int? = null,
    /** OS bar level 0..4. */
    val level: Int? = null,
    val asu: Int? = null,
    val rssi: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Double? = null,
    val cqi: Int? = null,
    /** Raw timing advance in the radio's native units (LTE: 16·Ts, GSM: bit periods). */
    val timingAdvance: Int? = null,
    /** NR timing advance in microseconds. */
    val timingAdvanceMicros: Int? = null,
    /** WCDMA Ec/No (dB) or CDMA Ec/Io (dB). */
    val ecNo: Double? = null,
    /** WCDMA RSCP / TD-SCDMA RSCP (dBm). */
    val rscp: Int? = null,
    val bitErrorRate: Int? = null,
    val csiRsrp: Int? = null,
    val csiRsrq: Int? = null,
    val csiSinr: Int? = null,
)

enum class ConnectionRole(val label: String) {
    PRIMARY("Serving"),
    SECONDARY("Secondary"),
    NEIGHBOR("Neighbor"),
}

/** A single observed cell at a moment in time. */
data class CellObservation(
    val identity: CellIdentity,
    val signal: SignalMetrics,
    val role: ConnectionRole,
    val timestampMs: Long,
    /** True when synthesised from SignalStrength (e.g. 5G NSA leg not reported in cell list). */
    val synthetic: Boolean = false,
) {
    val key: String get() = identity.key + if (synthetic) ":nsa" else ""
    val isServing: Boolean get() = role != ConnectionRole.NEIGHBOR
}

enum class Transport(val label: String) {
    CELLULAR("Cellular"), WIFI("Wi-Fi"), ETHERNET("Ethernet"), VPN("VPN"), OTHER("Other"), NONE("Offline"), UNKNOWN("Unknown")
}

data class NetworkStatus(
    val operatorName: String? = null,
    val operatorMccMnc: String? = null,
    val simOperatorName: String? = null,
    /** e.g. "LTE", "5G NSA", "5G SA", "HSPA+". */
    val networkTypeLabel: String? = null,
    val isNrNsa: Boolean = false,
    val isRoaming: Boolean? = null,
    val transport: Transport = Transport.UNKNOWN,
    val linkDownKbps: Int? = null,
    val linkUpKbps: Int? = null,
    /** Bandwidth of each aggregated component carrier (kHz). */
    val cellBandwidthsKhz: List<Int> = emptyList(),
    val duplexMode: String? = null,
    val simCount: Int? = null,
)

data class RadioCapabilities(
    val canReadCells: Boolean,
    val limitationNote: String? = null,
)

data class RadioSnapshot(
    val cells: List<CellObservation>,
    val network: NetworkStatus,
    val timestampMs: Long,
    val capabilities: RadioCapabilities,
) {
    val serving: CellObservation?
        get() = cells.firstOrNull { it.role == ConnectionRole.PRIMARY && !it.synthetic }
            ?: cells.firstOrNull { it.role == ConnectionRole.PRIMARY }
            ?: cells.firstOrNull { it.isServing }

    val neighbors: List<CellObservation> get() = cells.filter { it.role == ConnectionRole.NEIGHBOR }
}

data class GeoFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Double? = null,
    val altitudeM: Double? = null,
    val speedMps: Double? = null,
    val bearingDeg: Double? = null,
    val timestampMs: Long,
    val provider: String? = null,
)

enum class PlatformKind { ANDROID, IOS }

data class DeviceInfo(
    val platform: PlatformKind,
    val manufacturer: String,
    val model: String,
    val osName: String,
    val osVersion: String,
    val apiLevel: Int? = null,
)

/** Known physical location of a tower from a database or broadcast. */
data class TowerLocation(
    val lat: Double,
    val lon: Double,
    /** Estimated coverage radius / position uncertainty reported by the source (m). */
    val rangeM: Double? = null,
    val samples: Int? = null,
    val source: String,
    val updatedEpochSec: Long? = null,
)

/** A tower record from the area database (not necessarily seen by this phone). */
data class DatabaseTower(
    val identity: CellIdentity,
    val location: TowerLocation,
    val averageSignalDbm: Int? = null,
)

sealed interface TowerLookup {
    data object NoApiKey : TowerLookup
    data object Loading : TowerLookup
    data object NotFound : TowerLookup
    data object NotLookupable : TowerLookup
    data class Found(val location: TowerLocation) : TowerLookup
    data class Failed(val message: String) : TowerLookup
}

/** One history sample of the serving cell signal. */
data class SignalSample(
    val timestampMs: Long,
    val cellKey: String,
    val radio: RadioType,
    val dbm: Int?,
    val rsrp: Int?,
    val rsrq: Int?,
    val sinr: Double?,
    val rssi: Int?,
    val cqi: Int?,
    val timingAdvance: Int?,
)
