package io.towerscope.android

import androidx.activity.ComponentActivity

/**
 * What differs between the editions on Android. Each flavor's source set defines `object Edition`
 * implementing this (src/play and src/foss), so Play Billing and the background service only exist in
 * the build that uses them.
 */
interface EditionHooks {
    /** Once, when the process starts, after the feature flags are set. */
    fun install(app: TowerScopeApplication)

    /** From MainActivity.onCreate, before it is started (activity-result launchers must be registered here). */
    fun onCreate(activity: ComponentActivity) {}

    fun onStart(activity: ComponentActivity) {}

    fun onStop(activity: ComponentActivity) {}
}
