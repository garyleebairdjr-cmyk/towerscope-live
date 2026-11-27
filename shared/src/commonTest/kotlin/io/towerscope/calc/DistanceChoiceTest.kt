package io.towerscope.calc

import io.towerscope.data.AppSettings
import io.towerscope.model.CellIdentity
import io.towerscope.model.CellObservation
import io.towerscope.model.ConnectionRole
import io.towerscope.model.DatabaseTower
import io.towerscope.model.GeoFix
import io.towerscope.model.RadioType
import io.towerscope.model.SignalMetrics
import io.towerscope.model.TowerLocation
import io.towerscope.model.TowerLookup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Which distance becomes the headline, and the same-tower fallback when a sector isn't in the database. */
class DistanceChoiceTest {
    private val enb = 84090L
    private fun eci(sector: Int) = enb * 256 + sector
    private val fix = GeoFix(47.6100, -122.3493, 10.0, timestampMs = 0)

    private fun serving(sector: Int, ta: Int? = 0) = CellObservation(
        CellIdentity(RadioType.LTE, 310, 260, 11544, eci(sector), pci = 398, channel = 1575),
        SignalMetrics(rsrp = -94, timingAdvance = ta),
        ConnectionRole.PRIMARY, 0,
    )

    private fun dbTower(sector: Int, samples: Int, lat: Double = 47.6132) = DatabaseTower(
        CellIdentity(RadioType.LTE, 310, 260, 11622, eci(sector)),
        TowerLocation(lat, -122.3493, rangeM = 1165.0, samples = samples, source = "OpenCelliD"),
    )

    @Test
    fun weakDatabasePositionLosesToTimingAdvance() {
        val weak = TowerLocation(47.6132, -122.3493, rangeM = 1165.0, samples = 1, source = "OpenCelliD")
        val a = Analyzer.analyze(serving(3), TowerLookup.Found(weak), fix, AppSettings(), null)
        assertEquals(DistanceMethod.TIMING_ADVANCE, a.bestDistance!!.method)
        // The database distance is still listed, and still gives the direction.
        assertNotNull(a.distances.firstOrNull { it.method == DistanceMethod.GPS_DATABASE })
        assertNotNull(a.bearingDeg)
    }

    @Test
    fun wellSurveyedPositionStillWins() {
        val good = TowerLocation(47.6132, -122.3493, rangeM = 400.0, samples = 40, source = "OpenCelliD")
        val a = Analyzer.analyze(serving(3), TowerLookup.Found(good), fix, AppSettings(), null)
        assertEquals(DistanceMethod.GPS_DATABASE, a.bestDistance!!.method)
    }

    @Test
    fun hugeRangeIsWeakEvenWithManySamples() {
        val vague = TowerLocation(47.6132, -122.3493, rangeM = 10_758.0, samples = 30, source = "OpenCelliD")
        val a = Analyzer.analyze(serving(3), TowerLookup.Found(vague), fix, AppSettings(), null)
        assertEquals(DistanceMethod.TIMING_ADVANCE, a.bestDistance!!.method)
    }

    @Test
    fun weakPositionIsUsedWhenThereIsNoTimingAdvance() {
        val weak = TowerLocation(47.6132, -122.3493, rangeM = 1165.0, samples = 1, source = "OpenCelliD")
        val a = Analyzer.analyze(serving(3, ta = null), TowerLookup.Found(weak), fix, AppSettings(), null)
        assertEquals(DistanceMethod.GPS_DATABASE, a.bestDistance!!.method)
    }

    @Test
    fun missingSectorFallsBackToAnotherSectorOfTheSameTower() {
        val nearby = listOf(dbTower(6, samples = 1), dbTower(4, samples = 5, lat = 47.6140))
        val a = Analyzer.analyze(serving(3), TowerLookup.NotFound, fix, AppSettings(), null, nearby)
        assertEquals(4, a.tower!!.siblingSector) // the better-surveyed sibling
        assertNotNull(a.bearingDeg)
        val db = a.distances.first { it.method == DistanceMethod.GPS_DATABASE }
        assertEquals(Confidence.LOW, db.confidence)
        assertEquals(DistanceMethod.TIMING_ADVANCE, a.bestDistance!!.method)
    }

    @Test
    fun otherTowersAndOtherNetworksAreNotSiblings() {
        val otherEnb = DatabaseTower(CellIdentity(RadioType.LTE, 310, 260, 11622, (enb + 1) * 256 + 3), TowerLocation(47.6, -122.3, source = "OpenCelliD"))
        val otherNetwork = DatabaseTower(CellIdentity(RadioType.LTE, 310, 410, 11622, eci(6)), TowerLocation(47.6, -122.3, source = "OpenCelliD"))
        val a = Analyzer.analyze(serving(3), TowerLookup.NotFound, fix, AppSettings(), null, listOf(otherEnb, otherNetwork))
        assertNull(a.tower)
        assertNull(a.bearingDeg)
    }

    @Test
    fun foundCellIsNotReplacedBySibling() {
        val exact = TowerLocation(47.6150, -122.3493, rangeM = 300.0, samples = 25, source = "OpenCelliD")
        val a = Analyzer.analyze(serving(3), TowerLookup.Found(exact), fix, AppSettings(), null, listOf(dbTower(6, samples = 99)))
        assertNull(a.tower!!.siblingSector)
        assertEquals(DistanceMethod.GPS_DATABASE, a.bestDistance!!.method)
    }
}
