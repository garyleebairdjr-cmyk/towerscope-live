package io.towerscope.calc

import io.towerscope.model.RadioType

enum class Duplex(val label: String) { FDD("FDD"), TDD("TDD"), SDL("Supplemental DL") }

data class BandInfo(
    val radio: RadioType,
    /** Display name, e.g. "B66" or "n41". */
    val name: String,
    val number: Int,
    val dlMHz: Double,
    val ulMHz: Double?,
    val duplex: Duplex,
    val commonName: String?,
) {
    val frequencyClass: String
        get() = when {
            dlMHz < 1000 -> "Low-band"
            dlMHz < 2700 -> "Mid-band"
            dlMHz < 7200 -> "Upper mid-band"
            else -> "mmWave"
        }
}

/**
 * Channel-number to band/frequency conversion per 3GPP TS 36.101 (LTE), TS 38.104 (NR),
 * TS 25.101 (UMTS) and TS 45.005 (GSM).
 */
object Bands {
    private class LteBand(
        val band: Int, val dlLow: Double, val nOffDl: Int, val nDlMax: Int,
        val ulLow: Double?, val nOffUl: Int?, val duplex: Duplex, val name: String,
    )

    private val lte = listOf(
        LteBand(1, 2110.0, 0, 599, 1920.0, 18000, Duplex.FDD, "2100 IMT"),
        LteBand(2, 1930.0, 600, 1199, 1850.0, 18600, Duplex.FDD, "1900 PCS"),
        LteBand(3, 1805.0, 1200, 1949, 1710.0, 19200, Duplex.FDD, "1800 DCS"),
        LteBand(4, 2110.0, 1950, 2399, 1710.0, 19950, Duplex.FDD, "AWS-1"),
        LteBand(5, 869.0, 2400, 2649, 824.0, 20400, Duplex.FDD, "850 CLR"),
        LteBand(7, 2620.0, 2750, 3449, 2500.0, 20750, Duplex.FDD, "2600 IMT-E"),
        LteBand(8, 925.0, 3450, 3799, 880.0, 21450, Duplex.FDD, "900 E-GSM"),
        LteBand(11, 1475.9, 4750, 4949, 1427.9, 22750, Duplex.FDD, "1500 Lower"),
        LteBand(12, 729.0, 5010, 5179, 699.0, 23010, Duplex.FDD, "700 a"),
        LteBand(13, 746.0, 5180, 5279, 777.0, 23180, Duplex.FDD, "700 c"),
        LteBand(14, 758.0, 5280, 5379, 788.0, 23280, Duplex.FDD, "700 PS"),
        LteBand(17, 734.0, 5730, 5849, 704.0, 23730, Duplex.FDD, "700 b"),
        LteBand(18, 860.0, 5850, 5999, 815.0, 23850, Duplex.FDD, "800 Lower"),
        LteBand(19, 875.0, 6000, 6149, 830.0, 24000, Duplex.FDD, "800 Upper"),
        LteBand(20, 791.0, 6150, 6449, 832.0, 24150, Duplex.FDD, "800 DD"),
        LteBand(21, 1495.9, 6450, 6599, 1447.9, 24450, Duplex.FDD, "1500 Upper"),
        LteBand(25, 1930.0, 8040, 8689, 1850.0, 26040, Duplex.FDD, "1900+ G"),
        LteBand(26, 859.0, 8690, 9039, 814.0, 26690, Duplex.FDD, "850+ ESMR"),
        LteBand(28, 758.0, 9210, 9659, 703.0, 27210, Duplex.FDD, "700 APT"),
        LteBand(29, 717.0, 9660, 9769, null, null, Duplex.SDL, "700 d"),
        LteBand(30, 2350.0, 9770, 9869, 2305.0, 27660, Duplex.FDD, "2300 WCS"),
        LteBand(32, 1452.0, 9920, 10359, null, null, Duplex.SDL, "1500 L-band"),
        LteBand(34, 2010.0, 36200, 36349, null, null, Duplex.TDD, "2000 TDD"),
        LteBand(38, 2570.0, 37750, 38249, null, null, Duplex.TDD, "2600 TDD"),
        LteBand(39, 1880.0, 38250, 38649, null, null, Duplex.TDD, "1900 TDD"),
        LteBand(40, 2300.0, 38650, 39649, null, null, Duplex.TDD, "2300 TDD"),
        LteBand(41, 2496.0, 39650, 41589, null, null, Duplex.TDD, "2500 TDD"),
        LteBand(42, 3400.0, 41590, 43589, null, null, Duplex.TDD, "3500 TDD"),
        LteBand(43, 3600.0, 43590, 45589, null, null, Duplex.TDD, "3700 TDD"),
        LteBand(46, 5150.0, 46790, 54539, null, null, Duplex.TDD, "5 GHz LAA"),
        LteBand(48, 3550.0, 55240, 56739, null, null, Duplex.TDD, "3.5 GHz CBRS"),
        LteBand(66, 2110.0, 66436, 67335, 1710.0, 131972, Duplex.FDD, "AWS-3"),
        LteBand(71, 617.0, 68586, 68935, 663.0, 133122, Duplex.FDD, "600"),
    )

    fun lte(earfcn: Int): BandInfo? {
        val b = lte.firstOrNull { earfcn in it.nOffDl..it.nDlMax } ?: return null
        val dl = b.dlLow + 0.1 * (earfcn - b.nOffDl)
        val ul = when (b.duplex) {
            Duplex.TDD -> dl
            Duplex.SDL -> null
            Duplex.FDD -> b.ulLow!! + (dl - b.dlLow)
        }
        return BandInfo(RadioType.LTE, "B${b.band}", b.band, dl, ul, b.duplex, b.name)
    }

    private class NrBand(
        val band: Int, val dlLow: Double, val dlHigh: Double, val ulLow: Double?,
        val duplex: Duplex, val name: String, val usPreferred: Boolean = false,
    )

    private val nr = listOf(
        NrBand(1, 2110.0, 2170.0, 1920.0, Duplex.FDD, "2100 IMT"),
        NrBand(66, 2110.0, 2200.0, 1710.0, Duplex.FDD, "AWS-3", usPreferred = true),
        NrBand(2, 1930.0, 1990.0, 1850.0, Duplex.FDD, "1900 PCS"),
        NrBand(25, 1930.0, 1995.0, 1850.0, Duplex.FDD, "1900+ G", usPreferred = true),
        NrBand(3, 1805.0, 1880.0, 1710.0, Duplex.FDD, "1800 DCS"),
        NrBand(5, 869.0, 894.0, 824.0, Duplex.FDD, "850 CLR"),
        NrBand(7, 2620.0, 2690.0, 2500.0, Duplex.FDD, "2600 IMT-E"),
        NrBand(8, 925.0, 960.0, 880.0, Duplex.FDD, "900"),
        NrBand(12, 729.0, 746.0, 699.0, Duplex.FDD, "700 a"),
        NrBand(13, 746.0, 756.0, 777.0, Duplex.FDD, "700 c"),
        NrBand(14, 758.0, 768.0, 788.0, Duplex.FDD, "700 PS"),
        NrBand(20, 791.0, 821.0, 832.0, Duplex.FDD, "800 DD"),
        NrBand(26, 859.0, 894.0, 814.0, Duplex.FDD, "850+"),
        NrBand(28, 758.0, 803.0, 703.0, Duplex.FDD, "700 APT"),
        NrBand(29, 717.0, 728.0, null, Duplex.SDL, "700 d"),
        NrBand(30, 2350.0, 2360.0, 2305.0, Duplex.FDD, "2300 WCS"),
        NrBand(41, 2496.0, 2690.0, null, Duplex.TDD, "2.5 GHz"),
        NrBand(38, 2570.0, 2620.0, null, Duplex.TDD, "2600 TDD"),
        NrBand(40, 2300.0, 2400.0, null, Duplex.TDD, "2300 TDD"),
        NrBand(48, 3550.0, 3700.0, null, Duplex.TDD, "CBRS"),
        NrBand(70, 1995.0, 2020.0, 1695.0, Duplex.FDD, "AWS-4"),
        NrBand(71, 617.0, 652.0, 663.0, Duplex.FDD, "600"),
        NrBand(75, 1432.0, 1517.0, null, Duplex.SDL, "1500 L-band"),
        NrBand(77, 3300.0, 4200.0, null, Duplex.TDD, "C-Band", usPreferred = true),
        NrBand(78, 3300.0, 3800.0, null, Duplex.TDD, "3.5 GHz"),
        NrBand(79, 4400.0, 5000.0, null, Duplex.TDD, "4.7 GHz"),
        NrBand(258, 24250.0, 27500.0, null, Duplex.TDD, "mmWave 26 GHz"),
        NrBand(261, 27500.0, 28350.0, null, Duplex.TDD, "mmWave 28 GHz", usPreferred = true),
        NrBand(257, 26500.0, 29500.0, null, Duplex.TDD, "mmWave 28 GHz"),
        NrBand(260, 37000.0, 40000.0, null, Duplex.TDD, "mmWave 39 GHz"),
    )

    /** Bands overlapping a US-deployed band that US operators don't use (2.6 GHz is n41 there). */
    private val notUsedInUs = setOf(7, 38)

    /** NR-ARFCN → frequency in MHz (global frequency raster, TS 38.104 §5.4.2.1). */
    fun nrArfcnToMHz(arfcn: Int): Double = when {
        arfcn < 600_000 -> 0.005 * arfcn
        arfcn < 2_016_667 -> 3000.0 + 0.015 * (arfcn - 600_000)
        else -> 24250.08 + 0.06 * (arfcn - 2_016_667)
    }

    fun nr(arfcn: Int, reportedBands: List<Int> = emptyList(), mcc: Int? = null): BandInfo? {
        var dl = nrArfcnToMHz(arfcn)
        var candidates = nr.filter { dl >= it.dlLow - 0.01 && dl <= it.dlHigh + 0.01 }
        if (candidates.isEmpty()) {
            // Channel number doesn't fall in any band (seen on emulators and some modems):
            // trust the modem-reported band and use its centre frequency.
            val reported = nr.firstOrNull { it.band in reportedBands } ?: return null
            dl = (reported.dlLow + reported.dlHigh) / 2
            candidates = listOf(reported)
        }
        val isUs = mcc != null && mcc in 310..316
        val b = candidates.firstOrNull { it.band in reportedBands }
            ?: candidates.firstOrNull { isUs && it.usPreferred }
            ?: candidates.firstOrNull { (!it.usPreferred || isUs) && !(isUs && it.band in notUsedInUs) }
            ?: candidates.firstOrNull()
            ?: return null
        val ul = when (b.duplex) {
            Duplex.TDD -> dl
            Duplex.SDL -> null
            Duplex.FDD -> b.ulLow!! + (dl - b.dlLow)
        }
        return BandInfo(RadioType.NR, "n${b.band}", b.band, dl, ul, b.duplex, b.name)
    }

    /** Default carrier bandwidth (MHz) commonly deployed in an NR band, used when the OS doesn't report it. */
    fun typicalNrBandwidthMHz(band: Int): Double = when (band) {
        41, 77, 78, 79 -> 100.0
        48 -> 40.0
        in 257..261 -> 400.0
        5, 12, 13, 14, 26, 29 -> 10.0
        else -> 20.0
    }

    fun umts(uarfcn: Int): BandInfo? {
        data class U(val band: Int, val range: IntRange, val offset: Double, val duplex: Double, val name: String)
        val table = listOf(
            U(1, 10562..10838, 0.0, 190.0, "2100 IMT"),
            U(2, 9662..9938, 0.0, 80.0, "1900 PCS"),
            U(2, 412..687, 1850.1, 80.0, "1900 PCS"),
            U(3, 1162..1513, 1575.0, 95.0, "1800 DCS"),
            U(4, 1537..1738, 1805.0, 400.0, "AWS-1"),
            U(5, 4357..4458, 0.0, 45.0, "850 CLR"),
            U(5, 1007..1087, 670.1, 45.0, "850 CLR"),
            U(8, 2937..3088, 340.0, 45.0, "900"),
        )
        val u = table.firstOrNull { uarfcn in it.range } ?: return null
        val dl = u.offset + 0.2 * uarfcn
        return BandInfo(RadioType.WCDMA, "B${u.band}", u.band, dl, dl - u.duplex, Duplex.FDD, u.name)
    }

    fun gsm(arfcn: Int, mcc: Int? = null): BandInfo? {
        val isAmericas = mcc != null && mcc in 300..399
        return when (arfcn) {
            in 0..124 -> {
                val ul = 890.0 + 0.2 * arfcn
                BandInfo(RadioType.GSM, "GSM 900", 900, ul + 45, ul, Duplex.FDD, "P-GSM")
            }
            in 975..1023 -> {
                val ul = 890.0 + 0.2 * (arfcn - 1024)
                BandInfo(RadioType.GSM, "GSM 900", 900, ul + 45, ul, Duplex.FDD, "E-GSM")
            }
            in 128..251 -> {
                val ul = 824.2 + 0.2 * (arfcn - 128)
                BandInfo(RadioType.GSM, "GSM 850", 850, ul + 45, ul, Duplex.FDD, "GSM 850")
            }
            in 512..810 -> if (isAmericas) {
                val dl = 1930.2 + 0.2 * (arfcn - 512)
                BandInfo(RadioType.GSM, "PCS 1900", 1900, dl, dl - 80, Duplex.FDD, "PCS 1900")
            } else {
                val ul = 1710.2 + 0.2 * (arfcn - 512)
                BandInfo(RadioType.GSM, "DCS 1800", 1800, ul + 95, ul, Duplex.FDD, "DCS 1800")
            }
            in 811..885 -> {
                val ul = 1710.2 + 0.2 * (arfcn - 512)
                BandInfo(RadioType.GSM, "DCS 1800", 1800, ul + 95, ul, Duplex.FDD, "DCS 1800")
            }
            else -> null
        }
    }

    fun forCell(radio: RadioType, channel: Int?, bands: List<Int>, mcc: Int?): BandInfo? {
        if (channel == null) return null
        return when (radio) {
            RadioType.LTE -> lte(channel)
            RadioType.NR -> nr(channel, bands, mcc)
            RadioType.WCDMA, RadioType.TDSCDMA -> umts(channel)
            RadioType.GSM -> gsm(channel, mcc)
            else -> null
        }
    }
}
