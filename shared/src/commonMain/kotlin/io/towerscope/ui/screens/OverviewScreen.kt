package io.towerscope.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.towerscope.AppController
import io.towerscope.calc.Analyzer
import io.towerscope.calc.DistanceMethod
import io.towerscope.calc.Fmt
import io.towerscope.calc.Geo
import io.towerscope.calc.SignalQuality
import io.towerscope.calc.fmt
import io.towerscope.data.UnitSystem
import io.towerscope.model.Transport
import io.towerscope.ui.Navigator
import io.towerscope.ui.Route
import io.towerscope.ui.ScreenColumn
import io.towerscope.ui.Tab
import io.towerscope.ui.components.ArcGauge
import io.towerscope.ui.components.CompassArrow
import io.towerscope.ui.components.DrillRow
import io.towerscope.ui.components.GlassCard
import io.towerscope.ui.components.GradePill
import io.towerscope.ui.components.InfoNote
import io.towerscope.ui.components.MetricTile
import io.towerscope.ui.components.Pill
import io.towerscope.ui.components.RadarBlip
import io.towerscope.ui.components.RadarView
import io.towerscope.ui.components.RadioBadge
import io.towerscope.ui.components.ScanningLabel
import io.towerscope.ui.components.SignalBars
import io.towerscope.ui.components.TileRow
import io.towerscope.ui.theme.Palette
import io.towerscope.ui.theme.TextStyles

private val radarRangesMetric = listOf(250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0, 32000.0, 64000.0)

// Imperial ranges chosen so the four rings land on round values (200 ft steps, then ¼-mile and whole-mile steps).
private val radarRangesImperial = listOf(800 * 0.3048) + listOf(0.5, 1.0, 2.0, 4.0, 8.0, 20.0, 40.0).map { it * Fmt.METERS_PER_MILE }

@Composable
fun OverviewScreen(controller: AppController, nav: Navigator) {
    val perms by controller.permissions.collectAsState()
    val snapshot by controller.snapshot.collectAsState()
    val fix by controller.fix.collectAsState()
    val heading by controller.heading.collectAsState()
    val settings by controller.settings.collectAsState()
    val nearby by controller.nearby.collectAsState()
    val radioError by controller.radioError.collectAsState()
    val live = rememberLiveAnalysis(controller)
    val serving = live.serving
    val units = settings.units

    ScreenColumn {
        if (!perms.coreGranted) {
            PermissionCard(controller)
            return@ScreenColumn
        }

        // ---- Status strip -------------------------------------------------------------
        val net = snapshot?.network
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(net?.operatorName?.ifBlank { null } ?: "Searching…", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                Text(
                    listOfNotNull(
                        net?.operatorMccMnc?.let { "PLMN $it" },
                        if (net?.isRoaming == true) "Roaming" else null,
                        fix?.let { "GPS ±${Fmt.distance(it.accuracyM ?: 0.0, units)}" } ?: "Waiting for GPS",
                    ).joinToString(" · "),
                    fontSize = 12.sp, color = Palette.TextDim,
                )
            }
            net?.networkTypeLabel?.let { Pill(it, if (net.isNrNsa || it.startsWith("5G")) Palette.Violet else Palette.Cyan, filled = true) }
            net?.transport?.takeIf { it != Transport.UNKNOWN }?.let { Pill(it.label, if (it == Transport.WIFI) Palette.Amber else Palette.TextDim) }
        }

        // ---- Radar --------------------------------------------------------------------
        val nearest = fix?.let { f -> Analyzer.nearestKnown(f, nearby.towers) }
        val best = serving?.bestDistance
        val heroDistance = best?.meters ?: nearest?.second
        val radarRanges = if (units == UnitSystem.METRIC) radarRangesMetric else radarRangesImperial
        val range = radarRanges.firstOrNull { it >= (heroDistance ?: 0.0) * 1.25 } ?: radarRanges.last()
        val blips = buildList {
            if (fix != null) {
                nearby.towers.forEach { t ->
                    val d = Geo.distanceM(fix!!.lat, fix!!.lon, t.location.lat, t.location.lon)
                    if (d <= range) add(RadarBlip(Geo.bearingDeg(fix!!.lat, fix!!.lon, t.location.lat, t.location.lon), d, Palette.TextFaint))
                }
                live.neighbors.forEach { n ->
                    if (n.bearingDeg != null) add(RadarBlip(n.bearingDeg, n.bestDistance?.meters, Palette.Blue))
                }
            }
            if (serving != null && best != null) {
                add(RadarBlip(serving.bearingDeg, best.meters, Palette.radio(serving.obs.identity.radio), primary = true, ringOnly = serving.bearingDeg == null))
            } else if (serving == null && nearest != null) {
                add(RadarBlip(Geo.bearingDeg(fix!!.lat, fix!!.lon, nearest.first.location.lat, nearest.first.location.lon), nearest.second, Palette.Cyan, primary = true))
            }
        }
        GlassCard(Modifier.fillMaxWidth(), onClick = serving?.let { s -> { nav.push(Route.Cell(s.obs.key)) } }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (serving != null) "SERVING TOWER" else "NEAREST KNOWN TOWER", style = TextStyles.overline)
                    val (v, u) = Fmt.distanceParts(heroDistance, units)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(v, style = TextStyles.metricLarge)
                        Spacer(Modifier.width(4.dp))
                        Text(u, style = TextStyles.unit, modifier = Modifier.padding(bottom = 8.dp))
                        if (heroDistance != null) {
                            Spacer(Modifier.width(10.dp))
                            Text("≈ " + Fmt.distanceAlt(heroDistance, units), fontSize = 13.sp, color = Palette.TextFaint, modifier = Modifier.padding(bottom = 10.dp))
                        }
                    }
                    Text(
                        when {
                            best != null -> best.method.label + (serving?.bearingDeg?.let { " · ${it.fmt(0)}° ${Geo.cardinal(it)}" } ?: "")
                            nearest != null -> "OpenCelliD · ${nearest.first.identity.radio.label}"
                            snapshot == null -> ""
                            else -> "Distance unavailable"
                        },
                        fontSize = 12.sp, color = Palette.TextDim,
                    )
                }
                if (serving?.bearingDeg != null) {
                    CompassArrow(serving.bearingDeg, heading, Modifier.size(64.dp), Palette.radio(serving.obs.identity.radio))
                }
            }
            Box(contentAlignment = Alignment.Center) {
                RadarView(blips, heading, scanning = snapshot == null || serving == null, rangeM = range, units = units, modifier = Modifier.padding(top = 8.dp))
                if (snapshot == null) ScanningLabel("ACQUIRING SIGNAL…", Modifier.align(Alignment.BottomCenter))
            }
            if (serving != null && serving.bearingDeg == null && best != null && best.method != DistanceMethod.GPS_DATABASE) {
                Text(
                    if (settings.hasApiKey) "Dashed ring: distance known, direction unknown (tower not in database yet)."
                    else "Dashed ring: distance known, direction unknown. Add a free OpenCelliD key in Settings to pinpoint the tower.",
                    fontSize = 11.sp, color = Palette.TextFaint,
                )
            }
        }

        radioError?.let { InfoNote("Radio error: $it", color = Palette.Red, icon = Icons.Rounded.Warning) }
        snapshot?.capabilities?.limitationNote?.let { InfoNote(it, icon = Icons.Rounded.Info) }
        if (net?.transport == Transport.WIFI) {
            InfoNote("You're on Wi-Fi. Signal data is from the cell tower, but internet traffic (and speed tests) use Wi-Fi.", color = Palette.Amber, icon = Icons.Rounded.Info)
        }

        // ---- Signal gauge + quick facts -----------------------------------------------
        if (serving != null) {
            val radio = serving.obs.identity.radio
            val metric = SignalQuality.primaryMetric(radio)
            val pv = SignalQuality.primaryValue(radio, serving.obs.signal)
            val g = metric.grade(pv)
            GlassCard(Modifier.fillMaxWidth(), onClick = { nav.push(Route.Metric(metric)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioBadge(radio, radioLabel(serving.obs))
                    Spacer(Modifier.width(8.dp))
                    serving.band?.let { Pill(it.name, Palette.TextDim) }
                    Spacer(Modifier.weight(1f))
                    SignalBars(serving.obs.signal.level, Palette.grade(g))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArcGauge(
                        fraction = pv?.let { metric.fraction(it) },
                        valueText = pv?.fmt(0) ?: "—",
                        unitText = metric.unit,
                        label = metric.shortTitle,
                        caption = g.label,
                        captionColor = Palette.grade(g),
                        modifier = Modifier.weight(1.25f),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickStat("Link score", serving.score?.let { "$it" } ?: "—", "/100", serving.grade.let { Palette.grade(it) })
                        serving.throughput?.sinrDlDb?.let {
                            QuickStat("SINR", it.fmt(1), "dB", Palette.grade(io.towerscope.calc.MetricKind.SINR.grade(it)))
                        }
                        serving.obs.signal.rsrq?.let {
                            QuickStat("RSRQ", "$it", "dB", Palette.grade(io.towerscope.calc.MetricKind.RSRQ.grade(it.toDouble())))
                        }
                        serving.pathLossDb?.let { QuickStat("Path loss", it.fmt(0), "dB", Palette.TextDim) }
                    }
                }
            }

            // ---- Derived tiles -----------------------------------------------------------
            val combined = Analyzer.combinedThroughput(serving, live.secondary.firstOrNull { it.obs.identity.radio == io.towerscope.model.RadioType.NR })
            if (serving != null) DiagnoseCard(controller, nav)

            TileRow {
                MetricTile(
                    "Distance", Fmt.distanceParts(best?.meters, units).first, Fmt.distanceParts(best?.meters, units).second,
                    Modifier.weight(1f), Icons.Rounded.Straighten, Palette.Cyan,
                    caption = best?.let { it.method.label + " · " + it.confidence.label.lowercase() },
                    onClick = { nav.push(Route.Cell(serving.obs.key)) },
                )
                MetricTile(
                    "Bearing", serving.bearingDeg?.fmt(0) ?: "—", if (serving.bearingDeg != null) "° ${Geo.cardinal(serving.bearingDeg)}" else "",
                    Modifier.weight(1f), Icons.Rounded.Explore, Palette.Violet,
                    caption = if (serving.bearingDeg != null) (if (heading != null) "Arrow follows compass" else "From true north") else "Needs tower position",
                    onClick = { nav.push(Route.Cell(serving.obs.key)) },
                )
            }
            TileRow {
                val (dv, du) = Fmt.speedParts(combined?.first)
                val (uv, uu) = Fmt.speedParts(combined?.second)
                MetricTile(
                    "Est. download", dv, du, Modifier.weight(1f), Icons.Rounded.Download, Palette.Green,
                    caption = net?.linkDownKbps?.let { "Android est. ${Fmt.speed(it / 1000.0)}" } ?: "Radio-link capacity",
                    onClick = { nav.select(Tab.SPEED) },
                )
                MetricTile(
                    "Est. upload", uv, uu, Modifier.weight(1f), Icons.Rounded.Upload, Palette.Pink,
                    caption = net?.linkUpKbps?.let { "Android est. ${Fmt.speed(it / 1000.0)}" } ?: "Link-budget model",
                    onClick = { nav.select(Tab.SPEED) },
                )
            }

            // ---- Drill-down list ----------------------------------------------------------
            GlassCard(Modifier.fillMaxWidth()) {
                Text("EXPLORE", style = TextStyles.overline)
                Spacer(Modifier.height(4.dp))
                DrillRow(cellTitle(serving.obs), cellSubtitle(serving), trailing = { GradePill(serving.grade) }) {
                    nav.push(Route.Cell(serving.obs.key))
                }
                live.secondary.forEach { sec ->
                    DrillRow(cellTitle(sec.obs), cellSubtitle(sec), trailing = { GradePill(sec.grade) }) { nav.push(Route.Cell(sec.obs.key)) }
                }
                DrillRow("Find best spot", "Walk around and compare signal at each place") { nav.push(Route.SpotFinder) }
                DrillRow("Better signal guide", "What really helps, and what doesn't") { nav.push(Route.Guide(null)) }
                DrillRow("Signal analysis", "Live charts, every metric explained") { nav.select(Tab.SIGNAL) }
                DrillRow("All towers", "${live.neighbors.size} neighbor cells · ${nearby.towers.size} in database nearby") { nav.select(Tab.TOWERS) }
                DrillRow("Device & network", controller.platform.device.let { "${it.manufacturer} ${it.model}" }, leading = null) { nav.push(Route.Device) }
            }
        } else if (snapshot != null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("No serving cell", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (snapshot?.capabilities?.canReadCells == false)
                        "This platform doesn't let apps read cell identity or signal. TowerScope shows the nearest towers from the open database instead."
                    else "No SIM, airplane mode, or no coverage. Insert a SIM or move to an area with service.",
                    fontSize = 13.sp, color = Palette.TextDim,
                )
                Spacer(Modifier.height(6.dp))
                DrillRow("Nearby towers", "${nearby.towers.size} in database") { nav.select(Tab.TOWERS) }
                DrillRow("Device & network", null) { nav.push(Route.Device) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Rounded.PhoneAndroid, null, tint = Palette.TextFaint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                snapshot?.let { "Updated ${Fmt.ago(controller.platform.nowMs() - it.timestampMs)} · every ${settings.refreshSeconds}s" } ?: "Starting radio scanner…",
                fontSize = 11.sp, color = Palette.TextFaint,
            )
        }
    }
}

@Composable
private fun QuickStat(label: String, value: String, unit: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label.uppercase(), style = TextStyles.overline.copy(fontSize = 10.sp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = TextStyles.metric.copy(fontSize = 19.sp, color = color))
            Spacer(Modifier.width(3.dp))
            Text(unit, style = TextStyles.unit, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}
