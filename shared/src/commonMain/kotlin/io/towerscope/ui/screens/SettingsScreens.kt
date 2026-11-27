package io.towerscope.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.towerscope.AppController
import io.towerscope.calc.Fmt
import io.towerscope.calc.fmt
import io.towerscope.data.Environment
import io.towerscope.data.UnitSystem
import io.towerscope.model.PlatformKind
import io.towerscope.ui.ScreenColumn
import io.towerscope.ui.components.Divider
import io.towerscope.ui.components.GlassCard
import io.towerscope.ui.components.KeyValueRow
import io.towerscope.ui.components.SectionHeader
import io.towerscope.ui.theme.Palette

@Composable
fun SettingsScreen(controller: AppController, nav: io.towerscope.ui.Navigator) {
    val settings by controller.settings.collectAsState()
    var keyDraft by remember { mutableStateOf(settings.openCellIdKey) }
    var showKey by remember { mutableStateOf(false) }

    ScreenColumn {
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Tower database", subtitle = "OpenCelliD API key — stored only on this device")
            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it.trim() },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showKey) "Hide" else "Show")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { controller.openUrl("https://opencellid.org/register.php") }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Get a free key")
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = keyDraft != settings.openCellIdKey,
                    onClick = { controller.updateSettings { it.copy(openCellIdKey = keyDraft) } },
                ) { Text("Save key") }
            }
            if (!settings.hasApiKey) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "How to get one (free):\n" +
                        "1. Tap Get a free key, enter your email address and tap Login with email (new? tap Sign-up for free).\n" +
                        "2. Open the email titled OpenCelliD Project magic link and tap click here. That signs you in.\n" +
                        "3. On the dashboard, open Access Tokens and copy your token. It starts with pk.\n" +
                        "4. Come back here, paste it into API key and tap Save key.",
                    fontSize = 12.sp, color = Palette.TextDim, lineHeight = 18.sp,
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Propagation model", subtitle = "Used for path-loss distance and uplink estimates")
            Text("Environment", fontSize = 13.sp, color = Palette.TextDim)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Environment.entries.forEach { e ->
                    FilterChip(
                        selected = settings.environment == e,
                        onClick = { controller.updateSettings { it.copy(environment = e) } },
                        label = { Text(e.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Cyan.copy(alpha = 0.18f), selectedLabelColor = Palette.Cyan),
                    )
                }
            }
            Text(settings.environment.description, fontSize = 11.sp, color = Palette.TextFaint)
            Spacer(Modifier.height(14.dp))
            SliderSetting(
                "Tower antenna height", Fmt.height(settings.towerHeightM, settings.units),
                settings.towerHeightM.toFloat(), 10f..120f,
            ) { v -> controller.updateSettings { it.copy(towerHeightM = v.toDouble()) } }
            SliderSetting(
                "Reference signal EIRP", "${settings.referenceEirpDbm.fmt(0)} dBm",
                settings.referenceEirpDbm.toFloat(), 15f..45f,
                hint = "Per resource element incl. antenna gain. Typical macro site: 28–33 dBm",
            ) { v -> controller.updateSettings { it.copy(referenceEirpDbm = v.toDouble().let { d -> kotlin.math.round(d) }) } }
            SliderSetting(
                "Handset transmit power", "${settings.handsetTxPowerDbm.fmt(0)} dBm",
                settings.handsetTxPowerDbm.toFloat(), 17f..26f,
                hint = "Power class 3 = 23 dBm (most phones), class 2 = 26 dBm",
            ) { v -> controller.updateSettings { it.copy(handsetTxPowerDbm = kotlin.math.round(v.toDouble())) } }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Display")
            Text("Units", fontSize = 13.sp, color = Palette.TextDim)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitSystem.entries.forEach { u ->
                    FilterChip(
                        selected = settings.units == u,
                        onClick = { controller.updateSettings { it.copy(units = u) } },
                        label = { Text(u.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Cyan.copy(alpha = 0.18f), selectedLabelColor = Palette.Cyan),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Refresh interval", fontSize = 13.sp, color = Palette.TextDim)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10).forEach { s ->
                    FilterChip(
                        selected = settings.refreshSeconds == s,
                        onClick = { controller.updateSettings { it.copy(refreshSeconds = s) } },
                        label = { Text("${s}s") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Cyan.copy(alpha = 0.18f), selectedLabelColor = Palette.Cyan),
                    )
                }
            }
            Text("Android may throttle cell updates; faster intervals use more battery.", fontSize = 11.sp, color = Palette.TextFaint)
        }

        controller.license?.let { UnlockCard(it, controller.platform.nowMs()) }

        if (io.towerscope.platform.FeatureFlags.current.allowHistoryLogging) GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("History", subtitle = "Signal and speed over time, kept on this phone")
            io.towerscope.ui.components.DrillRow(
                title = "History & patterns",
                subtitle = if (settings.recordHistory) "Recording · location: ${settings.historyLocation.label.lowercase()}" else "Recording is off",
            ) { nav.push(io.towerscope.ui.Route.History) }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("About")
            KeyValueRow("App", "TowerScope Live " + (controller.platform.appVersion ?: ""))
            Divider(); KeyValueRow("Edition", if (io.towerscope.platform.FeatureFlags.current.isPlayEdition) "Google Play" else "Open source")
            Divider(); KeyValueRow("Tower data", "OpenCelliD (CC BY-SA 4.0)")
            Divider(); KeyValueRow("Speed test", "speed.cloudflare.com")
            Spacer(Modifier.height(6.dp))
            Text(
                "Distances and speeds derived from radio measurements are engineering estimates. They rely on the assumptions above and on crowd-sourced tower positions.",
                fontSize = 11.sp, color = Palette.TextFaint, lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun SliderSetting(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, hint: String? = null, onChange: (Float) -> Unit) {
    var local by remember(current) { mutableStateOf(current) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = Palette.TextDim, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = Palette.Text)
    }
    Slider(
        value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) }, valueRange = range,
        colors = SliderDefaults.colors(thumbColor = Palette.Cyan, activeTrackColor = Palette.Cyan, inactiveTrackColor = Palette.Stroke),
    )
    hint?.let { Text(it, fontSize = 11.sp, color = Palette.TextFaint) }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun DeviceScreen(controller: AppController) {
    val snapshot by controller.snapshot.collectAsState()
    val fix by controller.fix.collectAsState()
    val heading by controller.heading.collectAsState()
    val perms by controller.permissions.collectAsState()
    val settings by controller.settings.collectAsState()
    val d = controller.platform.device
    val net = snapshot?.network

    ScreenColumn {
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Device")
            KeyValueRow("Model", "${d.manufacturer} ${d.model}")
            Divider(); KeyValueRow("OS", "${d.osName} ${d.osVersion}", hint = d.apiLevel?.let { "API level $it" })
            Divider(); KeyValueRow("Cell access", if (snapshot?.capabilities?.canReadCells == false) "Restricted by OS" else "Full")
            if (d.platform == PlatformKind.ANDROID) {
                Divider(); KeyValueRow("Phone-state permission", if (perms.phoneState) "Granted" else "Not granted", hint = "Needed for 5G NSA and carrier-aggregation details")
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Network")
            KeyValueRow("Operator", net?.operatorName?.ifBlank { null } ?: "—", hint = net?.operatorMccMnc?.let { "PLMN $it" })
            Divider(); KeyValueRow("SIM operator", net?.simOperatorName?.ifBlank { null } ?: "—")
            Divider(); KeyValueRow("Network type", net?.networkTypeLabel ?: "—")
            Divider(); KeyValueRow("Roaming", when (net?.isRoaming) { true -> "Yes"; false -> "No"; null -> "—" })
            Divider(); KeyValueRow("Data transport", net?.transport?.label ?: "—")
            net?.duplexMode?.let { Divider(); KeyValueRow("Duplex mode", it) }
            if (net != null && net.cellBandwidthsKhz.isNotEmpty()) {
                Divider()
                KeyValueRow(
                    "Component carriers", net.cellBandwidthsKhz.joinToString(" + ") { (it / 1000.0).fmt(0) } + " MHz",
                    hint = if (net.cellBandwidthsKhz.size > 1) "Carrier aggregation active · ${(net.cellBandwidthsKhz.sum() / 1000.0).fmt(0)} MHz total" else "Single carrier",
                )
            }
            net?.linkDownKbps?.let { Divider(); KeyValueRow("OS bandwidth estimate ↓", Fmt.speed(it / 1000.0), hint = "From the platform's connectivity service") }
            net?.linkUpKbps?.let { Divider(); KeyValueRow("OS bandwidth estimate ↑", Fmt.speed(it / 1000.0)) }
            net?.simCount?.let { Divider(); KeyValueRow("Active SIM slots", "$it") }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Location & sensors")
            fix?.let { f ->
                KeyValueRow("Latitude", Fmt.coord(f.lat))
                Divider(); KeyValueRow("Longitude", Fmt.coord(f.lon))
                f.accuracyM?.let { Divider(); KeyValueRow("Horizontal accuracy", "± ${Fmt.distance(it, settings.units)}") }
                f.altitudeM?.let { Divider(); KeyValueRow("Altitude", Fmt.height(it, settings.units)) }
                f.speedMps?.let { Divider(); KeyValueRow("Speed", Fmt.groundSpeed(it, settings.units)) }
                f.provider?.let { Divider(); KeyValueRow("Provider", it) }
                Divider()
            } ?: KeyValueRow("GPS", "Waiting for fix…")
            KeyValueRow("Compass heading", heading?.let { "${it.fmt(0)}°" } ?: "Unavailable")
        }
    }
}
