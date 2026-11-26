package io.towerscope.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.time.TimeSource

enum class SpeedPhase(val label: String) {
    IDLE("Ready"), LATENCY("Measuring latency"), DOWNLOAD("Download"), UPLOAD("Upload"), DONE("Complete"), ERROR("Failed")
}

data class SpeedTestState(
    val phase: SpeedPhase = SpeedPhase.IDLE,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    /** Live throughput of the running phase. */
    val liveMbps: Double = 0.0,
    /** 0..1 progress within the running phase. */
    val progress: Float = 0f,
    val downSeries: List<Float> = emptyList(),
    val upSeries: List<Float> = emptyList(),
    val bytesUsed: Long = 0,
    val error: String? = null,
    val server: String = "speed.cloudflare.com",
) {
    val running: Boolean get() = phase == SpeedPhase.LATENCY || phase == SpeedPhase.DOWNLOAD || phase == SpeedPhase.UPLOAD
}

data class SpeedResult(
    val timestampMs: Long,
    val pingMs: Double?,
    val jitterMs: Double?,
    val downMbps: Double?,
    val upMbps: Double?,
    val networkLabel: String,
    val transport: io.towerscope.model.Transport? = null,
    /** Radio-model prediction and serving RSRP at the time of the test (for congestion checks). */
    val predictedDownMbps: Double? = null,
    val rsrp: Int? = null,
)

/**
 * Multi-stream HTTP throughput test against Cloudflare's public speed-test endpoints.
 * Each transfer phase runs for a fixed time; the first second (TCP slow start) is excluded.
 */
@OptIn(ExperimentalAtomicApi::class)
class SpeedTester(private val http: HttpClient) {
    private val clock = TimeSource.Monotonic

    fun run(
        phaseSeconds: Double = 8.0,
        downloadStreams: Int = 4,
        uploadStreams: Int = 3,
    ): Flow<SpeedTestState> = flow {
        var state = SpeedTestState(phase = SpeedPhase.LATENCY)
        emit(state)
        try {
            // ---- Latency -------------------------------------------------
            val rtts = mutableListOf<Double>()
            repeat(11) { i ->
                val t = clock.markNow()
                http.get("$BASE/__down?bytes=0") { header("Cache-Control", "no-cache") }.bodyAsChannel().readAvailable(ByteArray(16))
                val ms = t.elapsedNow().inWholeMicroseconds / 1000.0
                if (i > 0) rtts += ms // first request includes DNS + TLS handshake
                state = state.copy(progress = (i + 1) / 11f, pingMs = rtts.sorted().getOrNull(rtts.size / 2))
                emit(state)
            }
            val sorted = rtts.sorted()
            val ping = sorted[sorted.size / 2]
            val jitter = rtts.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average()
            state = state.copy(pingMs = ping, jitterMs = jitter, phase = SpeedPhase.DOWNLOAD, progress = 0f)
            emit(state)

            // ---- Download ------------------------------------------------
            val down = AtomicLong(0L)
            val downResult = transferPhase(phaseSeconds, down) {
                repeat(downloadStreams) {
                    launch {
                        val buf = ByteArray(64 * 1024)
                        while (isActive) {
                            http.prepareGet("$BASE/__down?bytes=$DOWNLOAD_CHUNK").execute { resp ->
                                val ch = resp.bodyAsChannel()
                                while (isActive) {
                                    val n = ch.readAvailable(buf)
                                    if (n < 0) break
                                    down.fetchAndAdd(n.toLong())
                                }
                            }
                        }
                    }
                }
            }
            var series = mutableListOf<Float>()
            downResult.collectSamples { mbps, progress ->
                series = (series + mbps.toFloat()).toMutableList()
                state = state.copy(liveMbps = mbps, progress = progress, downSeries = series, bytesUsed = down.load())
                emit(state)
            }
            val downMbps = downResult.finalMbps()
            state = state.copy(downMbps = downMbps, phase = SpeedPhase.UPLOAD, progress = 0f, liveMbps = 0.0)
            emit(state)

            // ---- Upload --------------------------------------------------
            val up = AtomicLong(0L)
            val payload = Random.nextBytes(UPLOAD_CHUNK)
            val upResult = transferPhase(phaseSeconds, up) {
                repeat(uploadStreams) {
                    launch {
                        while (isActive) {
                            var last = 0L
                            http.post("$BASE/__up") {
                                contentType(ContentType.Application.OctetStream)
                                setBody(payload)
                                onUpload { sent, _ ->
                                    up.fetchAndAdd(sent - last)
                                    last = sent
                                }
                            }
                        }
                    }
                }
            }
            var upSeries = mutableListOf<Float>()
            upResult.collectSamples { mbps, progress ->
                upSeries = (upSeries + mbps.toFloat()).toMutableList()
                state = state.copy(liveMbps = mbps, progress = progress, upSeries = upSeries, bytesUsed = down.load() + up.load())
                emit(state)
            }
            state = state.copy(upMbps = upResult.finalMbps(), phase = SpeedPhase.DONE, progress = 1f, liveMbps = 0.0)
            emit(state)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emit(state.copy(phase = SpeedPhase.ERROR, error = e.message ?: e::class.simpleName ?: "Network error"))
        }
    }

    /** Runs [workers] in the background while sampling the byte counter. */
    private inner class Phase(
        val seconds: Double,
        val counter: AtomicLong,
        val workers: suspend kotlinx.coroutines.CoroutineScope.() -> Unit,
    ) {
        private var measuredBytes = 0L
        private var measuredSeconds = 0.0

        suspend fun collectSamples(onSample: suspend (mbps: Double, progress: Float) -> Unit) = coroutineScope {
            val job = launch { workers() }
            val start = clock.markNow()
            val window = ArrayDeque<Pair<Double, Long>>()
            var warmBytes = -1L
            var warmT = 0.0
            while (true) {
                delay(SAMPLE_MS)
                val t = start.elapsedNow().inWholeMilliseconds / 1000.0
                val bytes = counter.load()
                window.addLast(t to bytes)
                while (window.size > 1 && t - window.first().first > 1.0) window.removeFirst()
                val (t0, b0) = window.first()
                val mbps = if (t > t0) (bytes - b0) * 8 / (t - t0) / 1e6 else 0.0
                if (warmBytes < 0 && t >= WARMUP_S) { warmBytes = bytes; warmT = t }
                onSample(mbps, (t / seconds).toFloat().coerceIn(0f, 1f))
                if (t >= seconds) {
                    measuredBytes = if (warmBytes >= 0) bytes - warmBytes else bytes
                    measuredSeconds = if (warmBytes >= 0) t - warmT else t
                    break
                }
            }
            job.cancel()
        }

        fun finalMbps(): Double = if (measuredSeconds > 0) measuredBytes * 8 / measuredSeconds / 1e6 else 0.0
    }

    private fun transferPhase(
        seconds: Double,
        counter: AtomicLong,
        workers: suspend kotlinx.coroutines.CoroutineScope.() -> Unit,
    ) = Phase(seconds, counter, workers)

    private companion object {
        const val BASE = "https://speed.cloudflare.com"
        const val DOWNLOAD_CHUNK = 25_000_000
        const val UPLOAD_CHUNK = 2_000_000
        const val SAMPLE_MS = 200L
        const val WARMUP_S = 1.0
    }
}
