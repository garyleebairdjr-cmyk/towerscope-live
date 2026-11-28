package io.towerscope.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.towerscope.license.LicenseManager
import io.towerscope.license.LicenseState
import io.towerscope.ui.components.GlassCard
import io.towerscope.ui.components.SectionHeader
import io.towerscope.ui.theme.Palette

/**
 * Shown in place of the app when it isn't unlocked and no trial is running: either before the person
 * has started the free trial (App Store), or after it has ended.
 */
@Composable
fun PaywallScreen(license: LicenseManager) {
    val state by license.state.collectAsState()
    val intro = state.trialNotStarted
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.BgTop, Palette.Bg, Palette.Bg))).safeDrawingPadding(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Rounded.CellTower, null, tint = Palette.Cyan, modifier = Modifier.size(56.dp))
            Text(
                if (intro) "Try TowerScope Live free for 7 days" else "Your free week is over",
                fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, textAlign = TextAlign.Center,
            )
            Text(
                if (intro) "Everything in the app is included during the trial. It lasts 7 days from when you start it. " +
                    "When it ends, the app stops working until you unlock it with a one-time purchase of ${state.displayPrice}. " +
                    "This is not a subscription, and nothing renews or charges you automatically."
                else "Thanks for trying TowerScope Live. Unlock it once for ${state.displayPrice} and keep every feature. " +
                    "No subscription, no ads, and no account.",
                fontSize = 15.sp, color = Palette.TextDim, textAlign = TextAlign.Center, lineHeight = 21.sp,
            )
            GlassCard(Modifier.fillMaxWidth(), accent = Palette.Cyan) { UnlockActions(license, state, offerTrial = intro) }
            Text(
                "Already bought it? Tap Restore purchase while signed in to the same ${license.accountName}. ${license.trialNote}",
                fontSize = 12.sp, color = Palette.TextFaint, textAlign = TextAlign.Center, lineHeight = 17.sp,
            )
        }
    }
}

/** Trial status and the unlock buttons, for the Settings screen. */
@Composable
fun UnlockCard(license: LicenseManager, nowMs: Long) {
    val state by license.state.collectAsState()
    GlassCard(Modifier.fillMaxWidth(), accent = if (state.unlocked) null else Palette.Cyan) {
        SectionHeader("TowerScope Live unlock")
        when {
            state.unlocked -> Text("Unlocked. Thank you for supporting TowerScope Live.", fontSize = 13.sp, color = Palette.Green)
            state.trialActive(nowMs) -> {
                val days = state.trialDaysLeft(nowMs)
                Text(
                    "Free trial: $days ${if (days == 1) "day" else "days"} left. After that, a one-time ${state.displayPrice} " +
                        "unlock keeps everything. Not a subscription. ${license.trialNote}",
                    fontSize = 13.sp, color = Palette.TextDim, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                UnlockActions(license, state, offerTrial = false)
            }
            else -> UnlockActions(license, state, offerTrial = state.trialNotStarted)
        }
    }
}

@Composable
private fun UnlockActions(license: LicenseManager, state: LicenseState, offerTrial: Boolean) {
    state.message?.let {
        Text(it, fontSize = 13.sp, color = Palette.Amber, lineHeight = 18.sp)
        Spacer(Modifier.height(12.dp))
    }
    if (offerTrial) {
        Button(
            onClick = { license.startTrial() },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Cyan, contentColor = Palette.Bg),
        ) { Text("Start my free 7-day trial", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(10.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val unlock = @Composable {
            Icon(Icons.Rounded.LockOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Unlock for ${state.displayPrice}", fontWeight = FontWeight.SemiBold)
        }
        if (offerTrial) OutlinedButton(onClick = { license.purchase() }, enabled = !state.busy) { unlock() }
        else Button(
            onClick = { license.purchase() },
            enabled = !state.busy,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Cyan, contentColor = Palette.Bg),
        ) { unlock() }
        OutlinedButton(onClick = { license.restore() }, enabled = !state.busy) {
            Text(if (state.billingReady) "Restore purchase" else "Retry")
        }
        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Palette.Cyan, strokeWidth = 2.dp)
    }
}
