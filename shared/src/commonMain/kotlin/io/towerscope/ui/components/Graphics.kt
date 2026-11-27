package io.towerscope.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.towerscope.calc.Confidence
import io.towerscope.calc.DistanceEstimate
import io.towerscope.calc.Fmt
import io.towerscope.calc.MetricKind
import io.towerscope.data.UnitSystem
import io.towerscope.ui.theme.Mono
import io.towerscope.ui.theme.Palette
import io.towerscope.ui.theme.TextStyles
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun polar(center: Offset, r: Float, deg: Float): Offset {
    // 0° = up (north), clockwise.
    val a = (deg - 90f) * PI.toFloat() / 180f
    return Offset(center.x + r * cos(a), center.y + r * sin(a))
}

// ---------------------------------------------------------------------------------------------
// Radar
// ---------------------------------------------------------------------------------------------

data class RadarBlip(
    val bearingDeg: Double?,
    val distanceM: Double?,
    val color: Color,
    val primary: Boolean = false,
    /** Distance-only estimate (no bearing): drawn as a dashed ring. */
    val ringOnly: Boolean = false,
)

@Composable
fun RadarView(
    blips: List<RadarBlip>,
    headingDeg: Float?,
    scanning: Boolean,
    rangeM: Double,
    units: UnitSystem,
    modifier: Modifier = Modifier,
    /** Magnetic declination (east positive). When known with a compass, a magnetic-north marker is drawn. */
    declinationDeg: Float? = null,
) {
    val transition = rememberInfiniteTransition()
    val sweep by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (scanning) 1800 else 4200, easing = LinearEasing)))
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)))
    val heading by animateFloatAsState(headingDeg ?: 0f, tween(300))
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Palette.TextFaint, fontSize = 9.sp, fontFamily = Mono)
    val cardinalStyle = TextStyle(color = Palette.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val northStyle = cardinalStyle.copy(color = Palette.Red)
    val magStyle = TextStyle(color = Palette.Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    val beamLabelStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
    val accent = Palette.Cyan

    Canvas(modifier.fillMaxWidth().aspectRatio(1f)) {
        val c = center
        val R = size.minDimension / 2 * 0.86f
        val rot = if (headingDeg != null) -heading else 0f

        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF0E2A3A), Color(0xFF0A1424), Color.Transparent), c, R * 1.15f),
            radius = R * 1.15f, center = c,
        )
        // Rings & labels
        for (i in 1..4) {
            val r = R * i / 4
            drawCircle(Palette.StrokeHi.copy(alpha = if (i == 4) 0.9f else 0.45f), r, c, style = Stroke(if (i == 4) 1.5f else 1f))
            val txt = Fmt.distance(rangeM * i / 4, units)
            val layout = measurer.measure(txt, labelStyle)
            // On the lower-right diagonal: labels stagger instead of colliding, and the north markers stay clear.
            val at = polar(c, r, 135f)
            drawText(layout, topLeft = Offset(at.x - layout.size.width - 2f, at.y - layout.size.height - 1f))
        }
        // Ticks & cardinal points (rotate with heading)
        for (d in 0 until 360 step 10) {
            val a = d + rot
            val major = d % 30 == 0
            drawLine(
                Palette.StrokeHi.copy(alpha = if (major) 0.9f else 0.5f),
                polar(c, R, a), polar(c, R - if (major) 10f else 5f, a), strokeWidth = if (major) 2f else 1f,
            )
        }
        listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (t, d) ->
            val layout = measurer.measure(t, if (t == "N") northStyle else cardinalStyle)
            val p = polar(c, R + 15.dp.toPx(), d + rot)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
        // North markers: red = true north, amber = magnetic north (only shown when the compass is in use).
        drawRingMarker(c, R, rot, Palette.Red, 6.dp.toPx())
        if (headingDeg != null && declinationDeg != null) {
            val m = declinationDeg + rot
            drawRingMarker(c, R, m, Palette.Amber, 5.dp.toPx())
            val layout = measurer.measure("MN", magStyle)
            val p = polar(c, R - 20.dp.toPx(), m)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
        // Crosshair
        drawLine(Palette.Stroke, Offset(c.x - R, c.y), Offset(c.x + R, c.y), 1f)
        drawLine(Palette.Stroke, Offset(c.x, c.y - R), Offset(c.x, c.y + R), 1f)

        // Sweep: a 70° fading trail behind the leading edge.
        rotate(sweep - 90f, c) {
            drawArc(
                Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.80f to Color.Transparent,
                    1f to accent.copy(alpha = if (scanning) 0.45f else 0.28f),
                    center = c,
                ),
                startAngle = 0f, sweepAngle = 360f, useCenter = true,
                topLeft = Offset(c.x - R, c.y - R), size = Size(2 * R, 2 * R),
            )
        }
        drawLine(accent.copy(alpha = 0.9f), c, polar(c, R, sweep), strokeWidth = 2f)

        // Direction beam to the primary tower: wedge, core line, chevrons flowing outward, bearing tag.
        blips.firstOrNull { it.primary && !it.ringOnly && it.bearingDeg != null }?.let { b ->
            val bearing = b.bearingDeg!!.toFloat()
            val a = bearing + rot
            val half = 8f
            val wedge = Path().apply {
                moveTo(c.x, c.y)
                val p0 = polar(c, R, a - half)
                lineTo(p0.x, p0.y)
                arcTo(
                    androidx.compose.ui.geometry.Rect(c.x - R, c.y - R, c.x + R, c.y + R),
                    a - half - 90f, 2 * half, false,
                )
                close()
            }
            drawPath(
                wedge,
                Brush.linearGradient(listOf(b.color.copy(alpha = 0.05f), b.color.copy(alpha = 0.42f)), c, polar(c, R, a)),
            )
            drawLine(b.color.copy(alpha = 0.95f), c, polar(c, R, a), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            for (i in 0 until 3) {
                val f = (pulse + i / 3f) % 1f
                val alpha = sin(f * PI.toFloat()).coerceIn(0f, 1f)
                val tip = polar(c, R * (0.18f + 0.74f * f), a)
                val back = R * (0.18f + 0.74f * f) - 7.dp.toPx()
                val chevron = Path().apply {
                    val l = polar(c, back, a - 6f); val r = polar(c, back, a + 6f)
                    moveTo(l.x, l.y); lineTo(tip.x, tip.y); lineTo(r.x, r.y)
                }
                drawPath(chevron, Color.White.copy(alpha = 0.85f * alpha), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            // Arrowhead just outside the ring.
            val tip = polar(c, R + 9.dp.toPx(), a)
            val bl = polar(c, R - 1.dp.toPx(), a - 4.5f); val br = polar(c, R - 1.dp.toPx(), a + 4.5f)
            drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(bl.x, bl.y); lineTo(br.x, br.y); close() }, b.color)
            // Bearing tag on a pill, placed on the beam between the center and the tower.
            val tag = measurer.measure("${((bearing % 360 + 360) % 360).toInt()}°", beamLabelStyle)
            val tp = polar(c, R * 0.58f, a)
            val pad = 4.dp.toPx()
            drawRoundRect(
                Color(0xE60A1424), Offset(tp.x - tag.size.width / 2f - pad, tp.y - tag.size.height / 2f - pad / 2),
                Size(tag.size.width + 2 * pad, tag.size.height + pad), CornerRadius(8.dp.toPx()),
            )
            drawRoundRect(
                b.color, Offset(tp.x - tag.size.width / 2f - pad, tp.y - tag.size.height / 2f - pad / 2),
                Size(tag.size.width + 2 * pad, tag.size.height + pad), CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()),
            )
            drawText(tag, topLeft = Offset(tp.x - tag.size.width / 2f, tp.y - tag.size.height / 2f))
        }

        // Blips
        blips.forEach { b ->
            val d = b.distanceM ?: return@forEach
            val r = (min(d / rangeM, 1.0) * R).toFloat()
            if (b.ringOnly || b.bearingDeg == null) {
                drawCircle(
                    b.color.copy(alpha = 0.55f + 0.25f * (1 - pulse)), r, c,
                    style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), pulse * 18f)),
                )
                return@forEach
            }
            val a = b.bearingDeg.toFloat() + rot
            val p = polar(c, r, a)
            // Brighten when the sweep has just passed.
            val since = ((sweep - (b.bearingDeg.toFloat() + rot)) % 360f + 360f) % 360f
            val glow = (1f - since / 360f).coerceIn(0f, 1f)
            if (b.primary) {
                drawCircle(b.color.copy(alpha = 0.18f * (1 - pulse)), 10f + 26f * pulse, p)
                drawCircle(b.color.copy(alpha = 0.25f + 0.5f * glow), 14f, p)
                drawTowerGlyph(p, b.color, 13f)
            } else {
                drawCircle(b.color.copy(alpha = 0.2f + 0.6f * glow), 6f, p)
                drawCircle(b.color, 3f, p)
            }
        }
        // You
        if (headingDeg != null) {
            val cone = Path().apply {
                moveTo(c.x, c.y)
                lineTo(polar(c, R * 0.32f, -18f).x, polar(c, R * 0.32f, -18f).y)
                lineTo(polar(c, R * 0.32f, 18f).x, polar(c, R * 0.32f, 18f).y)
                close()
            }
            drawPath(cone, Brush.radialGradient(listOf(Palette.Blue.copy(alpha = 0.45f), Color.Transparent), c, R * 0.32f))
        }
        drawCircle(Palette.Blue.copy(alpha = 0.25f), 14f, c)
        drawCircle(Color.White, 7f, c)
        drawCircle(Palette.Blue, 5f, c)
    }
}

/** Small inward-pointing triangle sitting on the outer ring at [deg]. */
private fun DrawScope.drawRingMarker(c: Offset, r: Float, deg: Float, color: Color, len: Float) {
    val tip = polar(c, r - 1f, deg)
    val spread = len / r * 180f / PI.toFloat() * 0.7f
    val l = polar(c, r + len, deg - spread); val rr = polar(c, r + len, deg + spread)
    drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(l.x, l.y); lineTo(rr.x, rr.y); close() }, color)
}

private fun DrawScope.drawTowerGlyph(p: Offset, color: Color, s: Float) {
    val path = Path().apply {
        moveTo(p.x, p.y - s)
        lineTo(p.x - s * 0.55f, p.y + s * 0.8f)
        moveTo(p.x, p.y - s)
        lineTo(p.x + s * 0.55f, p.y + s * 0.8f)
        moveTo(p.x - s * 0.32f, p.y + s * 0.1f)
        lineTo(p.x + s * 0.32f, p.y + s * 0.1f)
    }
    drawPath(path, Color.White, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(color, s * 0.22f, Offset(p.x, p.y - s))
    drawArc(Color.White.copy(alpha = 0.8f), -140f, 100f, false, Offset(p.x - s * 0.7f, p.y - s * 1.7f), Size(s * 1.4f, s * 1.4f), style = Stroke(1.6f, cap = StrokeCap.Round))
}

// ---------------------------------------------------------------------------------------------
// Arc gauge
// ---------------------------------------------------------------------------------------------

/** Degrees reserved before an arc's start so its round cap doesn't wrap the gradient seam. */
private const val CAP_PAD = 20f

private val gaugeColors = listOf(Palette.Red, Palette.Orange, Palette.Amber, Palette.Lime, Palette.Green)

@Composable
fun ArcGauge(
    fraction: Float?,
    valueText: String,
    unitText: String,
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    captionColor: Color = Palette.TextDim,
    colors: List<Color> = gaugeColors,
) {
    val f by animateFloatAsState(fraction ?: 0f, tween(900, easing = FastOutSlowInEasing))
    val start = 140f
    val span = 260f
    Box(modifier.aspectRatio(1.15f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.085f
            val d = min(size.width, size.height * 1.15f) - stroke * 2
            val tl = Offset((size.width - d) / 2, stroke + (size.height - d * 0.92f) / 2)
            val arcSize = Size(d, d)
            val c = Offset(tl.x + d / 2, tl.y + d / 2)
            drawArc(Palette.Stroke, start, span, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // Ticks
            for (i in 0..26) {
                val a = start + span * i / 26 + 90f
                val outer = d / 2 + stroke * 0.95f
                val inner = outer - if (i % 13 == 0) stroke * 0.8f else stroke * 0.45f
                drawLine(Palette.StrokeHi, polar(c, inner, a), polar(c, outer, a), 1.5f)
            }
            if (fraction != null && f > 0.001f) {
                // Rotate so the gradient's seam sits CAP_PAD degrees behind the arc start; otherwise
                // the rounded start cap wraps past 0° and picks up the far end of the gradient.
                val brush = Brush.sweepGradient(
                    *(listOf(0f to colors.first()) +
                        colors.mapIndexed { i, col -> ((CAP_PAD + span * i / (colors.size - 1)) / 360f) to col }).toTypedArray(),
                    center = c,
                )
                rotate(start - CAP_PAD, c) {
                    drawArc(brush, CAP_PAD, span * f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                val tip = polar(c, d / 2, start + span * f + 90f)
                drawCircle(Color.White.copy(alpha = 0.25f), stroke * 0.95f, tip)
                drawCircle(Color.White, stroke * 0.42f, tip)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
            Text(label.uppercase(), style = TextStyles.overline)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(valueText, style = TextStyles.metricLarge)
                Spacer(Modifier.width(4.dp))
                Text(unitText, style = TextStyles.unit, modifier = Modifier.padding(bottom = 8.dp))
            }
            caption?.let { Text(it, color = captionColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Score ring
// ---------------------------------------------------------------------------------------------

@Composable
fun ScoreRing(score: Int?, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState((score ?: 0) / 100f, tween(900))
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension * 0.09f
            val tl = Offset(s / 2, s / 2)
            val sz = Size(size.width - s, size.height - s)
            drawArc(Palette.Stroke, -90f, 360f, false, tl, sz, style = Stroke(s))
            rotate(-90f - CAP_PAD) {
                drawArc(
                    Brush.sweepGradient(0f to color.copy(alpha = 0.35f), (CAP_PAD / 360f) to color.copy(alpha = 0.35f), 1f to color),
                    CAP_PAD, 360f * f, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score?.toString() ?: "—", style = TextStyles.metricLarge.copy(fontSize = 34.sp))
            Text("/ 100", style = TextStyles.unit)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Line chart
// ---------------------------------------------------------------------------------------------

data class ChartSeries(val values: List<Float?>, val color: Color, val label: String, val fill: Boolean = true)
data class ChartBand(val from: Float, val to: Float, val color: Color)

@Composable
fun LineChart(
    series: List<ChartSeries>,
    yMin: Float,
    yMax: Float,
    modifier: Modifier = Modifier,
    bands: List<ChartBand> = emptyList(),
    gridLines: Int = 4,
    labelSuffix: String = "",
) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = Palette.TextFaint, fontSize = 9.sp, fontFamily = Mono)
    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        val left = 34.dp.toPx()
        val w = size.width - left
        val h = size.height
        fun y(v: Float) = h - (v - yMin) / (yMax - yMin) * h
        bands.forEach { b ->
            val top = y(min(b.to, yMax)).coerceIn(0f, h)
            val bot = y(max(b.from, yMin)).coerceIn(0f, h)
            drawRect(b.color.copy(alpha = 0.06f), Offset(left, top), Size(w, bot - top))
        }
        for (i in 0..gridLines) {
            val v = yMin + (yMax - yMin) * i / gridLines
            val yy = y(v)
            drawLine(Palette.Stroke.copy(alpha = 0.7f), Offset(left, yy), Offset(size.width, yy), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            val layout = measurer.measure("${v.toInt()}$labelSuffix", style)
            drawText(layout, topLeft = Offset(0f, (yy - layout.size.height / 2f).coerceIn(0f, h - layout.size.height)))
        }
        series.forEach { s ->
            val n = s.values.size
            if (n < 2) return@forEach
            val dx = w / (n - 1)
            val line = Path()
            val fill = Path()
            var open = false
            var firstX = 0f
            var lastPt: Offset? = null
            s.values.forEachIndexed { i, v ->
                if (v == null) {
                    if (open && s.fill) { fill.lineTo(lastPt!!.x, h); fill.lineTo(firstX, h); fill.close() }
                    open = false
                    return@forEachIndexed
                }
                val p = Offset(left + i * dx, y(v.coerceIn(yMin, yMax)))
                if (!open) {
                    line.moveTo(p.x, p.y); fill.moveTo(p.x, h); fill.lineTo(p.x, p.y); firstX = p.x; open = true
                } else {
                    line.lineTo(p.x, p.y); fill.lineTo(p.x, p.y)
                }
                lastPt = p
            }
            if (open && s.fill && lastPt != null) { fill.lineTo(lastPt!!.x, h); fill.lineTo(firstX, h); fill.close() }
            if (s.fill) drawPath(fill, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.28f), Color.Transparent)))
            drawPath(line, s.color, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            lastPt?.let {
                drawCircle(s.color.copy(alpha = 0.3f), 9f, it)
                drawCircle(s.color, 4.5f, it)
            }
        }
    }
}

fun bandsFor(kind: MetricKind): List<ChartBand> {
    if (kind.thresholds.isEmpty()) return emptyList()
    val out = mutableListOf<ChartBand>()
    var upper = kind.rangeMax.toFloat()
    kind.thresholds.forEach { t ->
        val lower = max(t.min, kind.rangeMin).toFloat()
        out += ChartBand(lower, upper, Palette.grade(t.grade))
        upper = lower
    }
    return out
}

// ---------------------------------------------------------------------------------------------
// Threshold scale
// ---------------------------------------------------------------------------------------------

@Composable
fun ThresholdScale(kind: MetricKind, value: Double?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = Palette.TextFaint, fontSize = 9.sp, fontFamily = Mono)
    val pos by animateFloatAsState(value?.let { kind.fraction(it) } ?: 0f, tween(700))
    Canvas(modifier.fillMaxWidth().height(54.dp)) {
        val barTop = 16.dp.toPx()
        val barH = 10.dp.toPx()
        val bands = bandsFor(kind).reversed()
        val range = (kind.rangeMax - kind.rangeMin).toFloat()
        bands.forEachIndexed { i, b ->
            val x0 = (b.from - kind.rangeMin.toFloat()) / range * size.width
            val x1 = (b.to - kind.rangeMin.toFloat()) / range * size.width
            drawRoundRect(
                b.color.copy(alpha = 0.75f), Offset(x0 + 1.5f, barTop), Size(max(0f, x1 - x0 - 3f), barH),
                CornerRadius(barH / 2),
            )
            if (i > 0) {
                val layout = measurer.measure(b.from.toInt().toString(), style)
                drawText(layout, topLeft = Offset((x0 - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width), barTop + barH + 6f))
            }
        }
        if (value != null) {
            val x = pos * size.width
            val tri = Path().apply {
                moveTo(x, barTop - 2f); lineTo(x - 7f, barTop - 14f); lineTo(x + 7f, barTop - 14f); close()
            }
            drawPath(tri, Color.White)
            drawLine(Color.White, Offset(x, barTop - 2f), Offset(x, barTop + barH + 2f), 2.5f)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Speed gauge (log scale)
// ---------------------------------------------------------------------------------------------

private val speedMarks = listOf(0, 1, 5, 10, 25, 50, 100, 250, 500, 1000)
private fun speedFraction(mbps: Double) = (log10(1 + mbps.coerceIn(0.0, 1000.0)) / log10(1001.0)).toFloat()

@Composable
fun SpeedGauge(
    mbps: Double,
    color: Color,
    centerTop: String,
    centerValue: String,
    centerUnit: String,
    modifier: Modifier = Modifier,
) {
    val f by animateFloatAsState(speedFraction(mbps), tween(250))
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = Palette.TextFaint, fontSize = 10.sp, fontFamily = Mono)
    val start = 135f
    val span = 270f
    Box(modifier.fillMaxWidth().aspectRatio(1.1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.06f
            val d = size.minDimension - stroke * 2 - 40.dp.toPx()
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2 + 10f)
            val c = Offset(tl.x + d / 2, tl.y + d / 2)
            drawArc(Palette.Stroke, start, span, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            speedMarks.forEach { m ->
                val a = start + span * speedFraction(m.toDouble()) + 90f
                drawLine(Palette.StrokeHi, polar(c, d / 2 - stroke, a), polar(c, d / 2 - stroke * 1.6f, a), 2f)
                val layout = measurer.measure(m.toString(), style)
                val p = polar(c, d / 2 - stroke * 2.6f, a)
                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
            }
            if (f > 0.002f) {
                rotate(start - CAP_PAD, c) {
                    drawArc(
                        Brush.sweepGradient(
                            0f to color.copy(alpha = 0.25f), (CAP_PAD / 360f) to color.copy(alpha = 0.25f),
                            ((CAP_PAD + span) / 360f) to color, center = c,
                        ),
                        CAP_PAD, span * f, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                drawArc(color.copy(alpha = 0.18f), start, span * f, false, tl, Size(d, d), style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
                val tip = polar(c, d / 2, start + span * f + 90f)
                drawCircle(Color.White.copy(alpha = 0.25f), stroke * 0.95f, tip)
                drawCircle(Color.White, stroke * 0.42f, tip)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 20.dp)) {
            Text(centerTop.uppercase(), style = TextStyles.overline)
            Text(centerValue, style = TextStyles.metricLarge)
            Text(centerUnit, style = TextStyles.unit)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Compass arrow pointing at the tower
// ---------------------------------------------------------------------------------------------

@Composable
fun CompassArrow(bearingDeg: Double?, headingDeg: Float?, modifier: Modifier = Modifier, color: Color = Palette.Cyan) {
    val target = if (bearingDeg != null) (bearingDeg - (headingDeg ?: 0f)).toFloat() else 0f
    val rot by animateFloatAsState(target, tween(400))
    val measurer = rememberTextMeasurer()
    val nStyle = TextStyle(color = Palette.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2 * 0.9f
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.12f), Color.Transparent), c, r), r, c)
        drawCircle(Palette.StrokeHi, r, c, style = Stroke(1.5f))
        for (d in 0 until 360 step 30) drawLine(Palette.StrokeHi, polar(c, r, d - (headingDeg ?: 0f)), polar(c, r - 6f, d - (headingDeg ?: 0f)), 1.5f)
        val nLayout = measurer.measure("N", nStyle)
        val np = polar(c, r - 16f, -(headingDeg ?: 0f))
        drawText(nLayout, topLeft = Offset(np.x - nLayout.size.width / 2f, np.y - nLayout.size.height / 2f))
        if (bearingDeg == null) {
            drawCircle(Palette.TextFaint, 4f, c)
            return@Canvas
        }
        rotate(rot, c) {
            val arrow = Path().apply {
                moveTo(c.x, c.y - r * 0.72f)
                lineTo(c.x + r * 0.22f, c.y + r * 0.28f)
                lineTo(c.x, c.y + r * 0.12f)
                lineTo(c.x - r * 0.22f, c.y + r * 0.28f)
                close()
            }
            drawPath(arrow, Brush.verticalGradient(listOf(color, color.copy(alpha = 0.5f)), c.y - r * 0.72f, c.y + r * 0.3f))
            drawPath(arrow, Color.White.copy(alpha = 0.6f), style = Stroke(1.5f, join = StrokeJoin.Round))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Distance method comparison
// ---------------------------------------------------------------------------------------------

@Composable
fun DistanceComparison(estimates: List<DistanceEstimate>, units: UnitSystem, modifier: Modifier = Modifier) {
    val maxM = remember(estimates) { estimates.maxOfOrNull { it.meters + (it.uncertaintyM ?: 0.0) }?.coerceAtLeast(1.0) ?: 1.0 }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        estimates.forEach { e ->
            val color = when (e.confidence) {
                Confidence.HIGH -> Palette.Green
                Confidence.MEDIUM -> Palette.Cyan
                Confidence.LOW -> Palette.Amber
            }
            val frac by animateFloatAsState((e.meters / maxM).toFloat(), tween(700))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.method.label, fontSize = 13.sp, color = Palette.Text, modifier = Modifier.weight(1f))
                    Text(
                        Fmt.distance(e.meters, units) + (e.uncertaintyM?.let { " ±" + Fmt.distance(it, units) } ?: ""),
                        fontFamily = Mono, fontSize = 13.sp, color = color,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    drawRoundRect(Palette.Stroke, size = size, cornerRadius = CornerRadius(size.height / 2))
                    val w = size.width * frac
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(color.copy(alpha = 0.4f), color), 0f, max(w, 1f)),
                        size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2),
                    )
                    e.uncertaintyM?.let { u ->
                        val uw = (u / maxM).toFloat() * size.width
                        val x0 = (w - uw).coerceAtLeast(0f)
                        val x1 = (w + uw).coerceAtMost(size.width)
                        drawLine(Color.White.copy(alpha = 0.8f), Offset(x0, size.height / 2), Offset(x1, size.height / 2), 2f)
                        drawLine(Color.White.copy(alpha = 0.8f), Offset(x0, 0f), Offset(x0, size.height), 2f)
                        drawLine(Color.White.copy(alpha = 0.8f), Offset(x1, 0f), Offset(x1, size.height), 2f)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text("${e.confidence.label} confidence · ${e.detail}", fontSize = 11.sp, color = Palette.TextFaint)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Horizontal comparison bars (neighbors / speeds)
// ---------------------------------------------------------------------------------------------

data class BarDatum(val label: String, val value: Double, val display: String, val color: Color, val highlight: Boolean = false)

@Composable
fun HorizontalBars(data: List<BarDatum>, minValue: Double, maxValue: Double, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.forEach { d ->
            val frac by animateFloatAsState(((d.value - minValue) / (maxValue - minValue)).toFloat().coerceIn(0.02f, 1f), tween(600))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.label, fontSize = 12.sp, color = if (d.highlight) Palette.Text else Palette.TextDim,
                    fontWeight = if (d.highlight) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.width(96.dp), maxLines = 1,
                )
                Canvas(Modifier.weight(1f).height(12.dp)) {
                    drawRoundRect(Palette.Stroke.copy(alpha = 0.6f), size = size, cornerRadius = CornerRadius(6f))
                    drawRoundRect(
                        if (d.highlight) Brush.horizontalGradient(listOf(d.color.copy(alpha = 0.5f), d.color))
                        else Brush.horizontalGradient(listOf(d.color.copy(alpha = 0.25f), d.color.copy(alpha = 0.6f))),
                        size = Size(size.width * frac, size.height), cornerRadius = CornerRadius(6f),
                    )
                }
                Text(d.display, fontFamily = Mono, fontSize = 12.sp, color = Palette.Text, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.width(92.dp).padding(start = 8.dp))
            }
        }
    }
}

/** Shimmering placeholder used while acquiring. */
@Composable
fun ScanningLabel(text: String, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition()
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse))
    Text(text, color = Palette.Cyan.copy(alpha = a), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = modifier)
}

fun angleDiff(a: Float, b: Float): Float = abs(((b - a + 540f) % 360f) - 180f)
