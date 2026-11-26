package io.towerscope.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import io.towerscope.calc.Analyzer
import io.towerscope.calc.Fmt
import io.towerscope.calc.fmt
import io.towerscope.data.SpeedPhase
import io.towerscope.model.RadioType
import io.towerscope.model.Transport
import io.towerscope.ui.ScreenColumn
import io.towerscope.ui.components.BarDatum
import io.towerscope.ui.components.ChartSeries
import io.towerscope.ui.components.Divider
import io.towerscope.ui.components.GlassCard
import io.towerscope.ui.components.HorizontalBars
import io.towerscope.ui.components.InfoNote
import io.towerscope.ui.components.LineChart
import io.towerscope.ui.components.MetricTile
import io.towerscope.ui.components.SectionHeader
import io.towerscope.ui.components.SpeedGauge
import io.towerscope.ui.components.TileRow
import io.towerscope.ui.theme.Palette
import io.towerscope.ui.theme.TextStyles

@Composable
fun SpeedScreen(controller: AppController) {
    val st by controller.speed.collectAsState()
    val history by controller.speedHistory.collectAsState()
    val snapshot by controller.snapshot.collectAsState()
    val live = rememberLiveAnalysis(controller)
    var confirm by remember { mutableStateOf(false) }
    val transport = snapshot?.network?.transport
    val onCellular = transport == Transport.CELLULAR

    val phaseColor = when (st.phase) {
        SpeedPhase.UPLOAD -> Palette.Pink
        SpeedPhase.LATENCY -> Palette.Amber
        else -> Palette.Green
    }
    val shown = when (st.phase) {
        SpeedPhase.DOWNLOAD, SpeedPhase.UPLOAD -> st.liveMbps
        SpeedPhase.DONE -> st.downMbps ?: 0.0
        else -> 0.0
    }

    ScreenColumn {
        if (transport == Transport.WIFI) {
            InfoNote("You're on Wi-Fi — this test measures your Wi-Fi connection, not the cell tower. Turn Wi-Fi off to test cellular.", color = Palette.Amber, icon = Icons.Rounded.Warning)
        }
        GlassCard(Modifier.fillMaxWidth(), accent = if (st.running) phaseColor else null) {
            val (v, u) = when (st.phase) {
                SpeedPhase.LATENCY -> (st.pingMs?.fmt(0) ?: "…") to "ms"
                SpeedPhase.IDLE -> "—" to "Mbps"
                SpeedPhase.ERROR -> "!" to ""
                else -> Fmt.speedParts(shown).let { it.first to it.second }
            }
            SpeedGauge(
                mbps = shown, color = phaseColor,
                centerTop = if (st.phase == SpeedPhase.DONE) "Download" else st.phase.label,
                centerValue = v, centerUnit = u,
            )
            if (st.running) {
                val p by animateFloatAsState(st.progress)
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = phaseColor, trackColor = Palette.Stroke)
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (st.running) {
                    Button(onClick = { controller.cancelSpeedTest() }, colors = ButtonDefaults.buttonColors(containerColor = Palette.SurfaceHi, contentColor = Palette.Text)) {
                        Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stop")
                    }
                } else {
                    Button(
                        onClick = { if (onCellular) confirm = true else controller.startSpeedTest() },
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Cyan, contentColor = Palette.Bg),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp))
                        Text(if (st.phase == SpeedPhase.DONE || st.phase == SpeedPhase.ERROR) "Run again" else "Start test", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            st.error?.let {
                Spacer(Modifier.height(8.dp))
                Text("Test failed: $it", color = Palette.Red, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Server: ${st.server}" + if (st.bytesUsed > 0) " · ${Fmt.bytes(st.bytesUsed)} transferred" else "",
                fontSize = 11.sp, color = Palette.TextFaint, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        TileRow {
            MetricTile("Ping", st.pingMs?.fmt(0) ?: "—", "ms", Modifier.weight(1f), Icons.Rounded.Timer, Palette.Amber, caption = "Median RTT")
            MetricTile("Jitter", st.jitterMs?.fmt(1) ?: "—", "ms", Modifier.weight(1f), Icons.Rounded.GraphicEq, Palette.Violet, caption = "Mean RTT variation")
        }
        TileRow {
            MetricTile("Download", Fmt.speedParts(st.downMbps).first, Fmt.speedParts(st.downMbps).second, Modifier.weight(1f), Icons.Rounded.Download, Palette.Green, caption = "4 parallel streams")
            MetricTile("Upload", Fmt.speedParts(st.upMbps).first, Fmt.speedParts(st.upMbps).second, Modifier.weight(1f), Icons.Rounded.Upload, Palette.Pink, caption = "3 parallel streams")
        }

        if (st.downSeries.size > 2 || st.upSeries.size > 2) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Throughput over time", subtitle = "Green: download, then pink: upload (Mbps)")
                // The phases run one after the other, so lay them out sequentially on a shared time axis.
                val dl = st.downSeries
                val ul = st.upSeries
                val maxV = (dl + ul).maxOrNull()?.coerceAtLeast(1f) ?: 1f
                LineChart(
                    listOf(
                        ChartSeries(dl.map<Float, Float?> { it } + List(ul.size) { null }, Palette.Green, "DL"),
                        ChartSeries(List<Float?>(dl.size) { null } + ul, Palette.Pink, "UL"),
                    ),
                    0f, maxV * 1.15f,
                )
            }
        }

        // ---- Measured vs predicted ------------------------------------------------------
        val nsa = live.secondary.firstOrNull { it.obs.identity.radio == RadioType.NR }
        val predicted = Analyzer.combinedThroughput(live.serving, nsa)
        val net = snapshot?.network
        if (predicted != null || net?.linkDownKbps != null) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Measured vs predicted", subtitle = "Download, Mbps")
                val rows = buildList {
                    st.downMbps?.let { add(BarDatum("Measured", it, Fmt.speed(it), Palette.Green, highlight = true)) }
                    predicted?.let { add(BarDatum("Radio model", it.first, Fmt.speed(it.first), Palette.Cyan)) }
                    net?.linkDownKbps?.let { add(BarDatum("Android est.", it / 1000.0, Fmt.speed(it / 1000.0), Palette.Blue)) }
                    live.serving?.throughput?.dlPeakMbps?.let { add(BarDatum("Channel peak", it + (nsa?.throughput?.dlPeakMbps ?: 0.0), Fmt.speed(it + (nsa?.throughput?.dlPeakMbps ?: 0.0)), Palette.TextDim)) }
                }
                HorizontalBars(rows, 0.0, (rows.maxOfOrNull { it.value } ?: 1.0) * 1.05)
                if (st.downMbps != null && predicted != null && predicted.first > 0) {
                    Spacer(Modifier.height(10.dp))
                    val ratio = st.downMbps!! / predicted.first
                    Text(
                        when {
                            !onCellular -> "Measured over ${transport?.label ?: "another network"}, so it isn't comparable with the radio model."
                            ratio > 1.3 -> "Faster than the single-carrier model — likely extra aggregated carriers or a lightly loaded cell."
                            ratio > 0.6 -> "In line with what your signal supports. The radio link is the limiting factor."
                            ratio > 0.25 -> "Below what the signal supports (${(ratio * 100).fmt(0)} %). The cell is probably shared with other users."
                            else -> "Much slower than the radio supports — congestion, operator throttling or a backhaul limit."
                        },
                        fontSize = 12.sp, color = Palette.TextDim, lineHeight = 17.sp,
                    )
                }
            }
        }

        if (history.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Recent tests")
                history.forEachIndexed { i, r ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.networkLabel.ifBlank { "Unknown network" }, fontSize = 13.sp, color = Palette.Text)
                            Text(Fmt.ago(controller.platform.nowMs() - r.timestampMs), fontSize = 11.sp, color = Palette.TextFaint)
                        }
                        Text("↓ ${Fmt.speed(r.downMbps)}", style = TextStyles.mono.copy(fontSize = 12.sp, color = Palette.Green))
                        Spacer(Modifier.width(10.dp))
                        Text("↑ ${Fmt.speed(r.upMbps)}", style = TextStyles.mono.copy(fontSize = 12.sp, color = Palette.Pink))
                        Spacer(Modifier.width(10.dp))
                        Text("${r.pingMs?.fmt(0) ?: "—"} ms", style = TextStyles.mono.copy(fontSize = 12.sp, color = Palette.Amber))
                    }
                }
            }
        }
        InfoNote(
            "Tip: an active transfer makes the modem report timing advance and CQI. Check the Signal tab during a test for the most precise distance.",
            icon = Icons.Rounded.Info,
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Use mobile data?") },
            text = { Text("A speed test on a fast 5G connection can transfer 100 MB or more of mobile data.") },
            confirmButton = { TextButton(onClick = { confirm = false; controller.startSpeedTest() }) { Text("Run test") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
            containerColor = Palette.SurfaceHi,
        )
    }
}
