package io.towerscope.platform

/**
 * What this build of the app may do. The Android app sets [current] once at startup from its product
 * flavor (play or foss), before anything reads it. Other platforms keep the defaults.
 */
data class FeatureFlags(
    val isPlayEdition: Boolean = false,
    /**
     * Precise location may be requested. Android only reveals cell IDs to apps holding precise location,
     * so both Android editions keep it (foreground only). When false, approximate location counts as granted.
     */
    val allowPreciseLocation: Boolean = true,
    /** Opt-in recording while the app is closed, shown with a notification. */
    val allowBackgroundLocation: Boolean = false,
    /** Long-term on-phone history of readings, with a chosen location precision. */
    val allowHistoryLogging: Boolean = true,
    /** Share or save the history as a file. */
    val allowHistoryExport: Boolean = false,
    /** Seven-day trial, then a one-time unlock through Google Play. */
    val allowPlayBilling: Boolean = false,
) {
    val isFossEdition: Boolean get() = !isPlayEdition

    companion object {
        var current: FeatureFlags = FeatureFlags()
    }
}

/** Keeps the app recording while it's closed. Only offered where the platform supports it. */
interface BackgroundRecorder {
    fun start()
    fun stop()
}
