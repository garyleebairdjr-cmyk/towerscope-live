package io.towerscope.ui.screens

import io.towerscope.calc.GuideTopic

sealed interface GuideBlock {
    data class Para(val text: String) : GuideBlock
    data class Bullets(val items: List<String>) : GuideBlock
    /** A highlighted caveat or honest limitation. */
    data class Caveat(val text: String) : GuideBlock
}

data class GuideSection(
    val topic: GuideTopic,
    /** Rough size of the improvement, shown as a pill. Null when not meaningful. */
    val typicalGain: String? = null,
    val blocks: List<GuideBlock>,
)

/**
 * The "Better signal" guide. Written to be honest: what helps, by roughly how much, and what
 * doesn't work. Numbers are typical ranges, not promises.
 */
object GuideContent {
    val intro = "Most signal problems come down to where you are, what the network is doing, or a few phone " +
        "settings. This guide explains which fixes really work, roughly how much they help, and which " +
        "popular tricks don't."

    val sections: List<GuideSection> = listOf(
        GuideSection(
            GuideTopic.SHORT_VERSION, null,
            listOf(
                GuideBlock.Para("In rough order of how much they usually help:"),
                GuideBlock.Bullets(
                    listOf(
                        "Move: a window, a higher floor, or the side of the building facing the tower. Often 10–20 dB.",
                        "If a nearby cell is much stronger, toggle airplane mode so the phone picks again.",
                        "If 5G is weak, try LTE. Turn off battery saver when you need speed.",
                        "Indoors with weak signal, use Wi-Fi calling for calls.",
                        "For a permanent fix at home or work: a certified signal booster or your carrier's network extender.",
                        "If the signal is good but speeds are poor, the tower is probably busy. That isn't something your phone can fix.",
                    ),
                ),
            ),
        ),
        GuideSection(
            GuideTopic.APP_LIMITS, null,
            listOf(
                GuideBlock.Para(
                    "Android and iOS don't let ordinary apps change radio settings. No app from an app store can " +
                        "lock bands, pick a tower, change the network mode or toggle airplane mode for you. Those need " +
                        "system privileges or a rooted phone.",
                ),
                GuideBlock.Para(
                    "What TowerScope can do is measure carefully, spot common problems, tell you which fix is likely " +
                        "to help, and open the right settings page so you can make the change yourself.",
                ),
                GuideBlock.Caveat("Any app that claims to boost your signal on its own is not telling the truth. Software can't make radio waves stronger."),
            ),
        ),
        GuideSection(
            GuideTopic.READING_SIGNAL, null,
            listOf(
                GuideBlock.Para("Two numbers matter most:"),
                GuideBlock.Bullets(
                    listOf(
                        "RSRP is how loud the tower is. Around −80 dBm is excellent, −100 is fair, and below −110 is poor. Every 10 dB is a tenfold change in power.",
                        "SINR is how clearly you can hear it over interference from other cells. Above 13 dB is good. Below 0 dB is poor, even if RSRP looks fine.",
                    ),
                ),
                GuideBlock.Para(
                    "A strong but noisy signal (good RSRP, low SINR) usually means two or more towers overlap where you " +
                        "are. A different room or window can change that.",
                ),
                GuideBlock.Caveat(
                    "Signal bars are set by each phone maker and carrier and mostly follow RSRP. They don't show " +
                        "interference or how busy the tower is, so full bars can still mean slow data.",
                ),
            ),
        ),
        GuideSection(
            GuideTopic.LOCATION, "Often 10–20 dB",
            listOf(
                GuideBlock.Para(
                    "This is usually the biggest single improvement. Buildings block signal: brick and concrete can cost " +
                        "10–20 dB, and energy-efficient (low-E) coated windows and metal siding can cost 25 dB or more. " +
                        "Basements are often the worst.",
                ),
                GuideBlock.Bullets(
                    listOf(
                        "Try a window, ideally one facing the tower. With an OpenCelliD key, TowerScope shows which way that is.",
                        "Go up a floor if you can.",
                        "Move a metre or two. Signal reflects off walls and furniture, so it varies over short distances.",
                        "Use Find best spot: walk around slowly, wait a few seconds at each place, and save the spots you try.",
                    ),
                ),
                GuideBlock.Caveat("Readings lag by a second or two and bounce around naturally. Compare averages over a few seconds, not single readings."),
            ),
        ),
        GuideSection(
            GuideTopic.GRIP_CASE, "About 3–10 dB",
            listOf(
                GuideBlock.Para(
                    "Your hand absorbs radio energy. Covering the antenna areas (usually the top and bottom edges) can cost " +
                        "several dB. Metal cases, cases with metal plates, and magnetic mounts can cost more.",
                ),
                GuideBlock.Para(
                    "To test: stand still in one spot, note the average signal, then remove the case or change your grip " +
                        "and compare. A difference under 2–3 dB is just normal variation.",
                ),
            ),
        ),
        GuideSection(
            GuideTopic.RESELECT, "Varies",
            listOf(
                GuideBlock.Para(
                    "Phones don't always use the strongest nearby cell. The network chooses which cell and band you " +
                        "use, based on load, band priority and handover settings. Sometimes a phone stays on a weaker " +
                        "cell after you move.",
                ),
                GuideBlock.Para(
                    "TowerScope watches the neighbor cells your phone hears. If one stays clearly stronger (6 dB or more) " +
                        "for 30 seconds, it suggests a reset: turn airplane mode on, wait about 10 seconds, and turn it off. " +
                        "The phone then chooses a cell from scratch.",
                ),
                GuideBlock.Caveat(
                    "The network may move you back on purpose, for example to keep a busy cell from getting " +
                        "overloaded. If the same thing keeps happening, it's a network decision, not a phone fault.",
                ),
            ),
        ),
        GuideSection(
            GuideTopic.FIVE_G, "Situational",
            listOf(
                GuideBlock.Para(
                    "5G isn't always faster. In many places phones use 5G NSA (non-standalone), which adds a 5G link on " +
                        "top of LTE. When the 5G part is weak, the phone can spend effort and battery holding onto it, and " +
                        "speeds can be worse than plain LTE.",
                ),
                GuideBlock.Para(
                    "If TowerScope reports a weak 5G signal and your speeds are poor, try setting the network mode to " +
                        "LTE (usually under Settings, Mobile networks, Network mode). Run a speed test before and after.",
                ),
                GuideBlock.Caveat("Remember to switch back to 5G when you're somewhere with good 5G coverage."),
            ),
        ),
        GuideSection(
            GuideTopic.BATTERY_SAVER, null,
            listOf(
                GuideBlock.Para(
                    "Power-saving modes can turn off 5G, restrict background data and slow the processor. On Samsung " +
                        "phones, turning off 5G is an option inside Power saving. That's fine when battery matters, but " +
                        "turn it off when you need full speed or a fair speed test.",
                ),
            ),
        ),
        GuideSection(
            GuideTopic.WIFI_CALLING, "Fixes calls, not data",
            listOf(
                GuideBlock.Para(
                    "With Wi-Fi calling, calls and texts go over your Wi-Fi internet instead of the cell network. Where " +
                        "the signal is weak indoors, it's the easiest way to stop dropped calls. It doesn't improve mobile " +
                        "data, and it needs your carrier to support it (most major carriers do).",
                ),
            ),
        ),
        GuideSection(
            GuideTopic.UPDATES, null,
            listOf(
                GuideBlock.Para(
                    "Fixes for the modem (the phone's radio chip) and carrier settings arrive through system updates. " +
                        "Keeping your phone updated sometimes improves signal, battery use and 5G behavior. Don't expect " +
                        "big jumps, though: the radio hardware and the network matter far more.",
                ),
            ),
        ),
        GuideSection(
            GuideTopic.HARDWARE, "Large, when installed well",
            listOf(
                GuideBlock.Para("If a spot will always be bad (home office, basement, rural house), hardware is the real fix:"),
                GuideBlock.Bullets(
                    listOf(
                        "Signal booster (cellular repeater): an outdoor antenna picks up the signal and an indoor antenna rebroadcasts it. In the US, use an FCC-certified booster and register it with your carrier.",
                        "The outdoor antenna's placement matters most: high up, pointed at the tower. TowerScope's radar, compass and Find best spot can help you aim it.",
                        "Network extender / femtocell: some carriers offer a small cell that connects over your home internet.",
                        "Wi-Fi calling (free) is often enough if the problem is calls only.",
                    ),
                ),
                GuideBlock.Caveat("A booster can only amplify signal that exists. If there is no usable signal at the outdoor antenna either, it won't help."),
            ),
        ),
        GuideSection(
            GuideTopic.CONGESTION, null,
            listOf(
                GuideBlock.Para(
                    "A tower's capacity is shared by everyone connected to it. If your signal is good but speeds are " +
                        "poor, especially in the evening or at busy places, the cell is probably congested. Your phone " +
                        "can't fix that.",
                ),
                GuideBlock.Para(
                    "TowerScope compares the measured speed with what your signal should support. A big gap with a " +
                        "good signal points at congestion. Running the test again at another time of day usually " +
                        "confirms it.",
                ),
                GuideBlock.Bullets(
                    listOf(
                        "If you're reporting a problem to your carrier, specific numbers help: time, location, cell (eNB/gNB) ID, RSRP, SINR and measured speed. TowerScope shows all of these.",
                        "VPNs route your traffic through another server, so a speed test measures that path. Pause the VPN to test the cell itself.",
                    ),
                ),
            ),
        ),
        GuideSection(
            GuideTopic.MYTHS, null,
            listOf(
                GuideBlock.Bullets(
                    listOf(
                        "\"Signal booster\" apps: software can't increase signal strength. At best they toggle settings you can change yourself.",
                        "Antenna stickers and \"signal amplifier\" patches: no measurable effect.",
                        "Clearing the cache, closing apps or freeing RAM: doesn't affect the radio.",
                        "Turning off Wi-Fi or Bluetooth to improve cellular: no meaningful effect (and Wi-Fi calling actually helps).",
                        "Secret dialer codes: codes like *#*#4636#*#* open diagnostic menus. On some phones they let you pick the network type; none of them increase signal, and many are blocked on carrier phones.",
                        "More bars means faster: bars reflect signal strength, not interference or how busy the tower is.",
                    ),
                ),
            ),
        ),
        GuideSection(
            GuideTopic.ROOT_ONLY, null,
            listOf(
                GuideBlock.Para(
                    "Band locking and cell locking (forcing a specific band or tower) are possible on some phones through " +
                        "hidden service menus or with root access and specialist tools. They can help experts in specific " +
                        "situations, for example forcing a less crowded band.",
                ),
                GuideBlock.Caveat(
                    "They can also leave you with worse or no service when you move, and rooting can void your warranty " +
                        "and weaken security. Most US carrier phones block the service menus. TowerScope doesn't do this " +
                        "and doesn't recommend it for everyday use.",
                ),
            ),
        ),
    )
}
