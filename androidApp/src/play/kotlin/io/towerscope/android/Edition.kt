package io.towerscope.android

import androidx.activity.ComponentActivity

/** Play edition: a 7-day trial, then a one-time unlock through Google Play Billing. */
object Edition : EditionHooks {
    private var license: PlayLicenseManager? = null

    override fun install(app: TowerScopeApplication) {
        license = PlayLicenseManager(app, app.platform.store, app.platform::nowMs).also { app.platform.license = it }
    }

    // One Billing connection while the app is on screen.
    override fun onStart(activity: ComponentActivity) { license?.connect(activity) }

    override fun onStop(activity: ComponentActivity) { license?.disconnect() }
}
