package io.towerscope

import io.towerscope.calc.Analyzer
import io.towerscope.calc.CellAnalysis
import io.towerscope.calc.Advice
import io.towerscope.calc.DeviceConditions
import io.towerscope.calc.DiagnosticInput
import io.towerscope.calc.Diagnostics
import io.towerscope.calc.Geo
import io.towerscope.calc.NeighborWatch
import io.towerscope.calc.SettingsTarget
import io.towerscope.calc.SinrSanity
import io.towerscope.data.SavedSpot
import io.towerscope.data.SpotStore
import io.towerscope.model.RadioType
import io.towerscope.data.AppSettings
import io.towerscope.data.SettingsRepository
import io.towerscope.data.SpeedPhase
import io.towerscope.data.SpeedResult
import io.towerscope.data.SpeedTestState
import io.towerscope.data.UnitSystem
import io.towerscope.data.SpeedTester
import io.towerscope.data.TowerDatabase
import io.towerscope.model.CellObservation
import io.towerscope.model.DatabaseTower
import io.towerscope.model.GeoFix
import io.towerscope.model.RadioSnapshot
import io.towerscope.model.SignalSample
import io.towerscope.model.TowerLookup
import io.towerscope.platform.PlatformServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Long-lived application state holder shared by both platforms. */
class AppController(val platform: PlatformServices) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val settingsRepo = SettingsRepository(platform.store, UnitSystem.defaultFor(platform.regionCode))
    private val http by lazy { platform.createHttpClient() }
    private val towerDb by lazy { TowerDatabase(http) }
    private val speedTester by lazy { SpeedTester(http) }

    private val _settings = MutableStateFlow(settingsRepo.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _snapshot = MutableStateFlow<RadioSnapshot?>(null)
    val snapshot: StateFlow<RadioSnapshot?> = _snapshot.asStateFlow()

    private val _fix = MutableStateFlow<GeoFix?>(null)
    val fix: StateFlow<GeoFix?> = _fix.asStateFlow()

    private val _heading = MutableStateFlow<Float?>(null)
    val heading: StateFlow<Float?> = _heading.asStateFlow()

    private val _declination = MutableStateFlow<Float?>(null)
    /** Magnetic declination (east positive) once known; while null, [heading] is magnetic. */
    val declination: StateFlow<Float?> = _declination.asStateFlow()

    private val _history = MutableStateFlow<List<SignalSample>>(emptyList())
    val history: StateFlow<List<SignalSample>> = _history.asStateFlow()

    private val _lookups = MutableStateFlow<Map<String, TowerLookup>>(emptyMap())
    val lookups: StateFlow<Map<String, TowerLookup>> = _lookups.asStateFlow()

    private val _nearby = MutableStateFlow<NearbyState>(NearbyState())
    val nearby: StateFlow<NearbyState> = _nearby.asStateFlow()

    private val _speed = MutableStateFlow(SpeedTestState())
    val speed: StateFlow<SpeedTestState> = _speed.asStateFlow()

    private val _speedHistory = MutableStateFlow<List<SpeedResult>>(emptyList())
    val speedHistory: StateFlow<List<SpeedResult>> = _speedHistory.asStateFlow()

    private val _radioError = MutableStateFlow<String?>(null)
    val radioError: StateFlow<String?> = _radioError.asStateFlow()

    val permissions = platform.permissions.state

    private val _conditions = MutableStateFlow(DeviceConditions())
    val conditions: StateFlow<DeviceConditions> = _conditions.asStateFlow()

    private val _advice = MutableStateFlow<List<Advice>>(emptyList())
    /** Current diagnostic findings, most important first. */
    val advice: StateFlow<List<Advice>> = _advice.asStateFlow()

    private val spotStore = SpotStore(platform.store)
    private val _spots = MutableStateFlow(spotStore.load())
    val spots: StateFlow<List<SavedSpot>> = _spots.asStateFlow()

    /** True when the modem's LTE SINR is a constant 0 dB placeholder (hidden from the UI and models). */
    private val _sinrUnreported = MutableStateFlow(false)
    val sinrUnreported: StateFlow<Boolean> = _sinrUnreported.asStateFlow()

    private val neighborWatch = NeighborWatch()
    private var neighborCandidate: io.towerscope.calc.NeighborCandidate? = null
    private val rawSinr = ArrayDeque<Pair<Double?, Int?>>()
    private var fastRadio = false

    private var radioJob: Job? = null
    private var locationJob: Job? = null
    private var headingJob: Job? = null
    private var speedJob: Job? = null
    private var started = false
    private var lastNearbyFix: GeoFix? = null

    init {
        scope.launch {
            permissions.map { it.coreGranted }.distinctUntilChanged().collect { granted ->
                if (granted && started) startSensors()
            }
        }
    }

    /** Call when the UI becomes visible. */
    fun start() {
        started = true
        if (permissions.value.coreGranted) startSensors()
        if (headingJob == null) {
            headingJob = scope.launch {
                platform.headings().catch { }.collect {
                    _declination.value = platform.declinationDeg
                    _heading.value = it
                }
            }
        }
    }

    /** Call when the UI is hidden; stops radio and GPS to save battery (a running speed test continues). */
    fun stop() {
        started = false
        radioJob?.cancel(); radioJob = null
        locationJob?.cancel(); locationJob = null
        headingJob?.cancel(); headingJob = null
    }

    private fun startSensors() {
        if (radioJob == null) {
            radioJob = scope.launch {
                platform.radioSnapshots(if (fastRadio) FAST_INTERVAL_MS else _settings.value.refreshSeconds * 1000L)
                    .catch { e -> _radioError.value = e.message ?: "Radio unavailable" }
                    .collect { onSnapshot(it) }
            }
        }
        if (locationJob == null) {
            locationJob = scope.launch {
                platform.locationFixes().catch { }.collect { onFix(it) }
            }
        }
    }

    private fun restartRadio() {
        radioJob?.cancel(); radioJob = null
        if (started && permissions.value.coreGranted) startSensors()
    }

    private fun onSnapshot(incoming: RadioSnapshot) {
        _radioError.value = null
        // Some modems (e.g. Samsung) intermittently return an empty cell list between real reports.
        // Hold the last good cells briefly so the UI doesn't collapse to "scanning" and lose scroll position.
        val prev = _snapshot.value
        val s = if (incoming.serving == null && prev?.serving != null &&
            incoming.timestampMs - prev.timestampMs < STALE_HOLD_MS
        ) {
            incoming.copy(cells = prev.cells, timestampMs = prev.timestampMs)
        } else incoming
        val isHeld = s !== incoming
        if (isHeld) {
            _snapshot.value = s
            return
        }
        // Detect a placeholder SINR on the serving LTE cell and strip it so nothing treats it as real.
        val rawServing = s.serving
        if (rawServing != null && rawServing.identity.radio == RadioType.LTE) {
            rawSinr.addLast(rawServing.signal.sinr to rawServing.signal.rsrp)
            while (rawSinr.size > 12) rawSinr.removeFirst()
            _sinrUnreported.value = SinrSanity.isPlaceholder(rawSinr.toList())
        }
        val cleaned = if (_sinrUnreported.value) s.copy(
            cells = s.cells.map { c ->
                if (c.identity.radio == RadioType.LTE && c.signal.sinr == 0.0) c.copy(signal = c.signal.copy(sinr = null)) else c
            },
        ) else s
        _snapshot.value = cleaned
        neighborCandidate = neighborWatch.update(cleaned.serving, cleaned.neighbors, platform.nowMs())
        _conditions.value = runCatching { platform.deviceConditions() }.getOrDefault(DeviceConditions())
        recomputeAdvice()
        onCleanSnapshot(cleaned)
    }

    private fun onCleanSnapshot(s: RadioSnapshot) {
        s.serving?.let { cell ->
            val sample = SignalSample(
                timestampMs = s.timestampMs,
                cellKey = cell.key,
                radio = cell.identity.radio,
                dbm = cell.signal.dbm,
                rsrp = cell.signal.rsrp ?: cell.signal.rscp,
                rsrq = cell.signal.rsrq,
                sinr = cell.signal.sinr,
                rssi = cell.signal.rssi,
                cqi = cell.signal.cqi,
                timingAdvance = cell.signal.timingAdvance,
            )
            _history.update { (it + sample).takeLast(MAX_HISTORY) }
        }
        // Resolve tower positions for serving cells and complete neighbor identities.
        s.cells.filter { it.identity.isLookupable }
            .sortedByDescending { it.isServing }
            .take(6)
            .forEach { ensureLookup(it) }
    }

    private fun ensureLookup(cell: CellObservation) {
        val key = cell.identity.key
        val existing = _lookups.value[key]
        if (existing != null && existing !is TowerLookup.NoApiKey && existing !is TowerLookup.Failed) return
        val apiKey = _settings.value.openCellIdKey
        if (apiKey.isBlank()) {
            _lookups.update { it + (key to TowerLookup.NoApiKey) }
            return
        }
        if (existing is TowerLookup.Failed && failedRecently(key)) return
        _lookups.update { it + (key to TowerLookup.Loading) }
        scope.launch {
            val result = towerDb.lookup(cell.identity, apiKey)
            if (result is TowerLookup.Failed) failureTimes[key] = platform.nowMs()
            _lookups.update { it + (key to result) }
        }
    }

    private val failureTimes = mutableMapOf<String, Long>()
    private fun failedRecently(key: String) = (failureTimes[key] ?: 0L) > platform.nowMs() - 60_000

    private fun onFix(f: GeoFix) {
        _fix.value = f
        val last = lastNearbyFix
        if (last == null || Geo.distanceM(last.lat, last.lon, f.lat, f.lon) > 400) {
            refreshNearby(f)
        }
    }

    fun refreshNearby(f: GeoFix? = _fix.value) {
        f ?: return
        val key = _settings.value.openCellIdKey
        if (key.isBlank()) {
            _nearby.value = NearbyState(error = null, needsKey = true)
            return
        }
        lastNearbyFix = f
        _nearby.update { it.copy(loading = true, needsKey = false) }
        scope.launch {
            towerDb.nearby(f.lat, f.lon, key).fold(
                onSuccess = { towers -> _nearby.value = NearbyState(towers = towers, centerLat = f.lat, centerLon = f.lon) },
                onFailure = { e -> _nearby.update { it.copy(loading = false, error = e.message ?: "Lookup failed") } },
            )
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val old = _settings.value
        val new = transform(old)
        if (new == old) return
        _settings.value = new
        settingsRepo.save(new)
        if (new.openCellIdKey != old.openCellIdKey) {
            _lookups.value = emptyMap()
            failureTimes.clear()
            lastNearbyFix = null
            _snapshot.value?.let { onSnapshot(it) }
            refreshNearby()
        }
        if (new.refreshSeconds != old.refreshSeconds) restartRadio()
    }

    fun retryLookup(cell: CellObservation) {
        failureTimes.remove(cell.identity.key)
        _lookups.update { it - cell.identity.key }
        ensureLookup(cell)
    }

    private var lastCompletedSpeed: SpeedTestState? = null

    fun startSpeedTest() {
        if (_speed.value.running) return
        val previous = _speed.value
        if (previous.phase == SpeedPhase.DONE) lastCompletedSpeed = previous
        speedJob = scope.launch {
            speedTester.run()
                .flowOn(Dispatchers.Default)
                .collect { st ->
                    _speed.value = st
                    if (st.phase == SpeedPhase.DONE) {
                        val net = _snapshot.value?.network
                        val label = when (net?.transport) {
                            io.towerscope.model.Transport.CELLULAR -> listOfNotNull(net.operatorName, net.networkTypeLabel).joinToString(" · ")
                            null -> "Unknown"
                            else -> net.transport.label
                        }
                        val serving = _snapshot.value?.serving
                        val nr = _snapshot.value?.cells?.firstOrNull { it.isServing && it.identity.radio == RadioType.NR && it !== serving }
                        val predicted = Analyzer.combinedThroughput(serving?.let { analyze(it) }, nr?.let { analyze(it) })?.first
                        _speedHistory.update {
                            val r = SpeedResult(
                                platform.nowMs(), st.pingMs, st.jitterMs, st.downMbps, st.upMbps, label,
                                transport = net?.transport, predictedDownMbps = predicted,
                                rsrp = serving?.signal?.let { it.rsrp ?: it.rscp ?: it.dbm },
                            )
                            (listOf(r) + it).take(20)
                        }
                        recomputeAdvice()
                    }
                }
        }
    }

    fun cancelSpeedTest() {
        speedJob?.cancel()
        // Restore the last completed result instead of leaving a half-finished run on screen.
        _speed.value = lastCompletedSpeed ?: SpeedTestState()
    }

    private fun recomputeAdvice() {
        val snap = _snapshot.value
        val serving = snap?.serving
        val nrLeg = when {
            serving?.identity?.radio == RadioType.NR -> serving
            else -> snap?.cells?.firstOrNull { it.isServing && it.identity.radio == RadioType.NR }
        }
        _advice.value = Diagnostics.evaluate(
            DiagnosticInput(
                serving = serving,
                nrLeg = nrLeg,
                network = snap?.network,
                conditions = _conditions.value,
                neighbor = neighborCandidate,
                recentSpeeds = _speedHistory.value,
                nowMs = platform.nowMs(),
            ),
        )
    }

    fun openSystemSettings(target: SettingsTarget) = platform.openSystemSettings(target)

    /** Faster radio polling while the spot finder is open. */
    fun setFastRadio(on: Boolean) {
        if (fastRadio == on) return
        fastRadio = on
        restartRadio()
    }

    fun saveSpot(spot: SavedSpot) {
        _spots.update { (it + spot).sortedByDescending { s -> s.signalDbm } }
        spotStore.save(_spots.value)
    }

    fun deleteSpot(spot: SavedSpot) {
        _spots.update { it - spot }
        spotStore.save(_spots.value)
    }

    fun clearSpots() {
        _spots.value = emptyList()
        spotStore.save(emptyList())
    }

    fun analyze(cell: CellObservation): CellAnalysis =
        Analyzer.analyze(cell, _lookups.value[cell.identity.key], _fix.value, _settings.value, _snapshot.value?.network)

    fun openUrl(url: String) = platform.openUrl(url)

    private companion object {
        const val MAX_HISTORY = 600
        const val STALE_HOLD_MS = 15_000L
        const val FAST_INTERVAL_MS = 1_000L
    }
}

data class NearbyState(
    val towers: List<DatabaseTower> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val needsKey: Boolean = false,
    val centerLat: Double? = null,
    val centerLon: Double? = null,
)
