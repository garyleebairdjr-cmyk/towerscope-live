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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.towerscope.calc.Fmt
import io.towerscope.calc.Geo
import io.towerscope.calc.InsightReport
import io.towerscope.calc.MetricKind
import io.towerscope.calc.PlaceStat
import io.towerscope.calc.TowerStat
import io.towerscope.calc.fmt
import io.towerscope.data.HistoryLocation
import io.towerscope.data.UnitSystem
import io.towerscope.ui.ScreenColumn
import io.towerscope.ui.components.Divider
import io.towerscope.ui.components.GlassCard
import io.towerscope.ui.components.InfoNote
import io.towerscope.ui.components.KeyValueRow
import io.towerscope.ui.components.SectionHeader
import io.towerscope.ui.theme.Palette
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(controller: AppController) {
    val history by controller.longHistory.collectAsState()
    val fix by controller.fix.collectAsState()
    val settings by controller.settings.collectAsState()
    // Recompute when history grows or the user moves about 100 m, not on every GPS tick.
    val area = fix?.let { (it.lat * 1000).roundToInt() to (it.lon * 1000).roundToInt() }
    val report = remember(history.size, area, settings.historyLocation) { controller.insights() }
    val units = settings.units

    ScreenColumn {
        SummaryCard(controller, report)
        report.alerts.forEach { InfoNote(it.message, color = Palette.Amber, icon = Icons.Rounded.Warning) }

        if (report.isEmpty) {
            InfoNote(
                if (settings.recordHistory) "Nothing recorded yet. Keep TowerScope open as you go about your day, and run a speed test now " +
                    "and then. Patterns appear after a few days."
                else "Recording is off. Turn it on below to start learning where and when your connection is good.",
                icon = Icons.Rounded.Info,
            )
        } else {
            PlacesCard(report.places, units, settings.historyLocation)
            TimeOfDayCard(report)
            TowersCard(report.towers)
        }
        HistorySettingsCard(controller)
    }
}

@Composable
private fun SummaryCard(controller: AppController, r: InsightReport) {
    GlassCard(Modifier.fillMaxWidth()) {
        SectionHeader("Your history", subtitle = "Kept on this phone for up to 90 days")
        KeyValueRow("Signal readings", "${r.signalCount}")
        Divider(); KeyValueRow("Speed tests", "${r.speedCount}")
        r.sinceMs?.let {
            val days = ((controller.platform.nowMs() - it) / 86_400_000L).toInt()
            Divider(); KeyValueRow("Covers", if (days < 1) "Today" else "$days day${if (days == 1) "" else "s"}")
        }
        if (r.devices.size > 1) {
            Divider()
            KeyValueRow(
                "Phones", "${r.devices.size}",
                hint = r.devices.joinToString(" · ") { (d, n) -> "$d ($n)" + if (d == controller.deviceLabel) ", this one" else "" },
            )
        }
    }
}

@Composable
private fun PlacesCard(places: List<PlaceStat>, units: UnitSystem, level: HistoryLocation) {
    SectionHeader("Best places", subtitle = "Areas of about 100 m where you've spent time, best signal first")
    if (places.isEmpty()) {
        InfoNote(
            if (level == HistoryLocation.NONE) "Location isn't recorded, so places can't be compared. Choose Approximate or Exact below."
            else "Not enough readings in one place yet. A place needs about 10 minutes of readings or two speed tests.",
        )
        return
    }
    GlassCard(Modifier.fillMaxWidth()) {
        places.take(6).forEachIndexed { i, p ->
            if (i > 0) Divider()
            val where = when {
                p.isHere -> "Here"
                p.distanceFromHereM != null && p.bearingFromHereDeg != null ->
                    "${Fmt.distance(p.distanceFromHereM, units)} ${Geo.cardinal(p.bearingFromHereDeg)} of you"
                else -> "${Fmt.coord(p.lat)}, ${Fmt.coord(p.lon)}"
            }
            StatRow(
                title = (if (i == 0) "★ " else "") + where,
                signal = p.medianSignalDbm,
                detail = listOfNotNull(
                    "${p.signalSamples} readings".takeIf { p.signalSamples > 0 },
                    p.medianDownMbps?.let { "${Fmt.speed(it)} typical (${p.speedTests} test${if (p.speedTests == 1) "" else "s"})" },
                ).joinToString(" · "),
            )
        }
    }
}

@Composable
private fun TimeOfDayCard(r: InsightReport) {
    val t = r.timeOfDay ?: return
    SectionHeader("Time of day", subtitle = if (t.local) "Around here" else "All your readings")
    t.finding?.let { InfoNote(it, icon = Icons.Rounded.Info) }
    GlassCard(Modifier.fillMaxWidth()) {
        t.periods.filter { it.signalSamples > 0 || it.speedTests > 0 }.forEachIndexed { i, p ->
            if (i > 0) Divider()
            StatRow(
                title = p.period.label.replaceFirstChar { it.uppercase() },
                signal = p.medianSignalDbm,
                detail = listOfNotNull(
                    "${p.signalSamples} readings".takeIf { p.signalSamples > 0 },
                    p.medianDownMbps?.let { "${Fmt.speed(it)} typical (${p.speedTests} test${if (p.speedTests == 1) "" else "s"})" },
                ).joinToString(" · "),
            )
        }
    }
}

@Composable
private fun TowersCard(towers: List<TowerStat>) {
    if (towers.isEmpty()) return
    SectionHeader("Your towers", subtitle = "Cells you've used, best typical signal first")
    GlassCard(Modifier.fillMaxWidth()) {
        towers.take(8).forEachIndexed { i, t ->
            if (i > 0) Divider()
            StatRow(
                title = towerTitle(t),
                signal = t.medianSignalDbm,
                detail = listOfNotNull(
                    "${t.signalSamples} readings",
                    t.medianDownMbps?.let { "${Fmt.speed(it)} typical (${t.speedTests} test${if (t.speedTests == 1) "" else "s"})" },
                ).joinToString(" · "),
            )
        }
    }
}

/** "LTE:310:260:11544:21527043" → "eNB 84090 · sector 3 (LTE)". */
private fun towerTitle(t: TowerStat): String {
    val cid = t.cellKey.substringAfterLast(':').toLongOrNull() ?: return t.cellKey
    return when (t.radio) {
        io.towerscope.model.RadioType.LTE -> "eNB ${cid shr 8} · sector ${cid and 0xFF}"
        io.towerscope.model.RadioType.NR -> "gNB ${cid shr 12} · cell ${cid and 0xFFF}"
        else -> "Cell $cid"
    } + " (${t.radio.label})"
}

@Composable
private fun StatRow(title: String, signal: Double?, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = Palette.Text, fontWeight = FontWeight.Medium)
            if (detail.isNotEmpty()) Text(detail, fontSize = 12.sp, color = Palette.TextDim)
        }
        signal?.let {
            Text("${it.fmt(0)} dBm", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.grade(MetricKind.RSRP.grade(it)))
        }
    }
}

@Composable
private fun HistorySettingsCard(controller: AppController) {
    val settings by controller.settings.collectAsState()
    val backedUp by controller.historyBackedUp.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    GlassCard(Modifier.fillMaxWidth()) {
        SectionHeader("What TowerScope keeps", subtitle = "Your choice. Nothing here is ever uploaded by the app.")

        SwitchRow(
            "Record history", "Signal about once a minute while the app is open, plus each speed test.",
            settings.recordHistory,
        ) { on -> controller.setRecordHistory(on) }

        val flags = io.towerscope.platform.FeatureFlags.current
        if (flags.allowBackgroundLocation && controller.platform.backgroundRecorder != null) {
            val bg by controller.backgroundRecording.collectAsState()
            Spacer(Modifier.height(12.dp))
            SwitchRow(
                "Keep recording when the app is closed",
                if (!settings.recordHistory) "Turn on Record history first."
                else "Shows a notification while it runs, with a Stop button. Reads the radio about every 20 seconds " +
                    "and uses more battery. Off by default.",
                settings.backgroundRecording && bg,
            ) { on -> if (settings.recordHistory || !on) controller.setBackgroundRecording(on) }
        }

        Spacer(Modifier.height(12.dp))
        Text("Location with each reading", fontSize = 13.sp, color = Palette.TextDim)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryLocation.entries.forEach { l ->
                FilterChip(
                    selected = settings.historyLocation == l,
                    onClick = { controller.setHistoryLocation(l) },
                    label = { Text(l.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Cyan.copy(alpha = 0.18f), selectedLabelColor = Palette.Cyan),
                )
            }
        }
        Text(settings.historyLocation.description, fontSize = 11.sp, color = Palette.TextFaint, lineHeight = 16.sp)
        if (settings.historyLocation != HistoryLocation.EXACT) {
            Text("Readings you already have were changed to match.", fontSize = 11.sp, color = Palette.TextFaint)
        }

        if (controller.historyBackupSupported) {
            Spacer(Modifier.height(12.dp))
            SwitchRow(
                "Include in my Google backup",
                if (backedUp) "Your history goes into this phone's Google backup, so it comes back if you reinstall TowerScope " +
                    "from the Play Store or set up a new phone from the backup. Each reading notes which phone took it."
                else "Off: the history stays on this phone and is deleted if you uninstall TowerScope.",
                backedUp,
            ) { on -> controller.setHistoryBackedUp(on) }
        }

        if (io.towerscope.platform.FeatureFlags.current.allowHistoryExport) {
            val history by controller.longHistory.collectAsState()
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { controller.exportHistory() }, enabled = history.isNotEmpty()) {
                Icon(Icons.Rounded.IosShare, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export as a spreadsheet (CSV)")
            }
            Text(
                "Opens Android's share sheet so you can save the file or send it where you choose. TowerScope never uploads it.",
                fontSize = 11.sp, color = Palette.TextFaint, lineHeight = 16.sp,
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(16.dp), tint = Palette.Red)
            Spacer(Modifier.width(6.dp))
            Text("Delete all history", color = Palette.Red)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all history?") },
            text = {
                Text(
                    "This removes every saved signal reading and speed test from this phone. If history was in your Google " +
                        "backup, it's removed from there at the next backup. This can't be undone.",
                    fontSize = 13.sp, color = Palette.TextDim,
                )
            },
            confirmButton = { TextButton(onClick = { controller.clearHistory(); confirmDelete = false }) { Text("Delete", color = Palette.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Palette.Text)
            Text(subtitle, fontSize = 11.sp, color = Palette.TextFaint, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Palette.Bg, checkedTrackColor = Palette.Cyan),
        )
    }
}
