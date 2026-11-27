package io.towerscope.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.towerscope.AppController
import io.towerscope.calc.CellAnalysis
import io.towerscope.model.CellObservation
import io.towerscope.model.ConnectionRole
import io.towerscope.model.RadioType
import io.towerscope.ui.components.GlassCard
import io.towerscope.ui.theme.Palette

/** Live analysis of every cell in the current snapshot, recomputed when any input changes. */
data class LiveAnalysis(
    val serving: CellAnalysis?,
    /** 5G NSA leg or other secondary serving cells. */
    val secondary: List<CellAnalysis>,
    val neighbors: List<CellAnalysis>,
) {
    val all: List<CellAnalysis> get() = listOfNotNull(serving) + secondary + neighbors
    fun byKey(key: String) = all.firstOrNull { it.obs.key == key }
}

@Composable
fun rememberLiveAnalysis(controller: AppController): LiveAnalysis {
    val snapshot by controller.snapshot.collectAsState()
    val lookups by controller.lookups.collectAsState()
    val fix by controller.fix.collectAsState()
    val settings by controller.settings.collectAsState()
    val nearby by controller.nearby.collectAsState()
    return remember(snapshot, lookups, fix, settings, nearby) {
        val s = snapshot ?: return@remember LiveAnalysis(null, emptyList(), emptyList())
        val serving = s.serving
        LiveAnalysis(
            serving = serving?.let { controller.analyze(it) },
            secondary = s.cells.filter { it.isServing && it !== serving }.map { controller.analyze(it) },
            neighbors = s.neighbors.map { controller.analyze(it) }.sortedByDescending { it.obs.signal.rsrp ?: it.obs.signal.dbm ?: -200 },
        )
    }
}

fun cellTitle(o: CellObservation): String {
    val id = o.identity
    return when {
        o.synthetic -> "5G NR (NSA leg)"
        id.radio == RadioType.LTE && id.cellId != null -> "eNB ${id.cellId shr 8} · sector ${id.cellId and 0xFF}"
        id.radio == RadioType.NR && id.cellId != null -> "gNB ${id.cellId shr 12} · cell ${id.cellId and 0xFFF}"
        id.cellId != null -> "Cell ${id.cellId}"
        id.pci != null -> "PCI ${id.pci}"
        else -> "${id.radio.label} cell"
    }
}

fun cellSubtitle(a: CellAnalysis): String = listOfNotNull(
    a.obs.identity.operatorName?.takeIf { it.isNotBlank() },
    a.band?.let { b -> b.name + (b.commonName?.let { " ($it)" } ?: "") },
    a.obs.identity.pci?.takeIf { !a.obs.synthetic }?.let { "PCI $it" },
    if (a.obs.role == ConnectionRole.SECONDARY) "secondary" else null,
).joinToString(" · ").ifEmpty { a.obs.identity.radio.label }

fun radioLabel(o: CellObservation): String = when (o.identity.radio) {
    RadioType.NR -> "5G"
    RadioType.LTE -> "4G LTE"
    RadioType.WCDMA, RadioType.TDSCDMA -> "3G"
    RadioType.GSM -> "2G"
    RadioType.CDMA -> "CDMA"
    RadioType.UNKNOWN -> "?"
}

@Composable
fun PermissionCard(controller: AppController) {
    val perms by controller.permissions.collectAsState()
    GlassCard(accent = Palette.Cyan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = Palette.Cyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Permissions needed", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "TowerScope Live reads the cell towers your phone can hear and pairs them with GPS to locate them. " +
                "Your readings stay on this phone. The only things sent anywhere are tower lookups to OpenCelliD " +
                "(only if you add a key) and speed tests you start.",
            fontSize = 13.sp, color = Palette.TextDim, lineHeight = 18.sp,
        )
        Spacer(Modifier.height(14.dp))
        PermissionLine(Icons.Rounded.LocationOn, "Precise location", "Required by the OS to read cell IDs; used for distance & bearing", perms.location)
        if (perms.phoneStateApplicable) {
            Spacer(Modifier.height(8.dp))
            PermissionLine(Icons.Rounded.PhoneAndroid, "Phone state", "Network type, 5G status, carrier aggregation", perms.phoneState)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { controller.platform.permissions.request() },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Cyan, contentColor = Palette.Bg),
            ) { Text("Grant access", fontWeight = FontWeight.SemiBold) }
            if (perms.requestedOnce) {
                OutlinedButton(onClick = { controller.platform.permissions.openSettings() }) { Text("Open settings") }
            }
        }
    }
}

@Composable
private fun PermissionLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (granted) Palette.Green else Palette.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Palette.Text, fontWeight = FontWeight.Medium)
            Text(body, fontSize = 12.sp, color = Palette.TextFaint)
        }
        Text(if (granted) "Granted" else "Needed", fontSize = 12.sp, color = if (granted) Palette.Green else Palette.Amber)
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable () -> Unit) {
    GlassCard(modifier.fillMaxWidth()) {
        io.towerscope.ui.components.SectionHeader(title, subtitle = subtitle, modifier = Modifier)
        content()
    }
}
