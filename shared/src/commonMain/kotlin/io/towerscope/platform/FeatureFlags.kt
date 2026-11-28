package io.towerscope.platform

/**
 * What this build of the app may do. Set once at startup, before anything reads it: the Android app sets
 * it from its product flavor (play or foss), and the iPhone app from MainViewController (App Store edition).
 */
data class FeatureFlags(
    val isPlayEdition: Boolean = false,
    /** Shown in Settings, e.g. "Google Play", "Open source" or "App Store". */
    val editionLabel: String = "Open source",
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
    /** Seven-day trial, then a one-time unlock through Google Play (Android). The App Store build uses StoreKit instead. */
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
