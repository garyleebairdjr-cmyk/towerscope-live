package io.towerscope.calc

import io.towerscope.data.SpeedResult
import io.towerscope.model.CellIdentity
import io.towerscope.model.CellObservation
import io.towerscope.model.ConnectionRole
import io.towerscope.model.RadioType
import io.towerscope.model.SignalMetrics
import io.towerscope.model.Transport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticsTest {
    private fun cell(cid: Long, rsrp: Int, role: ConnectionRole = ConnectionRole.PRIMARY, sinr: Double? = 10.0, radio: RadioType = RadioType.LTE) =
        CellObservation(
            CellIdentity(radio, 310, 260, 1, cid, pci = cid.toInt()),
            SignalMetrics(rsrp = rsrp, sinr = sinr), role, 0L,
        )

    private fun input(serving: CellObservation?, now: Long = 1_800_000_000_000, block: DiagnosticInput.() -> DiagnosticInput = { this }) =
        DiagnosticInput(serving, null, null, DeviceConditions(), null, emptyList(), now).block()

    private fun ids(i: DiagnosticInput) = Diagnostics.evaluate(i).map { it.id }

    @Test
    fun goodSignalHasNoAdvice() {
        assertEquals(emptyList(), ids(input(cell(1, -85))))
    }

    @Test
    fun weakSignalSuggestsMoving() {
        val adv = Diagnostics.evaluate(input(cell(1, -114)))
        assertEquals("weak", adv.first().id)
        assertEquals(AdviceAction.FindBestSpot, adv.first().action)
        assertTrue("fair" in ids(input(cell(1, -105))))
    }

    @Test
    fun strongButNoisy() {
        assertTrue("interference" in ids(input(cell(1, -90, sinr = -2.0))))
        assertFalse("interference" in ids(input(cell(1, -90, sinr = null))))
    }

    @Test
    fun neighborMustBeStrongerForAWhile() {
        val watch = NeighborWatch()
        val serving = cell(1, -105)
        val n = cell(2, -95, ConnectionRole.NEIGHBOR)
        assertEquals(0L, watch.update(serving, listOf(n), 0)!!.durationMs)
        val later = watch.update(serving, listOf(n), 31_000)
        assertNotNull(later); assertEquals(10, later.marginDb)
        assertTrue("reselect" in ids(input(serving) { copy(neighbor = later) }))
        // Gap closes: tracking resets.
        assertNull(watch.update(serving, listOf(cell(2, -103, ConnectionRole.NEIGHBOR)), 32_000))
        // A brief spike doesn't trigger advice.
        val brief = watch.update(serving, listOf(n), 33_000)
        assertFalse("reselect" in ids(input(serving) { copy(neighbor = brief) }))
    }

    @Test
    fun weak5gLegAndPowerSave() {
        val nr = cell(9, -118, ConnectionRole.SECONDARY, radio = RadioType.NR)
        val i = input(cell(1, -88)) { copy(nrLeg = nr, conditions = DeviceConditions(powerSaveMode = true)) }
        val got = ids(i)
        assertTrue("weak5g" in got); assertTrue("powersave" in got)
    }

    @Test
    fun congestionNeedsGoodSignalAndBigGap() {
        val now = 1_800_000_000_000
        val busy = SpeedResult(now - 60_000, 40.0, 5.0, 2.0, 5.0, "LTE", Transport.CELLULAR, predictedDownMbps = 40.0, rsrp = -92)
        assertTrue("congestion" in ids(input(cell(1, -92), now) { copy(recentSpeeds = listOf(busy)) }))
        val wifi = busy.copy(transport = Transport.WIFI)
        assertFalse("congestion" in ids(input(cell(1, -92), now) { copy(recentSpeeds = listOf(wifi)) }))
        val variance = listOf(busy.copy(predictedDownMbps = 3.0), busy.copy(downMbps = 35.0, predictedDownMbps = 3.0))
        assertTrue("variance" in ids(input(cell(1, -92), now) { copy(recentSpeeds = variance) }))
    }

    @Test
    fun sinrPlaceholderDetection() {
        val zeros = (0 until 10).map { 0.0 to -95 - (it % 3) }
        assertTrue(SinrSanity.isPlaceholder(zeros))
        assertFalse(SinrSanity.isPlaceholder(zeros.dropLast(1) + (0.4 to -95)))
        assertFalse(SinrSanity.isPlaceholder((0 until 10).map { 0.0 to -95 })) // RSRP flat: can't tell
    }

    @Test
    fun securityPatchAge() {
        val now = 1_764_000_000_000L // 2025-11-24
        assertEquals(0L, Diagnostics.patchAgeDays("2025-11-24", now))
        assertEquals(365L, Diagnostics.patchAgeDays("2024-11-24", now))
        assertNull(Diagnostics.patchAgeDays("bogus", now))
    }
}
