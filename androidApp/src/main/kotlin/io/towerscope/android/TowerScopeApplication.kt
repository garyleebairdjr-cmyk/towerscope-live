package io.towerscope.android

import android.app.Application
import io.towerscope.AppController
import io.towerscope.platform.AndroidPlatform
import io.towerscope.platform.FeatureFlags
import io.towerscope.ui.Navigator

class TowerScopeApplication : Application() {
    val platform by lazy { AndroidPlatform(this) }
    val controller by lazy { AppController(platform) }
    val navigator by lazy { Navigator() }

    override fun onCreate() {
        super.onCreate()
        // Before anything reads the flags (the controller is created lazily, after this).
        FeatureFlags.current = FeatureFlags(
            isPlayEdition = BuildConfig.IS_PLAY_EDITION,
            allowPreciseLocation = BuildConfig.ALLOW_PRECISE_LOCATION,
            allowBackgroundLocation = BuildConfig.ALLOW_BACKGROUND_LOCATION,
            allowHistoryLogging = BuildConfig.ALLOW_HISTORY_LOGGING,
            allowHistoryExport = BuildConfig.ALLOW_HISTORY_EXPORT,
            allowPlayBilling = BuildConfig.ALLOW_PLAY_BILLING,
        )
        Edition.install(this)
    }
}
