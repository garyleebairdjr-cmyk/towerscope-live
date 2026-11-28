package io.towerscope

import androidx.compose.ui.window.ComposeUIViewController
import io.towerscope.license.IosEdition
import io.towerscope.platform.FeatureFlags
import io.towerscope.platform.IosPlatform
import io.towerscope.ui.Navigator
import io.towerscope.ui.TowerScopeApp
import platform.UIKit.UIViewController

private val platform by lazy {
    // The iPhone App Store edition matches the Google Play edition: location only while the app is open,
    // no long-term history, no export, no background recording, and a 7-day trial then a one-time unlock.
    FeatureFlags.current = FeatureFlags(
        isPlayEdition = false,
        editionLabel = "App Store",
        allowPreciseLocation = true,
        allowBackgroundLocation = false,
        allowHistoryLogging = false,
        allowHistoryExport = false,
        allowPlayBilling = false, // StoreKit, not Play Billing; the license below is what turns the paywall on
    )
    IosPlatform().also { it.license = IosEdition.license }
}
private val controller by lazy { AppController(platform) }
private val navigator by lazy { Navigator() }

/** Entry point called from Swift: `MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController = ComposeUIViewController { TowerScopeApp(controller, navigator) }
