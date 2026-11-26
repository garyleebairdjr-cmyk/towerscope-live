package io.towerscope

import androidx.compose.ui.window.ComposeUIViewController
import io.towerscope.platform.IosPlatform
import io.towerscope.ui.Navigator
import io.towerscope.ui.TowerScopeApp
import platform.UIKit.UIViewController

private val platform by lazy { IosPlatform() }
private val controller by lazy { AppController(platform) }
private val navigator by lazy { Navigator() }

/** Entry point called from Swift: `MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController = ComposeUIViewController { TowerScopeApp(controller, navigator) }
