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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.towerscope.AppController
import io.towerscope.calc.Analyzer
import io.towerscope.calc.CellAnalysis
import io.towerscope.calc.Fmt
import io.towerscope.calc.Geo
import io.towerscope.calc.MetricKind
import io.towerscope.calc.RfModels
import io.towerscope.calc.SettingsTarget
import io.towerscope.calc.SignalQuality
import io.towerscope.calc.fmt
import io.towerscope.data.UnitSystem
import io.towerscope.model.DatabaseTower
import io.towerscope.model.TowerLookup
import io.towerscope.ui.Navigator
import io.towerscope.ui.Route
import io.towerscope.ui.ScreenColumn
import io.towerscope.ui.components.CompassArrow
import io.towerscope.ui.components.Divider
import io.towerscope.ui.components.DistanceComparison
import io.towerscope.ui.components.DrillRow
import io.towerscope.ui.components.GlassCard
import io.towerscope.ui.components.GradePill
import io.towerscope.ui.components.InfoNote
import io.towerscope.ui.components.KeyValueRow
import io.towerscope.ui.components.MetricTile
import io.towerscope.ui.components.Pill
import io.towerscope.ui.components.RadioBadge
import io.towerscope.ui.components.SectionHeader
import io.towerscope.ui.components.SignalBars
import io.towerscope.ui.components.TileRow
import io.towerscope.ui.theme.Palette
import io.towerscope.ui.theme.TextStyles

@Composable
fun TowersScreen(controller: AppController, nav: Navigator) {
    val perms by controller.permissions.collectAsState()
    val nearby by controller.nearby.collectAsState()
    val fix by controller.fix.collectAsState()
    val settings by controller.settings.collectAsState()
    val live = rememberLiveAnalysis(controller)
    var tab by rememberSaveable { mutableIntStateOf(0) }

    ScreenColumn {
        if (!perms.coreGranted) { PermissionCard(controller); return@ScreenColumn }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Detected by phone", "Database nearby").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = tab == i, onClick = { tab = i },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = Palette.Cyan.copy(alpha = 0.16f), activeContentColor = Palette.Cyan),
                ) { Text(label, fontSize = 13.sp) }
            }
        }

        if (tab == 0) {
            val serving = listOfNotNull(live.serving) + live.secondary
            if (serving.isEmpty() && live.neighbors.isEmpty()) {
                InfoNote("No cells reported by the modem yet.", icon = Icons.Rounded.Info)
            }
            if (serving.isNotEmpty()) SectionHeader("Connected", subtitle = "Cells carrying your traffic")
            serving.forEach { a -> CellCard(a, settings.units, controller.lookups.value[a.obs.identity.key]) { nav.push(Route.Cell(a.obs.key)) } }
            if (live.neighbors.isNotEmpty()) {
                SectionHeader("Neighbors (${live.neighbors.size})", subtitle = "Heard by your phone; candidates for handover")
                GlassCard(Modifier.fillMaxWidth()) {
                    live.neighbors.forEachIndexed { i, a ->
                        if (i > 0) Divider()
                        NeighborRow(a, settings.units) { nav.push(Route.Cell(a.obs.key)) }
                    }
                }
            }
            if (serving.isNotEmpty()) SwitchTowerCard(controller)
        } else {
            when {
                nearby.needsKey || !settings.hasApiKey -> ApiKeyPrompt { nav.push(Route.Settings) }
                fix == null -> InfoNote("Waiting for a GPS fix to search the area.", icon = Icons.Rounded.Info)
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${nearby.towers.size} cells within ~${if (settings.units == UnitSystem.METRIC) "1 km" else "0.6 mi"}", fontSize = 15.sp, color = Palette.Text, fontWeight = FontWeight.Medium)
                            Text("OpenCelliD · sorted by distance", fontSize = 12.sp, color = Palette.TextFaint)
                        }
                        if (nearby.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = { controller.refreshNearby() }) { Icon(Icons.Rounded.Refresh, "Refresh", tint = Palette.TextDim) }
                    }
                    nearby.error?.let { InfoNote(it, color = Palette.Red) }
                    val servingKeys = (listOfNotNull(live.serving) + live.secondary).map { it.obs.identity.key }.toSet()
                    val seenKeys = live.all.map { it.obs.identity.key }.toSet()
                    val sorted = remember(nearby.towers, fix) {
                        nearby.towers.map { it to Geo.distanceM(fix!!.lat, fix!!.lon, it.location.lat, it.location.lon) }.sortedBy { it.second }
                    }
                    if (sorted.isEmpty() && !nearby.loading) InfoNote("The database has no towers recorded within this area.")
                    GlassCard(Modifier.fillMaxWidth()) {
                        sorted.forEachIndexed { i, (t, d) ->
                            if (i > 0) Divider()
                            val bearing = Geo.bearingDeg(fix!!.lat, fix!!.lon, t.location.lat, t.location.lon)
                            DrillRow(
                                title = "${t.identity.radio.label} · ${t.identity.mccMnc ?: "?"} · ${t.identity.area ?: "?"}/${t.identity.cellId ?: "?"}",
                                subtitle = "${Fmt.distance(d, settings.units)} · ${bearing.fmt(0)}° ${Geo.cardinal(bearing)}" +
                                    (t.location.samples?.let { " · $it samples" } ?: ""),
                                leading = { RadioBadge(t.identity.radio, t.identity.radio.generation) },
                                trailing = {
                                    when {
                                        t.identity.key in servingKeys -> Pill("SERVING", Palette.Green)
                                        t.identity.key in seenKeys -> Pill("HEARD", Palette.Blue)
                                        i == 0 -> Pill("NEAREST", Palette.Cyan)
                                    }
                                },
                            ) { nav.push(Route.DbTower(t)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Phones and carriers choose the tower; no app can pick one. These are the two settings a person can
 * change to make the phone choose again. Apps can't flip them, only open the page.
 */
@Composable
private fun SwitchTowerCard(controller: AppController) {
    SectionHeader("Switch towers", subtitle = "Your phone and carrier choose the tower. You can make it choose again.")
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            "Airplane mode: turn it on, wait about 10 seconds, then turn it off. The phone searches again and often " +
                "lands on a stronger cell.",
            fontSize = 13.sp, color = Palette.TextDim, lineHeight = 18.sp,
        )
        Spacer(Modifier.height(8.dp))
        SettingsButton("Open airplane mode") { controller.openSystemSettings(SettingsTarget.AIRPLANE_MODE) }
        Spacer(Modifier.height(14.dp))
        Text(
            "Network mode: if 5G is weak, choose LTE. That often moves you to a stronger tower. Set it back when you " +
                "move somewhere else.",
            fontSize = 13.sp, color = Palette.TextDim, lineHeight = 18.sp,
        )
        Spacer(Modifier.height(8.dp))
        SettingsButton("Open network mode") { controller.openSystemSettings(SettingsTarget.NETWORK_MODE) }
    }
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun ApiKeyPrompt(onOpenSettings: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), accent = Palette.Amber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Key, null, tint = Palette.Amber)
            Spacer(Modifier.width(10.dp))
            Text("Connect the tower database", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Phones report which cell they are using, but not where the tower is. TowerScope looks tower positions up in " +
                "OpenCelliD, the largest open cell-tower database. A key is free and takes a minute: sign up with your email, " +
                "tap the link OpenCelliD emails you, then copy your access token into the API key field in Settings.",
            fontSize = 13.sp, color = Palette.TextDim, lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = Palette.Amber, contentColor = Palette.Bg)) {
            Text("Add API key", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CellCard(a: CellAnalysis, units: UnitSystem, lookup: TowerLookup?, onClick: () -> Unit) {
    val radio = a.obs.identity.radio
    val pm = SignalQuality.primaryMetric(radio)
    val pv = SignalQuality.primaryValue(radio, a.obs.signal)
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, accent = Palette.radio(radio)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioBadge(radio, radioLabel(a.obs))
            Spacer(Modifier.width(8.dp))
            Pill(a.obs.role.label.uppercase(), Palette.Green)
            Spacer(Modifier.weight(1f))
            SignalBars(a.obs.signal.level, Palette.grade(pm.grade(pv)))
        }
        Spacer(Modifier.height(10.dp))
        Text(cellTitle(a.obs), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
        Text(cellSubtitle(a), fontSize = 12.sp, color = Palette.TextDim)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            MiniStat(pm.shortTitle, pv?.fmt(0) ?: "—", pm.unit)
            MiniStat("Distance", Fmt.distance(a.bestDistance?.meters, units), "")
            MiniStat("Bearing", a.bearingDeg?.let { "${it.fmt(0)}° ${Geo.cardinal(it)}" } ?: "—", "")
        }
        lookupStatus(lookup)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 11.sp, color = Palette.TextFaint)
        }
    }
}

private fun lookupStatus(l: TowerLookup?): String? = when (l) {
    TowerLookup.Loading -> "Looking up tower position…"
    TowerLookup.NotFound -> "Tower not in OpenCelliD yet — using radio-based distance estimates"
    TowerLookup.NoApiKey -> "Add an OpenCelliD key to locate this tower"
    is TowerLookup.Failed -> "Lookup failed: ${l.message}"
    TowerLookup.NotLookupable -> "Incomplete identity — tower can't be looked up"
    else -> null
}

@Composable
private fun MiniStat(label: String, value: String, unit: String) {
    Column {
        Text(label.uppercase(), style = TextStyles.overline.copy(fontSize = 10.sp))
        Text("$value $unit".trim(), style = TextStyles.mono.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun NeighborRow(a: CellAnalysis, units: UnitSystem, onClick: () -> Unit) {
    val radio = a.obs.identity.radio
    val pm = SignalQuality.primaryMetric(radio)
    val pv = SignalQuality.primaryValue(radio, a.obs.signal)
    DrillRow(
        title = if (a.obs.identity.isLookupable) cellTitle(a.obs) else listOfNotNull(a.obs.identity.pci?.let { "PCI $it" }, a.band?.name).joinToString(" · ").ifEmpty { radio.label },
        subtitle = listOfNotNull(
            a.band?.let { Fmt.mhz(it.dlMHz) },
            a.bestDistance?.let { Fmt.distance(it.meters, units) },
            pv?.let { "${it.fmt(0)} ${pm.unit}" },
        ).joinToString(" · "),
        leading = { RadioBadge(radio, radio.generation) },
        trailing = { SignalBars(a.obs.signal.level, Palette.grade(pm.grade(pv)), Modifier.size(24.dp, 16.dp).padding(end = 6.dp)) },
        onClick = onClick,
    )
}

// =============================================================================================
// Cell detail
// =============================================================================================

@Composable
fun CellDetailScreen(controller: AppController, nav: Navigator, key: String) {
    val live = rememberLiveAnalysis(controller)
    val heading by controller.heading.collectAsState()
    val settings by controller.settings.collectAsState()
    val fix by controller.fix.collectAsState()
    val lookups by controller.lookups.collectAsState()
    val a = live.byKey(key)
    val units = settings.units

    ScreenColumn {
        if (a == null) {
            InfoNote("This cell is no longer visible to the modem. It may have handed over or faded out.", icon = Icons.Rounded.Info)
            return@ScreenColumn
        }
        val id = a.obs.identity
        val radio = id.radio

        // ---- Header -------------------------------------------------------------------
        GlassCard(Modifier.fillMaxWidth(), accent = Palette.radio(radio)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioBadge(radio, radioLabel(a.obs))
                Spacer(Modifier.width(8.dp))
                Pill(a.obs.role.label.uppercase(), if (a.obs.isServing) Palette.Green else Palette.Blue)
                Spacer(Modifier.weight(1f))
                GradePill(a.grade)
            }
            Spacer(Modifier.height(12.dp))
            Text(cellTitle(a.obs), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
            Text(cellSubtitle(a), fontSize = 13.sp, color = Palette.TextDim)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DISTANCE", style = TextStyles.overline)
                    val (v, u) = Fmt.distanceParts(a.bestDistance?.meters, units)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(v, style = TextStyles.metricLarge)
                        Spacer(Modifier.width(4.dp))
                        Text(u, style = TextStyles.unit, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text(a.bestDistance?.method?.label ?: "No distance estimate", fontSize = 12.sp, color = Palette.TextDim)
                    a.bearingDeg?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("Bearing ${it.fmt(0)}° ${Geo.cardinal(it)}", style = TextStyles.mono.copy(color = Palette.Cyan))
                        if (heading != null) {
                            val rel = Geo.angleDelta(heading!!.toDouble(), it)
                            Text(
                                if (kotlin.math.abs(rel) < 10) "You're facing the tower" else "Turn ${kotlin.math.abs(rel).fmt(0)}° ${if (rel > 0) "right" else "left"}",
                                fontSize = 12.sp, color = Palette.TextDim,
                            )
                        }
                    }
                }
                CompassArrow(a.bearingDeg, heading, Modifier.size(120.dp), Palette.radio(radio))
            }
        }

        lookupStatus(lookups[id.key])?.let { InfoNote(it, icon = Icons.Rounded.Info) }
        if (lookups[id.key] is TowerLookup.Failed) {
            OutlinedButton(onClick = { controller.retryLookup(a.obs) }) { Text("Retry lookup") }
        }

        // ---- Distance methods -----------------------------------------------------------
        if (a.distances.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Distance — method comparison", subtitle = "Independent estimates; bars show ± uncertainty")
                DistanceComparison(a.distances, units)
                a.bestDistance?.let { best ->
                    Spacer(Modifier.height(12.dp))
                    KeyValueRow("Best estimate", Fmt.distance(best.meters, units), hint = best.method.label)
                    Divider(); KeyValueRow("In miles", Fmt.miles(best.meters), hint = "${(best.meters * 3.28084).fmt(0)} ft")
                    Divider(); KeyValueRow("In kilometres", Fmt.kilometres(best.meters), hint = "${best.meters.fmt(0)} m")
                }
                Spacer(Modifier.height(10.dp))
                a.bestDistance?.let { Text(it.method.explanation, fontSize = 12.sp, color = Palette.TextFaint, lineHeight = 17.sp) }
            }
        }

        // ---- Throughput ---------------------------------------------------------------
        a.throughput?.let { t ->
            TileRow {
                MetricTile("Est. download", Fmt.speedParts(t.dlMbps).first, Fmt.speedParts(t.dlMbps).second, Modifier.weight(1f), accent = Palette.Green,
                    caption = "Peak ${Fmt.speed(t.dlPeakMbps)}")
                MetricTile("Est. upload", Fmt.speedParts(t.ulMbps).first, Fmt.speedParts(t.ulMbps).second, Modifier.weight(1f), accent = Palette.Pink,
                    caption = "Peak ${Fmt.speed(t.ulPeakMbps)}")
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Throughput model", subtitle = "How the speed estimate is derived")
                t.bandwidthMHz?.let { KeyValueRow("Channel bandwidth", "${it.fmt(it.rem(1.0).let { r -> if (r == 0.0) 0 else 1 })} MHz", hint = t.bandwidthSource) }
                t.sinrDlDb?.let { Divider(); KeyValueRow("Downlink SINR", "${it.fmt(1)} dB", hint = t.sinrDlSource) }
                if (t.modulationDl != "—") { Divider(); KeyValueRow("Modulation", t.modulationDl, hint = "Expected link adaptation") }
                t.spectralEfficiencyDl?.let { Divider(); KeyValueRow("Spectral efficiency", "${it.fmt(2)} b/s/Hz", hint = "Per MIMO layer") }
                if (t.dlLayers > 1) { Divider(); KeyValueRow("MIMO layers", "${t.effectiveLayers.fmt(1)} / ${t.dlLayers}", hint = "Effective rank at this SINR") }
                t.sinrUlDb?.let { Divider(); KeyValueRow("Uplink SINR", "${it.fmt(1)} dB", hint = "Link budget, ${settings.handsetTxPowerDbm.fmt(0)} dBm handset") }
                t.ulAllocationMHz?.let { Divider(); KeyValueRow("Optimal UL allocation", "${it.fmt(1)} MHz", hint = "Narrower allocation concentrates power at range") }
                Spacer(Modifier.height(8.dp))
                Text(t.method, fontSize = 11.sp, color = Palette.TextFaint, lineHeight = 16.sp)
            }
        }

        // ---- Signal -------------------------------------------------------------------
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Signal", subtitle = "Tap a metric for a full explanation")
            val s = a.obs.signal
            val metrics = SignalQuality.metricsFor(radio)
            var first = true
            metrics.forEach { m ->
                val v = m.valueOf(s) ?: return@forEach
                if (!first) Divider()
                first = false
                KeyValueRow(
                    m.shortTitle, "${if (m == MetricKind.SINR) v.fmt(1) else v.fmt(0)} ${m.unit}".trim(),
                    hint = m.grade(v).takeIf { it != io.towerscope.calc.Grade.UNKNOWN }?.label,
                    valueColor = Palette.grade(m.grade(v)).takeIf { m.thresholds.isNotEmpty() } ?: Palette.Text,
                    onClick = if (a.obs.isServing) ({ nav.push(Route.Metric(m)) }) else null,
                )
            }
            s.csiRsrp?.let { Divider(); KeyValueRow("CSI-RSRP", "$it dBm", hint = "Beamformed channel-state reference") }
            s.csiSinr?.let { Divider(); KeyValueRow("CSI-SINR", "$it dB") }
            s.bitErrorRate?.let { Divider(); KeyValueRow("Bit error rate", "$it", hint = "RXQUAL 0 (best) – 7") }
            s.level?.let { Divider(); KeyValueRow("OS bars", "$it / 4") }
        }

        // ---- Frequency ----------------------------------------------------------------
        a.band?.let { b ->
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Spectrum", subtitle = "${b.frequencyClass} · ${b.duplex.label}")
                KeyValueRow("Band", b.name, hint = b.commonName)
                Divider(); KeyValueRow("Downlink", Fmt.mhz(b.dlMHz))
                b.ulMHz?.let { Divider(); KeyValueRow("Uplink", Fmt.mhz(it), hint = if (b.duplex.name == "TDD") "Same frequency, time-shared" else "Duplex gap ${(b.dlMHz - it).fmt(1)} MHz") }
                a.wavelengthM?.let { Divider(); KeyValueRow("Wavelength", if (it >= 1) "${it.fmt(2)} m" else "${(it * 100).fmt(1)} cm", hint = "λ = c / f") }
                Divider(); KeyValueRow("Free-space loss @ 1 km", "${RfModels.freeSpacePathLossDb(1.0, b.dlMHz).fmt(1)} dB", hint = "Lower bands lose less and penetrate buildings better")
            }
        }

        // ---- RF geometry --------------------------------------------------------------
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("RF path geometry", subtitle = "Assuming a ${Fmt.height(settings.towerHeightM, units)} antenna")
            a.pathLossDb?.let { KeyValueRow("Path loss", "${it.fmt(1)} dB", hint = "${a.transmitReferenceDbm.fmt(0)} dBm reference − measured") }
            a.fresnelRadiusM?.let { Divider(); KeyValueRow("1st Fresnel zone radius", Fmt.distance(it, units), hint = "Keep this ellipse clear at mid-path for line-of-sight") }
            a.elevationDeg?.let { Divider(); KeyValueRow("Elevation to antenna", "${it.fmt(2)}°", hint = "Angle above horizon from your position") }
            Divider(); KeyValueRow("Radio horizon", Fmt.distance(a.radioHorizonKm * 1000, units), hint = "4/3-earth model; beyond this the tower is below the horizon")
            a.dopplerHz?.let { Divider(); KeyValueRow("Doppler shift", "${it.fmt(1)} Hz", hint = "From your GPS speed ${Fmt.groundSpeed(fix?.speedMps ?: 0.0, units)}") }
            a.noiseLimitedSnrDb?.let { Divider(); KeyValueRow("Margin over noise floor", "${it.fmt(1)} dB", hint = "Thermal noise ${a.noiseFloorPerReDbm.fmt(1)} dBm/RE") }
        }

        // ---- Identity -----------------------------------------------------------------
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Identity", subtitle = "Network identifiers, decoded")
            a.decoded.forEachIndexed { i, f ->
                if (i > 0) Divider()
                KeyValueRow(f.label, f.value, hint = f.hint)
            }
            if (a.decoded.isEmpty()) Text("The modem did not report identity for this cell.", fontSize = 13.sp, color = Palette.TextFaint)
        }

        // ---- Location -----------------------------------------------------------------
        a.tower?.let { t ->
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Tower location", subtitle = "Source: ${t.source}")
                KeyValueRow("Latitude", Fmt.coord(t.lat))
                Divider(); KeyValueRow("Longitude", Fmt.coord(t.lon))
                t.rangeM?.let { Divider(); KeyValueRow("Coverage range", Fmt.distance(it, units), hint = "Radius over which samples were observed") }
                t.samples?.let { Divider(); KeyValueRow("Samples", "$it", hint = "More samples = more reliable position") }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { controller.openUrl("https://www.google.com/maps/search/?api=1&query=${t.lat},${t.lon}") }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open in Maps")
                }
            }
        }
    }
}

// =============================================================================================
// Database tower detail
// =============================================================================================

@Composable
fun DatabaseTowerScreen(controller: AppController, tower: DatabaseTower) {
    val fix by controller.fix.collectAsState()
    val heading by controller.heading.collectAsState()
    val settings by controller.settings.collectAsState()
    val live = rememberLiveAnalysis(controller)
    val units = settings.units
    val id = tower.identity
    val seen = live.all.firstOrNull { it.obs.identity.key == id.key }
    val distance = fix?.let { Geo.distanceM(it.lat, it.lon, tower.location.lat, tower.location.lon) }
    val bearing = fix?.let { Geo.bearingDeg(it.lat, it.lon, tower.location.lat, tower.location.lon) }

    ScreenColumn {
        GlassCard(Modifier.fillMaxWidth(), accent = Palette.radio(id.radio)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioBadge(id.radio, "${id.radio.generation} ${id.radio.label}")
                Spacer(Modifier.width(8.dp))
                when {
                    seen?.obs?.isServing == true -> Pill("YOUR SERVING CELL", Palette.Green)
                    seen != null -> Pill("HEARD BY YOUR PHONE", Palette.Blue)
                    else -> Pill("NOT HEARD", Palette.TextFaint)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DISTANCE", style = TextStyles.overline)
                    val (v, u) = Fmt.distanceParts(distance, units)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(v, style = TextStyles.metricLarge)
                        Spacer(Modifier.width(4.dp))
                        Text(u, style = TextStyles.unit, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    bearing?.let { Text("Bearing ${it.fmt(0)}° ${Geo.cardinal(it)}", style = TextStyles.mono.copy(color = Palette.Cyan)) }
                    tower.location.rangeM?.let { Text("Position uncertainty up to ${Fmt.distance(it, units)}", fontSize = 12.sp, color = Palette.TextFaint) }
                }
                CompassArrow(bearing, heading, Modifier.size(110.dp), Palette.radio(id.radio))
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Identity")
            val decoded = Analyzer.decode(io.towerscope.model.CellObservation(id, io.towerscope.model.SignalMetrics(), io.towerscope.model.ConnectionRole.NEIGHBOR, 0))
            decoded.forEachIndexed { i, f -> if (i > 0) Divider(); KeyValueRow(f.label, f.value, hint = f.hint) }
            tower.averageSignalDbm?.let { Divider(); KeyValueRow("Avg. signal (crowd)", "$it dBm", hint = "Average reported by contributors") }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Location", subtitle = "Source: ${tower.location.source}")
            KeyValueRow("Latitude", Fmt.coord(tower.location.lat))
            Divider(); KeyValueRow("Longitude", Fmt.coord(tower.location.lon))
            tower.location.samples?.let { Divider(); KeyValueRow("Samples", "$it") }
            if (distance != null) {
                Divider(); KeyValueRow("Elevation angle", "${RfModels.elevationAngleDeg(settings.towerHeightM - settings.handsetHeightM, distance).fmt(2)}°", hint = "Assuming ${Fmt.height(settings.towerHeightM, units)} antenna")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { controller.openUrl("https://www.google.com/maps/search/?api=1&query=${tower.location.lat},${tower.location.lon}") }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open in Maps")
            }
        }
        if (seen == null) {
            InfoNote(
                "Your phone isn't hearing this cell right now. It may belong to another operator, use a band your phone doesn't support, or be blocked by terrain.",
                icon = Icons.Rounded.Info,
            )
        }
        Box(Modifier.height(4.dp))
    }
}
