package io.towerscope.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.towerscope.AppController
import io.towerscope.calc.CellAnalysis
import io.towerscope.calc.Cqi
import io.towerscope.calc.DistanceMethod
import io.towerscope.calc.Fmt
import io.towerscope.calc.Grade
import io.towerscope.calc.MetricKind
import io.towerscope.calc.RfModels
import io.towerscope.calc.SignalQuality
import io.towerscope.calc.Throughput
import io.towerscope.calc.fmt
import io.towerscope.model.RadioType
import io.towerscope.model.SignalSample
import io.towerscope.ui.Navigator
import io.towerscope.ui.Route
import io.towerscope.ui.ScreenColumn
import io.towerscope.ui.components.BarDatum
import io.towerscope.ui.components.ChartSeries
import io.towerscope.ui.components.Divider
import io.towerscope.ui.components.GlassCard
import io.towerscope.ui.components.GradePill
import io.towerscope.ui.components.HorizontalBars
import io.towerscope.ui.components.InfoNote
import io.towerscope.ui.components.KeyValueRow
import io.towerscope.ui.components.LineChart
import io.towerscope.ui.components.MetricTile
import io.towerscope.ui.components.RadioBadge
import io.towerscope.ui.components.ScoreRing
import io.towerscope.ui.components.SectionHeader
import io.towerscope.ui.components.ThresholdScale
import io.towerscope.ui.components.bandsFor
import io.towerscope.ui.theme.Palette
import io.towerscope.ui.theme.TextStyles

@Composable
fun SignalScreen(controller: AppController, nav: Navigator) {
    val perms by controller.permissions.collectAsState()
    val history by controller.history.collectAsState()
    val settings by controller.settings.collectAsState()
    val live = rememberLiveAnalysis(controller)
    val s = live.serving

    ScreenColumn {
        if (!perms.coreGranted) { PermissionCard(controller); return@ScreenColumn }
        if (s == null) {
            val note = controller.snapshot.value?.capabilities?.limitationNote
            InfoNote(note ?: "Waiting for a serving cell. Signal analysis appears as soon as the modem reports measurements.", icon = Icons.Rounded.Info)
            return@ScreenColumn
        }
        val radio = s.obs.identity.radio

        // ---- Score hero ----------------------------------------------------------------
        GlassCard(Modifier.fillMaxWidth(), accent = Palette.grade(s.grade)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(s.score, Palette.grade(s.grade), Modifier.size(112.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("LINK QUALITY", style = TextStyles.overline)
                    Text(s.grade.label, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Palette.grade(s.grade))
                    Spacer(Modifier.height(4.dp))
                    Text(verdict(s), fontSize = 13.sp, color = Palette.TextDim, lineHeight = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RadioBadge(radio, radioLabel(s.obs))
                        s.band?.let { io.towerscope.ui.components.Pill(it.name, Palette.TextDim) }
                    }
                }
            }
        }

        // ---- Live chart ----------------------------------------------------------------
        val chartable = SignalQuality.metricsFor(radio).filter { it != MetricKind.CQI && it != MetricKind.ECNO }
        var chartMetric by remember(radio) { mutableStateOf(chartable.first()) }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Live history", subtitle = "Serving cell · last ${history.size} samples")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chartable.forEach { m ->
                    FilterChip(
                        selected = chartMetric == m,
                        onClick = { chartMetric = m },
                        label = { Text(m.shortTitle, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Cyan.copy(alpha = 0.18f), selectedLabelColor = Palette.Cyan),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            MetricChart(chartMetric, history.takeLast(150))
        }

        // ---- Metric tiles --------------------------------------------------------------
        SectionHeader("Measurements", subtitle = "Tap any metric to drill in")
        val metrics = SignalQuality.metricsFor(radio)
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { m ->
                    val v = if (m == MetricKind.SINR) s.throughput?.sinrDlDb else m.valueOf(s.obs.signal)
                    val g = m.grade(v)
                    MetricTile(
                        m.shortTitle,
                        v?.let { if (m == MetricKind.SINR) it.fmt(1) else it.fmt(0) } ?: "—",
                        if (v != null) m.unit else "",
                        Modifier.weight(1f),
                        accent = Palette.grade(g),
                        caption = when {
                            v == null && m == MetricKind.TIMING_ADVANCE -> "Reported only during data transfer"
                            v == null && m == MetricKind.CQI -> "Reported only during data transfer"
                            v == null -> "Not reported by modem"
                            m == MetricKind.SINR && s.obs.signal.sinr == null -> "Estimated · ${g.label}"
                            m == MetricKind.TIMING_ADVANCE -> s.distances.firstOrNull { it.method == DistanceMethod.TIMING_ADVANCE }?.let { "≈ " + Fmt.distance(it.meters, settings.units) }
                            else -> g.label
                        },
                        onClick = { nav.push(Route.Metric(m)) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // ---- Neighbor comparison -----------------------------------------------------------
        if (live.neighbors.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Serving vs neighbors", subtitle = "Signal strength of every cell your phone hears")
                val rows = (listOf(s) + live.neighbors).take(10).mapNotNull { a ->
                    val v = (a.obs.signal.rsrp ?: a.obs.signal.rscp ?: a.obs.signal.dbm)?.toDouble() ?: return@mapNotNull null
                    BarDatum(
                        label = if (a === s) "Serving" else (a.obs.identity.pci?.let { "PCI $it" } ?: a.obs.identity.radio.label) + (a.band?.let { " ${it.name}" } ?: ""),
                        value = v, display = "${v.fmt(0)} dBm",
                        color = Palette.grade(MetricKind.RSRP.grade(v)), highlight = a === s,
                    )
                }
                HorizontalBars(rows, -140.0, -44.0)
                val strongest = live.neighbors.firstOrNull()
                val sv = s.obs.signal.rsrp ?: s.obs.signal.dbm
                val nv = strongest?.obs?.signal?.rsrp ?: strongest?.obs?.signal?.dbm
                if (sv != null && nv != null) {
                    Spacer(Modifier.height(10.dp))
                    val margin = sv - nv
                    Text(
                        if (margin >= 0) "Serving cell leads the strongest neighbor by $margin dB."
                        else "A neighbor is ${-margin} dB stronger — a handover is likely if this persists.",
                        fontSize = 12.sp, color = if (margin >= 3) Palette.TextDim else Palette.Amber,
                    )
                }
            }
        }
    }
}

private fun verdict(a: CellAnalysis): String {
    val s = a.obs.signal
    val rsrp = s.rsrp ?: s.dbm
    val sinr = a.throughput?.sinrDlDb
    val strong = rsrp != null && rsrp >= -95
    val clean = sinr != null && sinr >= 10
    return when {
        rsrp == null -> "Limited measurements available for this cell."
        strong && clean -> "Strong signal on a clean channel — expect fast, stable data."
        strong && !clean -> "Strong signal but heavy interference or load. Speeds may vary."
        !strong && clean -> "Weak but clean signal. Works, but indoors may drop to slower modulation."
        else -> "Weak signal with interference. Move toward a window or the tower for better results."
    }
}

@Composable
fun MetricChart(kind: MetricKind, samples: List<SignalSample>) {
    val values = samples.map { kind.valueOf(it)?.toFloat() }
    val present = values.filterNotNull()
    if (present.size < 2) {
        Text(
            if (present.isEmpty()) "No ${kind.shortTitle} samples yet." else "Collecting samples…",
            fontSize = 13.sp, color = Palette.TextFaint, modifier = Modifier.padding(vertical = 40.dp),
        )
        return
    }
    val lo = present.min()
    val hi = present.max()
    val pad = ((hi - lo) * 0.25f).coerceAtLeast(4f)
    val yMin = (lo - pad).coerceAtLeast(kind.rangeMin.toFloat())
    val yMax = (hi + pad).coerceAtMost(kind.rangeMax.toFloat()).let { if (it <= yMin) yMin + 1 else it }
    val color = Palette.grade(kind.grade(present.last().toDouble())).takeIf { kind.thresholds.isNotEmpty() } ?: Palette.Cyan
    LineChart(listOf(ChartSeries(values, color, kind.shortTitle)), yMin, yMax, bands = bandsFor(kind))
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatCell("Min", lo.toDouble().fmt(1))
        StatCell("Avg", present.average().fmt(1))
        StatCell("Max", hi.toDouble().fmt(1))
        val sd = kotlin.math.sqrt(present.map { (it - present.average()).let { d -> d * d } }.average())
        StatCell("Std dev", sd.fmt(1))
    }
    if (kind.unit.isNotBlank()) {
        Text("All values in ${kind.unit}", fontSize = 11.sp, color = Palette.TextFaint, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = TextStyles.overline.copy(fontSize = 10.sp), maxLines = 1, softWrap = false)
        Text(value, style = TextStyles.mono.copy(fontSize = 15.sp), maxLines = 1, softWrap = false)
    }
}

// =============================================================================================
// Metric drill-down
// =============================================================================================

@Composable
fun MetricDetailScreen(controller: AppController, kind: MetricKind) {
    val history by controller.history.collectAsState()
    val settings by controller.settings.collectAsState()
    val live = rememberLiveAnalysis(controller)
    val s = live.serving

    ScreenColumn {
        val value = s?.let { if (kind == MetricKind.SINR) it.throughput?.sinrDlDb else kind.valueOf(it.obs.signal) }
        val grade = kind.grade(value)
        GlassCard(Modifier.fillMaxWidth(), accent = Palette.grade(grade).takeIf { grade != Grade.UNKNOWN }) {
            Text(kind.title.uppercase(), style = TextStyles.overline)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value?.let { if (kind == MetricKind.SINR) it.fmt(1) else it.fmt(0) } ?: "—", style = TextStyles.metricLarge.copy(fontSize = 56.sp))
                Spacer(Modifier.width(6.dp))
                Text(kind.unit, style = TextStyles.unit, modifier = Modifier.padding(bottom = 12.dp))
                Spacer(Modifier.weight(1f))
                if (grade != Grade.UNKNOWN) GradePill(grade, Modifier.padding(bottom = 14.dp))
            }
            if (kind == MetricKind.SINR && s?.obs?.signal?.sinr == null && value != null) {
                Text(s?.throughput?.sinrDlSource ?: "", fontSize = 12.sp, color = Palette.Amber)
            }
            if (kind.thresholds.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ThresholdScale(kind, value)
                Row {
                    Text("${kind.rangeMin.fmt(0)} ${kind.unit}", fontSize = 10.sp, color = Palette.TextFaint)
                    Spacer(Modifier.weight(1f))
                    Text("${kind.rangeMax.fmt(0)} ${kind.unit}", fontSize = 10.sp, color = Palette.TextFaint)
                }
            }
        }

        if (kind != MetricKind.ECNO) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("History", subtitle = "Serving cell, all samples this session")
                MetricChart(kind, history.takeLast(300))
            }
        }

        if (s != null) derivedSection(kind, s, settings.units)?.let { rows ->
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Derived from this measurement")
                rows.forEachIndexed { i, r ->
                    if (i > 0) Divider()
                    KeyValueRow(r.first, r.second, hint = r.third)
                }
            }
        }

        ExplainCard(Icons.Rounded.Info, "What it is", kind.what, Palette.Cyan)
        ExplainCard(Icons.Rounded.Tune, "What affects it", kind.affects, Palette.Violet)
        ExplainCard(Icons.Rounded.Lightbulb, "How to improve it", kind.improve, Palette.Green)

        if (kind.thresholds.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Reference ranges")
                var upper: Double? = null
                kind.thresholds.forEach { t ->
                    val range = when {
                        upper == null -> "≥ ${t.min.fmt(0)}"
                        t.min < kind.rangeMin -> "< ${upper!!.fmt(0)}"
                        else -> "${t.min.fmt(0)} to ${upper!!.fmt(0)}"
                    }
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        GradePill(t.grade, Modifier.width(96.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("$range ${kind.unit}", style = TextStyles.mono)
                    }
                    upper = t.min
                }
            }
        }
    }
}

@Composable
private fun ExplainCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, color: androidx.compose.ui.graphics.Color) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
        }
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 13.sp, color = Palette.TextDim, lineHeight = 19.sp)
    }
}

private fun derivedSection(kind: MetricKind, a: CellAnalysis, units: io.towerscope.data.UnitSystem): List<Triple<String, String, String?>>? {
    val s = a.obs.signal
    val out = mutableListOf<Triple<String, String, String?>>()
    when (kind) {
        MetricKind.RSRP, MetricKind.RSCP, MetricKind.RSSI, MetricKind.DBM -> {
            a.pathLossDb?.let { out += Triple("Path loss", "${it.fmt(1)} dB", "Assumed transmit reference ${a.transmitReferenceDbm.fmt(0)} dBm − measured") }
            a.noiseLimitedSnrDb?.let { out += Triple("Margin over thermal noise", "${it.fmt(1)} dB", "Noise floor ${a.noiseFloorPerReDbm.fmt(1)} dBm per resource element (NF 7 dB)") }
            a.distances.firstOrNull { it.method == DistanceMethod.PATH_LOSS }?.let {
                out += Triple("Distance (path-loss model)", Fmt.distance(it.meters, units), "Hata/COST-231 inverted for your environment")
            }
            a.distances.firstOrNull { it.method == DistanceMethod.FREE_SPACE }?.let {
                out += Triple("Distance upper bound", Fmt.distance(it.meters, units), "Free-space propagation")
            }
            if (a.obs.identity.radio == RadioType.LTE && s.rsrp != null && s.rssi != null) {
                val bwKnown = a.obs.identity.bandwidthKhz != null
                val nRb = (a.obs.identity.bandwidthKhz ?: 10_000) * 0.9 / 180.0
                val implied = 10 * kotlin.math.log10(nRb) + s.rsrp - s.rssi
                val basis = "N·RSRP/RSSI with N = ${nRb.fmt(0)} resource blocks" + if (bwKnown) "" else " (assumed 10 MHz)"
                // RSRQ cannot physically exceed about −3 dB (RS power can't exceed total power), so a
                // higher value means the modem's RSSI is measured differently (e.g. a narrower window).
                if (implied <= -3.0) {
                    out += Triple("Implied RSRQ", "${implied.fmt(1)} dB", basis)
                } else {
                    out += Triple("Implied RSRQ", "—", "Not computable: this modem's RSSI is inconsistent with its RSRP")
                }
            }
            s.level?.let { out += Triple("OS signal bars", "$it / 4", null) }
            s.asu?.let { out += Triple("ASU", "$it", "Arbitrary strength unit") }
        }
        MetricKind.SINR -> {
            val sinr = a.throughput?.sinrDlDb ?: return null
            out += Triple("Best modulation", Throughput.modulationFor(sinr), "Approximate link-adaptation threshold")
            out += Triple("Shannon limit", "${kotlin.math.log2(1 + RfModels.dbToLinear(sinr)).fmt(2)} bit/s/Hz", "Theoretical maximum per layer")
            a.throughput?.spectralEfficiencyDl?.let { out += Triple("Practical efficiency", "${it.fmt(2)} bit/s/Hz", "Attenuated Shannon, α = 0.6") }
            a.throughput?.let { out += Triple("Effective MIMO layers", "${it.effectiveLayers.fmt(1)} of ${it.dlLayers}", "Rank falls as SINR drops") }
            a.throughput?.let { out += Triple("Downlink capacity", Fmt.speed(it.dlMbps), it.bandwidthMHz?.let { b -> "${b.fmt(0)} MHz carrier" }) }
            out += Triple("Linear ratio", "${RfModels.dbToLinear(sinr).fmt(1)} : 1", null)
        }
        MetricKind.RSRQ -> {
            val rsrq = s.rsrq ?: return null
            out += Triple("Implied SINR", "${(2.0 * (rsrq + 17)).coerceIn(-10.0, 30.0).fmt(1)} dB", "Empirical RSRQ→SINR map for loaded LTE")
            out += Triple(
                "Load / interference",
                when {
                    rsrq >= -8 -> "Very light"
                    rsrq >= -11 -> "Light"
                    rsrq >= -14 -> "Moderate"
                    rsrq >= -17 -> "Heavy"
                    else -> "Severe"
                },
                "RSRQ falls as other traffic and neighbor cells raise RSSI",
            )
            s.rssi?.let { out += Triple("RSSI (wideband)", "$it dBm", null) }
        }
        MetricKind.TIMING_ADVANCE -> {
            val ta = a.distances.firstOrNull { it.method == DistanceMethod.TIMING_ADVANCE } ?: return listOf(
                Triple("Status", "Not reported", "Start a speed test to create an active connection, then come back"),
            )
            out += Triple("Distance", Fmt.distance(ta.meters, units), "± ${Fmt.distance(ta.uncertaintyM, units)} quantisation")
            out += Triple("Round-trip time", "${(ta.meters * 2 / RfModels.SPEED_OF_LIGHT * 1e6).fmt(2)} µs", "Radio propagation only")
            a.distances.firstOrNull { it.method == DistanceMethod.GPS_DATABASE }?.let { g ->
                val diff = ta.meters - g.meters
                out += Triple("vs. GPS distance", (if (diff >= 0) "+" else "−") + Fmt.distance(kotlin.math.abs(diff), units), "Positive = signal took an indirect path")
            }
        }
        MetricKind.CQI -> {
            val e = s.cqi?.let { Cqi.entry(it) } ?: return null
            out += Triple("Modulation", e.modulation, "3GPP TS 36.213 Table 7.2.3-1")
            out += Triple("Code rate", e.codeRate.fmt(3), null)
            out += Triple("Spectral efficiency", "${e.efficiency.fmt(3)} bit/s/Hz", "Per layer")
        }
        MetricKind.ECNO -> {
            s.ecNo?.let { out += Triple("Linear ratio", RfModels.dbToLinear(it).fmt(3), null) }
        }
    }
    return out.ifEmpty { null }
}
