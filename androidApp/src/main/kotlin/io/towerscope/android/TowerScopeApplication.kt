package io.towerscope.android

import android.app.Application
import io.towerscope.AppController
import io.towerscope.platform.AndroidPlatform
import io.towerscope.ui.Navigator

class TowerScopeApplication : Application() {
    val platform by lazy { AndroidPlatform(this) }
    val controller by lazy { AppController(platform) }
    val navigator by lazy { Navigator() }
}
