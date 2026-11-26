package io.towerscope.calc

import io.towerscope.data.AppSettings
import io.towerscope.data.Environment
import io.towerscope.model.CellIdentity
import io.towerscope.model.CellObservation
import io.towerscope.model.ConnectionRole
import io.towerscope.model.GeoFix
import io.towerscope.model.RadioType
import io.towerscope.model.SignalMetrics
import io.towerscope.model.TowerLocation
import io.towerscope.model.TowerLookup
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CalcTest {
    private fun near(expected: Double, actual: Double, tol: Double) =
        assertTrue(abs(expected - actual) <= tol, "expected $expected ± $tol but was $actual")

    @Test
    fun haversineKnownDistance() {
        // London (51.5074, -0.1278) → Paris (48.8566, 2.3522) ≈ 343.5 km
        near(343_500.0, Geo.distanceM(51.5074, -0.1278, 48.8566, 2.3522), 1_500.0)
        near(0.0, Geo.distanceM(10.0, 10.0, 10.0, 10.0), 0.001)
    }

    @Test
    fun bearingCardinals() {
        near(0.0, Geo.bearingDeg(0.0, 0.0, 1.0, 0.0), 0.01)
        near(90.0, Geo.bearingDeg(0.0, 0.0, 0.0, 1.0), 0.01)
        near(180.0, Geo.bearingDeg(1.0, 0.0, 0.0, 0.0), 0.01)
        near(270.0, Geo.bearingDeg(0.0, 1.0, 0.0, 0.0), 0.01)
        assertEquals("NE", Geo.cardinal(45.0))
        assertEquals("N", Geo.cardinal(359.0))
    }

    @Test
    fun lteEarfcn() {
        val b3 = assertNotNull(Bands.lte(1300)) // Band 3
        assertEquals("B3", b3.name)
        near(1815.0, b3.dlMHz, 0.01)
        near(1720.0, b3.ulMHz!!, 0.01)
        val b66 = assertNotNull(Bands.lte(66786))
        assertEquals("B66", b66.name)
        near(2145.0, b66.dlMHz, 0.01)
        near(1745.0, b66.ulMHz!!, 0.01)
        val b13 = assertNotNull(Bands.lte(5230))
        near(751.0, b13.dlMHz, 0.01)
        near(782.0, b13.ulMHz!!, 0.01) // reverse duplex
        val b41 = assertNotNull(Bands.lte(40072))
        assertEquals(Duplex.TDD, b41.duplex)
    }

    @Test
    fun nrArfcn() {
        near(3500.01, Bands.nrArfcnToMHz(633_334), 0.001)
        near(28_000.08, Bands.nrArfcnToMHz(2_079_167), 0.1)
        assertEquals("n77", Bands.nr(650_000, mcc = 310)!!.name) // 3750 MHz in the US
        assertEquals("n78", Bands.nr(633_334, mcc = 262)!!.name) // 3500 MHz in Germany
        assertEquals("n41", Bands.nr(520_110)!!.name) // 2600.55 MHz
        assertEquals("n41", Bands.nr(530_000, mcc = 310)!!.name) // 2650 MHz, US
        assertEquals("n7", Bands.nr(530_000, mcc = 234)!!.name) // 2650 MHz, UK
        assertEquals("n41", Bands.nr(530_000, listOf(41), mcc = 234)!!.name) // modem-reported band wins
        val bogus = Bands.nr(9000, listOf(41))!! // invalid channel, band reported
        assertEquals("n41", bogus.name)
        near(2593.0, bogus.dlMHz, 0.01)
        kotlin.test.assertNull(Bands.nr(9000))
        val n71 = Bands.nr(126_400)!! // 632 MHz
        assertEquals("n71", n71.name)
        near(678.0, n71.ulMHz!!, 0.01)
    }

    @Test
    fun umtsAndGsm() {
        val u = assertNotNull(Bands.umts(10700))
        assertEquals("B1", u.name)
        near(2140.0, u.dlMHz, 0.01)
        near(1950.0, u.ulMHz!!, 0.01)
        val g = assertNotNull(Bands.gsm(62))
        near(947.4, g.dlMHz, 0.01)
        assertEquals("PCS 1900", Bands.gsm(600, mcc = 310)!!.name)
        assertEquals("DCS 1800", Bands.gsm(600, mcc = 234)!!.name)
    }

    @Test
    fun hataInversionRoundTrips() {
        for (env in Environment.entries) {
            for (f in listOf(700.0, 1900.0, 2600.0)) {
                val pl = RfModels.hataPathLossDb(2.5, f, 30.0, 1.5, env)
                near(2.5, RfModels.hataDistanceKm(pl, f, 30.0, 1.5, env), 1e-6)
            }
        }
        // Reference value: Okumura-Hata urban, 900 MHz, hb 30 m, hm 1.5 m, 1 km ≈ 126.4 dB
        near(126.4, RfModels.hataPathLossDb(1.0, 900.0, 30.0, 1.5, Environment.URBAN), 0.5)
    }

    @Test
    fun freeSpaceAndTimingAdvance() {
        near(91.5, RfModels.freeSpacePathLossDb(1.0, 900.0), 0.2)
        near(5.0, RfModels.freeSpaceDistanceKm(RfModels.freeSpacePathLossDb(5.0, 2100.0), 2100.0), 1e-9)
        near(78.07, RfModels.timingAdvanceStepM(RadioType.LTE)!!, 0.05)
        near(553.5, RfModels.timingAdvanceStepM(RadioType.GSM)!!, 1.0)
        near(0.333, RfModels.wavelengthM(900.0), 0.001)
    }

    @Test
    fun throughputIsMonotonicInSinr() {
        val band = Bands.lte(1300)
        fun dl(sinr: Double) = Throughput.estimate(RadioType.LTE, band, SignalMetrics(rsrp = -90, sinr = sinr), 20_000, emptyList(), 120.0, 23.0)!!.dlMbps
        assertTrue(dl(-15.0) == 0.0)
        assertTrue(dl(0.0) < dl(10.0))
        assertTrue(dl(10.0) < dl(25.0))
        val peak = Throughput.estimate(RadioType.LTE, band, SignalMetrics(sinr = 30.0), 20_000, emptyList(), 100.0, 23.0)!!
        // 20 MHz 2x2 256-QAM LTE tops out near 200 Mbps.
        near(200.0, peak.dlPeakMbps, 20.0)
        assertTrue(peak.dlMbps <= peak.dlPeakMbps)
        assertTrue(peak.ulMbps in 1.0..peak.ulPeakMbps)
    }

    @Test
    fun uplinkFallsWithPathLoss() {
        val band = Bands.lte(66786)
        fun ul(pl: Double) = Throughput.estimate(RadioType.LTE, band, SignalMetrics(sinr = 15.0), 20_000, emptyList(), pl, 23.0)!!.ulMbps
        assertTrue(ul(110.0) > ul(130.0))
        assertTrue(ul(130.0) > ul(150.0))
    }

    @Test
    fun analyzerPrefersGpsDistanceAndDecodesEci() {
        val eci = 0x01A2B3CL // eNB 6699, sector 60
        val obs = CellObservation(
            CellIdentity(RadioType.LTE, 310, 260, 12345, eci, pci = 301, channel = 66786, bandwidthKhz = 20_000),
            SignalMetrics(rsrp = -95, rsrq = -11, sinr = 12.0, timingAdvance = 10),
            ConnectionRole.PRIMARY, 0,
        )
        val tower = TowerLocation(47.6205, -122.3493, 800.0, 50, "OpenCelliD")
        val fix = GeoFix(47.6100, -122.3493, 5.0, timestampMs = 0)
        val a = Analyzer.analyze(obs, TowerLookup.Found(tower), fix, AppSettings(), null)
        assertEquals(io.towerscope.calc.DistanceMethod.GPS_DATABASE, a.bestDistance!!.method)
        near(1167.0, a.bestDistance.meters, 10.0)
        near(0.0, a.bearingDeg!!, 0.5)
        val ta = a.distances.first { it.method == DistanceMethod.TIMING_ADVANCE }
        near(10 * 78.07 + 39.0, ta.meters, 1.0)
        assertEquals("${eci shr 8}", a.decoded.first { it.label == "eNB ID" }.value)
        assertEquals("${eci and 0xFF}", a.decoded.first { it.label == "Sector / cell" }.value)
        near(125.0, a.pathLossDb!!, 0.01) // 30 dBm EIRP − (−95)
        assertNotNull(a.throughput)
        assertTrue(a.score!! in 30..80)
    }

    @Test
    fun formatting() {
        assertEquals("3.14", 3.14159.fmt(2))
        assertEquals("-2.5", (-2.5).fmt(1))
        assertEquals("0", (-0.0001).fmt(0))
        assertEquals("1.23 km", Fmt.distance(1234.0, io.towerscope.data.UnitSystem.METRIC))
        assertEquals("850 m", Fmt.distance(850.0, io.towerscope.data.UnitSystem.METRIC))
        assertEquals("125 Mbps", Fmt.speed(125.4))
        assertEquals("1.50 Gbps", Fmt.speed(1500.0))
    }

    @Test
    fun milesConversion() {
        val imp = io.towerscope.data.UnitSystem.IMPERIAL
        assertEquals("1.00 mi", Fmt.miles(1609.344))
        assertEquals("0.024 mi", Fmt.miles(39.0))
        assertEquals("128 ft", Fmt.distance(39.0, imp))
        assertEquals("3.11 mi", Fmt.distance(5000.0, imp))
        assertEquals("0.039 km", Fmt.distanceAlt(39.0, imp))
        assertEquals("60 mph", Fmt.groundSpeed(26.8224, imp))
        assertEquals(imp, io.towerscope.data.UnitSystem.defaultFor("US"))
        assertEquals(io.towerscope.data.UnitSystem.METRIC, io.towerscope.data.UnitSystem.defaultFor("DE"))
    }
}
