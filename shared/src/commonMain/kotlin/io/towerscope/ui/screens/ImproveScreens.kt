package io.towerscope.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.towerscope.AppController
import io.towerscope.calc.Advice
import io.towerscope.calc.AdviceAction
import io.towerscope.calc.AdviceLevel
import io.towerscope.calc.Fmt
import io.towerscope.calc.GuideTopic
import io.towerscope.calc.MetricKind
import io.towerscope.calc.SettingsTarget
import io.towerscope.calc.fmt
import io.towerscope.data.SavedSpot
import io.towerscope.model.RadioType
import io.towerscope.ui.Navigator
import io.towerscope.ui.Route
import io.towerscope.ui.ScreenColumn
import io.towerscope.ui.Tab
import io.towerscope.ui.components.Divider
import io.towerscope.ui.components.GlassCard
import io.towerscope.ui.components.GradePill
import io.towerscope.ui.components.InfoNote
import io.towerscope.ui.components.Pill
import io.towerscope.ui.components.SectionHeader
import io.towerscope.ui.components.ThresholdScale
import io.towerscope.ui.theme.Palette
import io.towerscope.ui.theme.TextStyles
import kotlin.math.pow
import kotlin.math.roundToInt

// =============================================================================================
// Diagnose card (Overview)
// =============================================================================================

private fun levelColor(l: AdviceLevel): Color = when (l) {
    AdviceLevel.ACTION -> Palette.Amber
    AdviceLevel.TIP -> Palette.Cyan
    AdviceLevel.INFO -> Palette.Blue
}

private fun levelIcon(l: AdviceLevel) = when (l) {
    AdviceLevel.ACTION -> Icons.Rounded.Warning
    AdviceLevel.TIP -> Icons.Rounded.TipsAndUpdates
    AdviceLevel.INFO -> Icons.Rounded.Info
}

private fun actionLabel(a: AdviceAction): String = when (a) {
    is AdviceAction.OpenSettings -> a.target.buttonLabel
    AdviceAction.FindBestSpot -> "Find best spot"
    AdviceAction.RunSpeedTest -> "Run speed test"
}

fun runAdviceAction(a: AdviceAction, controller: AppController, nav: Navigator) = when (a) {
    is AdviceAction.OpenSettings -> controller.openSystemSettings(a.target)
    AdviceAction.FindBestSpot -> nav.push(Route.SpotFinder)
    AdviceAction.RunSpeedTest -> nav.select(Tab.SPEED)
}

@Composable
fun DiagnoseCard(controller: AppController, nav: Navigator) {
    val advice by controller.advice.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val top = advice.firstOrNull()?.level
    GlassCard(Modifier.fillMaxWidth().animateContentSize(), accent = if (top == AdviceLevel.ACTION) Palette.Amber else null) {
        SectionHeader(
            "Diagnosis",
            subtitle = if (advice.isEmpty()) null else "${advice.size} suggestion${if (advice.size == 1) "" else "s"} for this spot",
            trailing = {
                TextButton(onClick = { nav.push(Route.Guide(null)) }) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Guide", fontSize = 13.sp)
                }
            },
        )
        if (advice.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Palette.Green, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("No problems found", fontSize = 15.sp, color = Palette.Text, fontWeight = FontWeight.Medium)
                    Text("Signal and phone settings look fine here.", fontSize = 12.sp, color = Palette.TextDim)
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { nav.push(Route.SpotFinder) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("Compare spots anyway", fontSize = 13.sp, color = Palette.Cyan)
            }
        } else {
            val shown = if (expanded) advice else advice.take(2)
            shown.forEachIndexed { idx, a ->
                if (idx > 0) { Spacer(Modifier.height(4.dp)); Divider(); Spacer(Modifier.height(4.dp)) }
                AdviceItem(a, controller, nav)
            }
            if (advice.size > 2) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(if (expanded) "Show less" else "Show ${advice.size - 2} more", fontSize = 13.sp, color = Palette.Cyan)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdviceItem(a: Advice, controller: AppController, nav: Navigator) {
    val color = levelColor(a.level)
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(levelIcon(a.level), null, tint = color, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, fontSize = 15.sp, color = Palette.Text, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(a.body, fontSize = 13.sp, color = Palette.TextDim, lineHeight = 18.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                a.action?.let { act ->
                    OutlinedButton(
                        onClick = { runAdviceAction(act, controller, nav) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text(actionLabel(act), fontSize = 12.sp, color = color) }
                }
                a.topic?.let { t ->
                    TextButton(
                        onClick = { nav.push(Route.Guide(t)) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Why?", fontSize = 12.sp, color = Palette.TextDim) }
                }
            }
        }
    }
}

// =============================================================================================
// Find best spot
// =============================================================================================

private const val AVERAGE_WINDOW_MS = 4_000L

@Composable
fun SpotFinderScreen(controller: AppController, nav: Navigator) {
    DisposableEffect(Unit) {
        controller.setFastRadio(true)
        onDispose { controller.setFastRadio(false) }
    }
    val history by controller.history.collectAsState()
    val snapshot by controller.snapshot.collectAsState()
    val spots by controller.spots.collectAsState()
    val haptics = LocalHapticFeedback.current

    val serving = snapshot?.serving
    val radio = serving?.identity?.radio
    val kind = if (radio == RadioType.WCDMA) MetricKind.RSCP else MetricKind.RSRP

    // Average over the last few seconds: single readings jump around by several dB.
    val latestTs = history.lastOrNull()?.timestampMs ?: 0L
    val window = history.filter { latestTs - it.timestampMs <= AVERAGE_WINDOW_MS && it.cellKey == serving?.key }
    val levels = window.mapNotNull { (it.rsrp ?: it.dbm)?.toDouble() }
    val avg = levels.takeIf { it.isNotEmpty() }?.average()
    val sinrAvg = window.mapNotNull { it.sinr }.takeIf { it.isNotEmpty() }?.average()
    val previous = history.filter { latestTs - it.timestampMs in 5_000L..9_000L && it.cellKey == serving?.key }
        .mapNotNull { (it.rsrp ?: it.dbm)?.toDouble() }.takeIf { it.isNotEmpty() }?.average()

    var startLevel by remember { mutableStateOf<Double?>(null) }
    var startCell by remember { mutableStateOf<String?>(null) }
    var best by remember { mutableStateOf<Double?>(null) }
    var showSave by remember { mutableStateOf(false) }

    LaunchedEffect(avg, levels.size) {
        if (avg == null || levels.size < 3) return@LaunchedEffect
        if (startLevel == null) { startLevel = avg; startCell = serving?.key }
        val b = best
        if (b == null || avg >= b + 1.0) {
            if (b != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            best = avg
        }
    }

    ScreenColumn {
        InfoNote(
            "Walk slowly and pause for about 5 seconds at each place. Hold the phone the way you normally use it. " +
                "Save the spots you try and the best one rises to the top. The phone vibrates when you reach a new best.",
            icon = Icons.Rounded.MyLocation,
        )
        GlassCard(Modifier.fillMaxWidth(), accent = avg?.let { Palette.grade(kind.grade(it)) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${kind.shortTitle} · 4-SECOND AVERAGE", style = TextStyles.overline)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(avg?.fmt(1) ?: "—", style = TextStyles.metricLarge)
                        Spacer(Modifier.width(4.dp))
                        Text("dBm", style = TextStyles.unit, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                avg?.let { GradePill(kind.grade(it)) }
            }
            val trend = if (avg != null && previous != null) avg - previous else null
            Text(
                when {
                    avg == null -> "Waiting for readings…"
                    trend == null -> "Measuring…"
                    trend >= 2 -> "▲ ${trend.fmt(1)} dB better than a few seconds ago"
                    trend <= -2 -> "▼ ${(-trend).fmt(1)} dB worse than a few seconds ago"
                    else -> "Holding steady"
                },
                fontSize = 14.sp,
                color = when {
                    trend == null -> Palette.TextDim
                    trend >= 2 -> Palette.Green
                    trend <= -2 -> Palette.Red
                    else -> Palette.TextDim
                },
            )
            Spacer(Modifier.height(6.dp))
            ThresholdScale(kind, avg)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SpotStat("Start", startLevel)
                SpotStat("Best", best, Palette.Green)
                SpotStat("Change", if (avg != null && startLevel != null) avg - startLevel!! else null, signed = true)
                Column {
                    Text("SINR", style = TextStyles.overline.copy(fontSize = 10.sp))
                    Text(sinrAvg?.let { "${it.fmt(1)} dB" } ?: "—", style = TextStyles.mono.copy(fontSize = 15.sp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { showSave = true }, enabled = avg != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Cyan, contentColor = Palette.Bg),
                ) { Text("Save this spot") }
                OutlinedButton(onClick = { startLevel = avg; best = avg; startCell = serving?.key }) { Text("Reset") }
            }
        }
        if (startCell != null && serving != null && serving.key != startCell) {
            InfoNote(
                "Your phone switched to a different cell since you started. Readings from different cells aren't directly comparable.",
                color = Palette.Amber, icon = Icons.Rounded.Warning,
            )
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(
                "Saved spots", subtitle = if (spots.isEmpty()) "None yet" else "Strongest first",
                trailing = if (spots.isNotEmpty()) ({ TextButton(onClick = { controller.clearSpots() }) { Text("Clear", fontSize = 13.sp, color = Palette.TextDim) } }) else null,
            )
            if (spots.isEmpty()) {
                Text("Save a reading at each place you try: a window, another room, upstairs.", fontSize = 13.sp, color = Palette.TextDim)
            }
            val top = spots.maxOfOrNull { it.signalDbm }
            spots.forEachIndexed { i, s ->
                if (i > 0) Divider()
                SpotRow(s, isBest = s.signalDbm == top, deltaFromBest = top?.let { s.signalDbm - it }) { controller.deleteSpot(s) }
            }
            if (spots.size >= 2 && top != null) {
                val worst = spots.minOf { it.signalDbm }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your best spot is ${(top - worst).fmt(0)} dB stronger than your worst. " +
                        if (top - worst >= 10) "That's a big difference: about ${10.0.pow((top - worst) / 10).roundToInt()}× the power."
                        else "Differences under 3 dB are within normal variation.",
                    fontSize = 12.sp, color = Palette.TextFaint, lineHeight = 17.sp,
                )
            }
        }
        TextButton(onClick = { nav.push(Route.Guide(GuideTopic.LOCATION)) }) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Why location matters so much")
        }
    }

    if (showSave) {
        var name by remember { mutableStateOf("Spot ${spots.size + 1}") }
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Save this spot") },
            text = {
                Column {
                    Text("Saving ${avg?.fmt(1) ?: "—"} dBm. Give it a name you'll recognise.", fontSize = 13.sp, color = Palette.TextDim)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, label = { Text("Name") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    avg?.let {
                        controller.saveSpot(SavedSpot(name.ifBlank { "Spot ${spots.size + 1}" }, it, sinrAvg, radio?.label ?: "", controller.platform.nowMs()))
                    }
                    showSave = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SpotStat(label: String, value: Double?, color: Color = Palette.Text, signed: Boolean = false) {
    Column {
        Text(label.uppercase(), style = TextStyles.overline.copy(fontSize = 10.sp))
        Text(
            value?.let { (if (signed && it > 0) "+" else "") + it.fmt(1) } ?: "—",
            style = TextStyles.mono.copy(fontSize = 15.sp, color = color),
        )
    }
}

@Composable
private fun SpotRow(s: SavedSpot, isBest: Boolean, deltaFromBest: Double?, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, fontSize = 15.sp, color = Palette.Text, fontWeight = FontWeight.Medium)
                if (isBest) { Spacer(Modifier.width(8.dp)); Pill("BEST", Palette.Green) }
            }
            Text(
                listOfNotNull(s.radioLabel.ifBlank { null }, s.sinrDb?.let { "SINR ${it.fmt(1)} dB" })
                    .joinToString(" · "),
                fontSize = 12.sp, color = Palette.TextFaint,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${s.signalDbm.fmt(1)} dBm", style = TextStyles.mono, color = Palette.grade(MetricKind.RSRP.grade(s.signalDbm)))
            if (!isBest && deltaFromBest != null) Text("${deltaFromBest.fmt(1)} dB", fontSize = 11.sp, color = Palette.TextFaint)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Close, "Delete", tint = Palette.TextFaint, modifier = Modifier.size(18.dp)) }
    }
}

// =============================================================================================
// Guide
// =============================================================================================

private fun topicAction(t: GuideTopic): AdviceAction? = when (t) {
    GuideTopic.LOCATION, GuideTopic.GRIP_CASE -> AdviceAction.FindBestSpot
    GuideTopic.RESELECT -> AdviceAction.OpenSettings(SettingsTarget.AIRPLANE_MODE)
    GuideTopic.FIVE_G -> AdviceAction.OpenSettings(SettingsTarget.NETWORK_MODE)
    GuideTopic.BATTERY_SAVER -> AdviceAction.OpenSettings(SettingsTarget.BATTERY_SAVER)
    GuideTopic.WIFI_CALLING -> AdviceAction.OpenSettings(SettingsTarget.WIFI_CALLING)
    GuideTopic.UPDATES -> AdviceAction.OpenSettings(SettingsTarget.SYSTEM_UPDATE)
    GuideTopic.CONGESTION -> AdviceAction.RunSpeedTest
    else -> null
}

@Composable
fun GuideScreen(controller: AppController, nav: Navigator, focus: GuideTopic?) {
    val scroll = rememberScrollState()
    val positions = remember { mutableStateMapOf<GuideTopic, Int>() }
    var target by remember { mutableStateOf(focus) }
    LaunchedEffect(target, positions[target]) {
        val t = target ?: return@LaunchedEffect
        positions[t]?.let { scroll.animateScrollTo(it); target = null }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Better signal: what actually works", fontSize = 20.sp, color = Palette.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(GuideContent.intro, fontSize = 14.sp, color = Palette.TextDim, lineHeight = 20.sp)
            Spacer(Modifier.height(10.dp))
            Text("CONTENTS", style = TextStyles.overline)
            GuideContent.sections.forEach { sec ->
                TextButton(onClick = { target = sec.topic }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                    Text(sec.topic.title, fontSize = 14.sp, color = Palette.Cyan, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        GuideContent.sections.forEach { sec ->
            GlassCard(
                Modifier.fillMaxWidth().onGloballyPositioned { positions[sec.topic] = it.positionInParent().y.toInt() },
                accent = if (sec.topic == focus) Palette.Cyan else null,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sec.topic.title, fontSize = 17.sp, color = Palette.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    sec.typicalGain?.let { Pill(it, Palette.Green) }
                }
                sec.blocks.forEach { b ->
                    Spacer(Modifier.height(8.dp))
                    when (b) {
                        is GuideBlock.Para -> Text(b.text, fontSize = 14.sp, color = Palette.TextDim, lineHeight = 21.sp)
                        is GuideBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            b.items.forEach { item ->
                                Row {
                                    Text("•", fontSize = 14.sp, color = Palette.Cyan, modifier = Modifier.width(16.dp))
                                    Text(item, fontSize = 14.sp, color = Palette.TextDim, lineHeight = 21.sp)
                                }
                            }
                        }
                        is GuideBlock.Caveat -> InfoNote(b.text, color = Palette.Amber, icon = Icons.Rounded.Info)
                    }
                }
                topicAction(sec.topic)?.let { act ->
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { runAdviceAction(act, controller, nav) }) { Text(actionLabel(act), fontSize = 13.sp) }
                }
            }
        }
        Text(
            "Figures are typical ranges from field experience and published measurements, not guarantees. Every building, phone and network is different.",
            fontSize = 11.sp, color = Palette.TextFaint, lineHeight = 16.sp,
        )
    }
}
