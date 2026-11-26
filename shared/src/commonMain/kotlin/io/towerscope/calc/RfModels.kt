package io.towerscope.calc

import io.towerscope.data.Environment
import io.towerscope.model.RadioType
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** Radio-propagation and link-budget formulas. Frequencies in MHz, distances in km unless noted. */
object RfModels {
    const val SPEED_OF_LIGHT = 299_792_458.0

    /** Thermal noise density at 290 K. */
    const val THERMAL_NOISE_DBM_PER_HZ = -174.0

    fun wavelengthM(fMHz: Double): Double = SPEED_OF_LIGHT / (fMHz * 1e6)

    fun freeSpacePathLossDb(dKm: Double, fMHz: Double): Double = 20 * log10(dKm) + 20 * log10(fMHz) + 32.44

    fun freeSpaceDistanceKm(pathLossDb: Double, fMHz: Double): Double =
        10.0.pow((pathLossDb - 32.44 - 20 * log10(fMHz)) / 20.0)

    /** Hata mobile-antenna correction for small/medium cities. */
    private fun mobileCorrection(fMHz: Double, hm: Double) =
        (1.1 * log10(fMHz) - 0.7) * hm - (1.56 * log10(fMHz) - 0.8)

    /**
     * Okumura–Hata (150–1500 MHz) / COST-231 Hata (1500–2000 MHz, extrapolated above) urban
     * path loss split into L = A + B·log10(d) so it can be inverted.
     */
    private fun hataTerms(fMHz: Double, hb: Double, hm: Double, env: Environment): Pair<Double, Double> {
        val f = fMHz.coerceIn(150.0, 6000.0)
        val lf = log10(f)
        val lhb = log10(hb.coerceIn(10.0, 300.0))
        var a = if (f <= 1500) {
            69.55 + 26.16 * lf - 13.82 * lhb - mobileCorrection(f, hm)
        } else {
            46.3 + 33.9 * lf - 13.82 * lhb - mobileCorrection(f, hm) + if (env == Environment.URBAN) 3.0 else 0.0
        }
        a += when (env) {
            Environment.URBAN -> 0.0
            Environment.SUBURBAN -> -2 * log10(f / 28.0).pow(2) - 5.4
            Environment.RURAL -> -4.78 * lf.pow(2) + 18.33 * lf - 40.94
        }
        val b = 44.9 - 6.55 * lhb
        return a to b
    }

    fun hataPathLossDb(dKm: Double, fMHz: Double, hb: Double, hm: Double, env: Environment): Double {
        val (a, b) = hataTerms(fMHz, hb, hm, env)
        return a + b * log10(dKm)
    }

    fun hataDistanceKm(pathLossDb: Double, fMHz: Double, hb: Double, hm: Double, env: Environment): Double {
        val (a, b) = hataTerms(fMHz, hb, hm, env)
        return 10.0.pow((pathLossDb - a) / b)
    }

    /**
     * Effective radiated power used to back out path loss from a received measurement.
     * [referenceEirpDbm] is per LTE/NR resource element; 3G/2G metrics are measured over the
     * whole channel so their transmit reference is higher.
     */
    fun transmitReferenceDbm(radio: RadioType, referenceEirpDbm: Double): Double = when (radio) {
        RadioType.LTE, RadioType.NR -> referenceEirpDbm
        RadioType.WCDMA, RadioType.TDSCDMA -> referenceEirpDbm + 15.0
        RadioType.GSM -> referenceEirpDbm + 25.0
        RadioType.CDMA -> referenceEirpDbm + 20.0
        RadioType.UNKNOWN -> referenceEirpDbm
    }

    /** Distance per timing-advance step (one way), metres. */
    fun timingAdvanceStepM(radio: RadioType): Double? = when (radio) {
        RadioType.LTE -> 16 * (1 / 30.72e6) * SPEED_OF_LIGHT / 2 // 16·Ts ≈ 78.07 m
        RadioType.GSM -> (48.0 / 13e6) * SPEED_OF_LIGHT / 2 // one bit period ≈ 553.5 m
        else -> null
    }

    fun timingAdvanceMicrosToM(us: Int): Double = us * 1e-6 * SPEED_OF_LIGHT / 2

    /** Radio horizon with 4/3 earth refraction, km. Heights in metres. */
    fun radioHorizonKm(h1: Double, h2: Double): Double = 4.12 * (sqrt(h1) + sqrt(h2))

    /** Radius of the first Fresnel zone at the path midpoint, metres. */
    fun fresnelRadiusM(dKm: Double, fMHz: Double): Double = 17.32 * sqrt(dKm / (4 * fMHz / 1000.0))

    fun elevationAngleDeg(heightDiffM: Double, distanceM: Double): Double =
        atan(heightDiffM / distanceM.coerceAtLeast(1.0)) * 180.0 / PI

    fun dopplerHz(speedMps: Double, fMHz: Double): Double = speedMps * fMHz * 1e6 / SPEED_OF_LIGHT

    fun noiseFloorDbm(bandwidthHz: Double, noiseFigureDb: Double): Double =
        THERMAL_NOISE_DBM_PER_HZ + 10 * log10(bandwidthHz) + noiseFigureDb

    fun dbToLinear(db: Double) = 10.0.pow(db / 10.0)
    fun linearToDb(x: Double) = 10 * log10(x)
}
