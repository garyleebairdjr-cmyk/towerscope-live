package io.towerscope.data

import io.towerscope.calc.DayPeriod
import io.towerscope.calc.Insights
import io.towerscope.calc.TrendKind
import io.towerscope.model.RadioType
import io.towerscope.platform.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryTest {
    private val day = 24L * 3600 * 1000
    private val now = 1_800_000_000_000L
    private val home = 47.6100 to -122.3400
    private val work = 47.6500 to -122.3000 // ~5 km away
    private val phone = "samsung SM-A165M"

    private fun sig(t: Long, dbm: Int, at: Pair<Double, Double>? = home, hour: Int = 12, cell: String? = "LTE:310:260:11544:21527043", device: String? = phone) =
        SignalRecord(t, hour, at?.first, at?.second, cell, RadioType.LTE, dbm, 10.0, -11, device)

    private fun spd(t: Long, down: Double, at: Pair<Double, Double>? = home, hour: Int = 12, device: String? = phone) =
        SpeedRecord(t, hour, at?.first, at?.second, "LTE:310:260:11544:21527043", down, 10.0, 30.0, cellular = true, device = device)

    private class MemFile : HistoryFile {
        var text = ""
        override fun readAll() = text.ifEmpty { null }
        override fun append(text: String) { this.text += text }
        override fun replace(text: String) { this.text = text }
        override fun clear() { text = "" }
    }

    @Test
    fun codecRoundTripsBothKindsIncludingDevice() {
        val a = sig(now, -95)
        val b = spd(now, 42.5)
        assertEquals(a, HistoryCodec.decode(HistoryCodec.encode(a)))
        assertEquals(b, HistoryCodec.decode(HistoryCodec.encode(b)))
    }

    @Test
    fun codecToleratesMissingLocationDeviceAndJunk() {
        val r = sig(now, -100, at = null, device = null)
        assertEquals(r, HistoryCodec.decode(HistoryCodec.encode(r)))
        assertNull(HistoryCodec.decode("garbage"))
        assertNull(HistoryCodec.decode("X\t1\t2"))
    }

    @Test
    fun storeDropsRecordsOlderThan90Days() {
        val f = MemFile()
        val store = HistoryStore(f)
        store.append(sig(now - 100 * day, -90))
        store.append(sig(now - day, -91))
        val loaded = store.load(now)
        assertEquals(listOf(-91), loaded.map { (it as SignalRecord).signalDbm })
        assertEquals(1, f.text.trim().lines().size) // the file was rewritten without the old record
    }

    @Test
    fun samplerKeepsAboutOnePerMinuteUnlessCellOrPlaceChanges() {
        val s = SignalSampler()
        assertTrue(s.shouldRecord(sig(now, -90)))
        assertFalse(s.shouldRecord(sig(now + 20_000, -91)))
        assertTrue(s.shouldRecord(sig(now + 25_000, -91, cell = "LTE:310:260:11544:999"))) // new cell
        assertFalse(s.shouldRecord(sig(now + 30_000, -91, cell = "LTE:310:260:11544:999"))) // too soon after
        assertTrue(s.shouldRecord(sig(now + 45_000, -91, at = 47.6110 to -122.3400, cell = "LTE:310:260:11544:999"))) // moved ~110 m
        assertTrue(s.shouldRecord(sig(now + 110_000, -91, at = 47.6110 to -122.3400, cell = "LTE:310:260:11544:999"))) // a minute later
    }

    @Test
    fun locationLevels() {
        assertEquals(47.611234 to -122.345678, HistoryLocation.EXACT.apply(47.611234, -122.345678))
        assertEquals(47.611 to -122.346, HistoryLocation.APPROXIMATE.apply(47.611234, -122.345678))
        assertEquals(null to null, HistoryLocation.NONE.apply(47.611234, -122.345678))
        val r = sig(now, -90).withLocation(HistoryLocation.NONE)
        assertNull(r.lat)
    }

    @Test
    fun keyValueFallbackKeepsWholeLinesWhenCapped() {
        val map = mutableMapOf<String, String>()
        val f = KeyValueHistoryFile(object : KeyValueStore {
            override fun getString(key: String) = map[key]
            override fun putString(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
        })
        repeat(6000) { f.append(HistoryCodec.encode(sig(now + it, -90)) + "\n") }
        val lines = f.readAll()!!.trim().lines()
        assertTrue(lines.size in 1000..5999)
        assertTrue(lines.all { HistoryCodec.decode(it) != null })
    }

    @Test
    fun placesRankBestSignalFirstAndMarkHere() {
        val h = List(12) { sig(now - it * 60_000L, -110) } + List(12) { sig(now - it * 60_000L, -85, at = work) }
        val places = Insights.report(h, home.first, home.second, now).places
        assertEquals(2, places.size)
        assertEquals(-85.0, places[0].medianSignalDbm)
        assertFalse(places[0].isHere)
        assertTrue(places[1].isHere)
    }

    @Test
    fun timeOfDayFindsEveningSlowdown() {
        val h = List(4) { spd(now - it * day, 80.0, hour = 9) } + List(4) { spd(now - it * day, 20.0, hour = 20) }
        val t = Insights.report(h, home.first, home.second, now).timeOfDay
        assertNotNull(t)
        assertTrue(t.local)
        assertTrue(t.finding!!.contains("75% lower in the ${DayPeriod.EVENING.label}"), t.finding)
    }

    @Test
    fun noFindingFromTooLittleData() {
        val h = listOf(spd(now, 80.0, hour = 9), spd(now, 10.0, hour = 20))
        val r = Insights.report(h, home.first, home.second, now)
        assertNull(r.timeOfDay)
        assertTrue(r.alerts.isEmpty())
    }

    @Test
    fun towersNeedEnoughReadings() {
        val h = List(5) { sig(now - it, -90) } + List(3) { sig(now - it, -80, cell = "LTE:310:260:1:2") }
        val towers = Insights.report(h, null, null, now).towers
        assertEquals(1, towers.size)
        assertEquals(5, towers[0].signalSamples)
    }

    @Test
    fun alertWhenThisWeekIsClearlyWorse() {
        val usual = List(40) { sig(now - (10 + it % 60) * day, -88) }
        val recent = List(25) { sig(now - (it % 6) * day, -98) }
        val alerts = Insights.report(usual + recent, home.first, home.second, now, currentDevice = phone).alerts
        assertEquals(TrendKind.SIGNAL, alerts.single().kind)
        assertTrue(alerts.single().local)
    }

    @Test
    fun anotherPhonesReadingsDontTriggerAlerts() {
        val usual = List(40) { sig(now - (10 + it % 60) * day, -88, device = "Google Pixel 8") }
        val recent = List(25) { sig(now - (it % 6) * day, -98) }
        val r = Insights.report(usual + recent, home.first, home.second, now, currentDevice = phone)
        assertTrue(r.alerts.isEmpty())
        assertEquals(listOf("Google Pixel 8" to 40, phone to 25), r.devices)
    }
}
