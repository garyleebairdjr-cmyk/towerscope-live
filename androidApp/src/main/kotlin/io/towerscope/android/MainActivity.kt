package io.towerscope.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.lifecycleScope
import io.towerscope.platform.FeatureFlags
import io.towerscope.ui.TowerScopeApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as TowerScopeApplication

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        app.platform.refreshPermissions(requestedOnce = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val gate = app.platform.permissions
        // Only asked when the person taps Continue on the in-app explanation (PermissionCard), never on launch.
        gate.requester = {
            val flags = FeatureFlags.current
            permissionLauncher.launch(
                buildList {
                    if (flags.allowPreciseLocation) add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    // Network type, 5G status and carrier-aggregation bandwidths; optional.
                    add(Manifest.permission.READ_PHONE_STATE)
                }.toTypedArray(),
            )
        }
        gate.settingsOpener = {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        app.platform.refreshPermissions()

        // System back pops the in-app navigation stack before leaving the app.
        val nav = app.navigator
        val backCallback = object : OnBackPressedCallback(nav.canGoBack) {
            override fun handleOnBackPressed() {
                nav.pop()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
        lifecycleScope.launch {
            snapshotFlow { nav.canGoBack }.collect { backCallback.isEnabled = it }
        }

        Edition.onCreate(this)
        setContent { TowerScopeApp(app.controller, nav) }
    }

    override fun onStart() {
        super.onStart()
        Edition.onStart(this)
    }

    override fun onStop() {
        Edition.onStop(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        app.platform.refreshPermissions()
        app.controller.start()
    }

    override fun onPause() {
        super.onPause()
        app.controller.stop()
    }
}
