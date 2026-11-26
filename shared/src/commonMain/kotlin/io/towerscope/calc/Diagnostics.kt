package io.towerscope.calc

import io.towerscope.data.SpeedResult
import io.towerscope.model.CellObservation
import io.towerscope.model.NetworkStatus
import io.towerscope.model.RadioType
import io.towerscope.model.Transport

/** Phone state that affects radio performance, read from the OS. Null = unknown on this platform. */
data class DeviceConditions(
    val powerSaveMode: Boolean? = null,
    val vpnActive: Boolean? = null,
    /** Security patch date "YYYY-MM-DD" (Android); modem fixes ship with these updates. */
    val securityPatch: String? = null,
)

/** System settings pages the app can open. Apps can't change these settings themselves. */
enum class SettingsTarget(val buttonLabel: String) {
    AIRPLANE_MODE("Open airplane mode"),
    NETWORK_MODE("Open mobile network settings"),
    BATTERY_SAVER("Open battery saver"),
    VPN("Open VPN settings"),
    WIFI_CALLING("Open connection settings"),
    SYSTEM_UPDATE("Check for updates"),
}

/** Sections of the "Better signal" guide; advice links to the relevant one. */
enum class GuideTopic(val title: String) {
    SHORT_VERSION("The short version"),
    APP_LIMITS("What this app can and can't do"),
    READING_SIGNAL("Reading your signal"),
    LOCATION("Move to a better spot"),
    GRIP_CASE("How you hold it, and your case"),
    RESELECT("Get onto a better cell"),
    FIVE_G("When 5G is worse than LTE"),
    BATTERY_SAVER("Battery saver"),
    WIFI_CALLING("Wi-Fi calling"),
    UPDATES("Software and carrier updates"),
    HARDWARE("Boosters and other hardware"),
    CONGESTION("When it's the network, not your phone"),
    MYTHS("Things that don't work"),
    ROOT_ONLY("Only possible with root or service menus"),
}

enum class AdviceLevel { ACTION, TIP, INFO }

sealed interface AdviceAction {
    data class OpenSettings(val target: SettingsTarget) : AdviceAction
    data object FindBestSpot : AdviceAction
    data object RunSpeedTest : AdviceAction
}

data class Advice(
    val id: String,
    val level: AdviceLevel,
    val title: String,
    val body: String,
    val action: AdviceAction? = null,
    val topic: GuideTopic? = null,
)

/** A neighbor that has been clearly stronger than the serving cell for a while. */
data class NeighborCandidate(val cell: CellObservation, val marginDb: Int, val durationMs: Long)

/**
 * Tracks how long each neighbor has been clearly stronger than the serving cell. A single
 * stronger reading is normal (fading); a sustained gap suggests the phone is stuck on a worse cell.
 */
class NeighborWatch(private val marginDb: Int = 6) {
    private val strongerSince = mutableMapOf<String, Long>()

    fun update(serving: CellObservation?, neighbors: List<CellObservation>, nowMs: Long): NeighborCandidate? {
        val servingLevel = serving?.let { level(it) }
        if (serving == null || servingLevel == null) {
            strongerSince.clear(); return null
        }
        val stronger = neighbors.filter { n ->
            n.identity.radio == serving.identity.radio && level(n)?.let { it - servingLevel >= marginDb } == true
        }
        val keys = stronger.map { it.key }.toSet()
        strongerSince.keys.retainAll(keys)
        stronger.forEach { strongerSince.getOrPut(it.key) { nowMs } }
        return stronger.map { NeighborCandidate(it, level(it)!! - servingLevel, nowMs - strongerSince.getValue(it.key)) }
            .maxByOrNull { it.marginDb }
    }

    private fun level(c: CellObservation): Int? = c.signal.rsrp ?: c.signal.rscp ?: c.signal.dbm
}

object SinrSanity {
    /**
     * Some modems (notably Samsung) report an LTE SINR of exactly 0 dB as a placeholder when the
     * real value is unavailable. A genuine SINR jitters by tenths of a dB, so a run of exact zeros
     * while RSRP is moving is treated as "not reported".
     */
    fun isPlaceholder(samples: List<Pair<Double?, Int?>>, window: Int = 10): Boolean {
        val recent = samples.takeLast(window)
        if (recent.size < window || recent.any { it.first != 0.0 }) return false
        val rsrps = recent.mapNotNull { it.second }
        return rsrps.size >= window / 2 && (rsrps.max() - rsrps.min()) >= 2
    }
}

data class DiagnosticInput(
    val serving: CellObservation?,
    /** The 5G NR leg in NSA mode, or the serving cell itself in SA mode. */
    val nrLeg: CellObservation?,
    val network: NetworkStatus?,
    val conditions: DeviceConditions,
    val neighbor: NeighborCandidate?,
    val recentSpeeds: List<SpeedResult>,
    val nowMs: Long,
)

/** Rule-based checks over data the app already has. Each rule is deliberately conservative. */
object Diagnostics {
    const val NEIGHBOR_MIN_DURATION_MS = 30_000L

    fun evaluate(i: DiagnosticInput): List<Advice> {
        val out = mutableListOf<Advice>()
        val s = i.serving
        val radio = s?.identity?.radio
        val rsrp = s?.signal?.rsrp ?: s?.signal?.rscp ?: s?.signal?.dbm
        val sinr = s?.signal?.sinr

        // ---- Signal strength --------------------------------------------------------------
        if (rsrp != null && (radio == RadioType.LTE || radio == RadioType.NR)) {
            when {
                rsrp < -110 -> out += Advice(
                    "weak", AdviceLevel.ACTION, "Weak signal ($rsrp dBm)",
                    "Indoors, walls and coated windows can cost 10–30 dB. Moving to a window, a higher floor, or the side of the building facing the tower usually helps more than anything else.",
                    AdviceAction.FindBestSpot, GuideTopic.LOCATION,
                )
                rsrp < -100 -> out += Advice(
                    "fair", AdviceLevel.TIP, "Signal is usable but not strong ($rsrp dBm)",
                    "A few metres can make a 5–10 dB difference. If speeds matter, try a spot nearer a window.",
                    AdviceAction.FindBestSpot, GuideTopic.LOCATION,
                )
            }
        }
        if (radio == RadioType.GSM || radio == RadioType.WCDMA || radio == RadioType.CDMA) {
            out += Advice(
                "legacy", AdviceLevel.ACTION, "You're on ${radio.generation}",
                "Your phone fell back to an older network, which usually means 4G is weak here. Data will be slow. Moving to a better spot often brings 4G back.",
                AdviceAction.FindBestSpot, GuideTopic.LOCATION,
            )
        }

        // ---- Strong but noisy ------------------------------------------------------------
        if (rsrp != null && rsrp >= -100 && sinr != null && sinr < 0) {
            out += Advice(
                "interference", AdviceLevel.TIP, "Strong signal, but a noisy one (SINR ${sinr.fmt(1)} dB)",
                "You can hear the tower well, but other cells are nearly as loud. This often happens where two towers overlap. A different room or window can change it, and so can the time of day.",
                AdviceAction.FindBestSpot, GuideTopic.READING_SIGNAL,
            )
        }

        // ---- Stuck on a worse cell -------------------------------------------------------
        i.neighbor?.takeIf { it.durationMs >= NEIGHBOR_MIN_DURATION_MS }?.let { n ->
            out += Advice(
                "reselect", AdviceLevel.ACTION, "A nearby cell is ${n.marginDb} dB stronger",
                "Your phone has stayed on a weaker cell for ${n.durationMs / 1000} s. Turning airplane mode on for about 10 seconds and then off makes the phone choose a cell again. The network may move you back on purpose (to spread load), so it doesn't always stick.",
                AdviceAction.OpenSettings(SettingsTarget.AIRPLANE_MODE), GuideTopic.RESELECT,
            )
        }

        // ---- Weak 5G leg -----------------------------------------------------------------
        i.nrLeg?.let { nr ->
            val nrRsrp = nr.signal.rsrp
            val nrSinr = nr.signal.sinr
            if ((nrRsrp != null && nrRsrp < -112) || (nrSinr != null && nrSinr < -3)) {
                out += Advice(
                    "weak5g", AdviceLevel.TIP, "5G is weak here" + (nrRsrp?.let { " ($it dBm)" } ?: ""),
                    "A weak 5G connection can be slower than plain LTE and uses more battery. If speeds are poor, try setting the network mode to LTE, and switch it back when you move somewhere else.",
                    AdviceAction.OpenSettings(SettingsTarget.NETWORK_MODE), GuideTopic.FIVE_G,
                )
            }
        }

        // ---- Device settings -------------------------------------------------------------
        if (i.conditions.powerSaveMode == true) {
            out += Advice(
                "powersave", AdviceLevel.TIP, "Battery saver is on",
                "Power saving can turn off 5G and limit background data (on Samsung it's an option inside Power saving). Turn it off while you need full speed.",
                AdviceAction.OpenSettings(SettingsTarget.BATTERY_SAVER), GuideTopic.BATTERY_SAVER,
            )
        }
        if (i.conditions.vpnActive == true || i.network?.transport == Transport.VPN) {
            out += Advice(
                "vpn", AdviceLevel.INFO, "A VPN is active",
                "Speed tests measure the path through the VPN server, which is often slower and adds delay. Pause the VPN to test the cell itself.",
                AdviceAction.OpenSettings(SettingsTarget.VPN), GuideTopic.CONGESTION,
            )
        }
        if (rsrp != null && (rsrp < -115 || radio == RadioType.GSM || radio == RadioType.WCDMA) && i.network?.transport != Transport.WIFI) {
            out += Advice(
                "wificalling", AdviceLevel.TIP, "Calls may drop here",
                "If you have Wi-Fi at this location, turn on Wi-Fi calling. Calls then go over Wi-Fi and don't depend on the cell signal.",
                AdviceAction.OpenSettings(SettingsTarget.WIFI_CALLING), GuideTopic.WIFI_CALLING,
            )
        }
        patchAgeDays(i.conditions.securityPatch, i.nowMs)?.takeIf { it > 180 }?.let { days ->
            out += Advice(
                "updates", AdviceLevel.INFO, "Software is ${days / 30} months old",
                "Your last security update was ${i.conditions.securityPatch}. Modem firmware and carrier settings fixes arrive with system updates.",
                AdviceAction.OpenSettings(SettingsTarget.SYSTEM_UPDATE), GuideTopic.UPDATES,
            )
        }

        // ---- Congestion (from speed tests) -----------------------------------------------
        congestion(i)?.let { out += it }

        return out.sortedBy { it.level.ordinal }
    }

    private fun congestion(i: DiagnosticInput): Advice? {
        val recent = i.recentSpeeds.filter {
            it.transport == Transport.CELLULAR && it.downMbps != null && i.nowMs - it.timestampMs < 60 * 60_000L
        }
        val last = recent.firstOrNull() ?: return null
        val down = last.downMbps!!
        val predicted = last.predictedDownMbps
        val goodSignal = (last.rsrp ?: -200) >= -105
        if (goodSignal && predicted != null && predicted > 0 && down < predicted * 0.3 && down < 10) {
            return Advice(
                "congestion", AdviceLevel.INFO, "The tower looks busy, not your phone",
                "Your signal supports about ${Fmt.speed(predicted)}, but the test measured ${Fmt.speed(down)}. That gap usually means the cell is shared with many users. Speeds often recover off-peak.",
                AdviceAction.RunSpeedTest, GuideTopic.CONGESTION,
            )
        }
        val speeds = recent.mapNotNull { it.downMbps }.filter { it > 0 }
        if (speeds.size >= 2 && speeds.max() / speeds.min() >= 3) {
            return Advice(
                "variance", AdviceLevel.INFO, "Speeds vary a lot on this cell",
                "Your last tests ranged from ${Fmt.speed(speeds.min())} to ${Fmt.speed(speeds.max())}. With the signal steady, that's how busy the cell is from minute to minute, not something your phone can fix.",
                null, GuideTopic.CONGESTION,
            )
        }
        return null
    }

    /** Days between an ISO date ("YYYY-MM-DD") and [nowMs], or null if unparseable. */
    fun patchAgeDays(date: String?, nowMs: Long): Long? {
        val parts = date?.split("-")?.mapNotNull { it.toIntOrNull() } ?: return null
        if (parts.size != 3) return null
        val days = daysFromCivil(parts[0], parts[1], parts[2])
        return nowMs / 86_400_000L - days
    }

    /** Howard Hinnant's days-from-civil algorithm (days since 1970-01-01). */
    private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = if (m <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }
}
