package io.towerscope.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.towerscope.calc.Grade
import io.towerscope.model.RadioType
import io.towerscope.ui.theme.Mono
import io.towerscope.ui.theme.Palette
import io.towerscope.ui.theme.TextStyles

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .clip(CardShape)
        .background(Brush.verticalGradient(listOf(Palette.SurfaceHi, Palette.Surface)))
        .border(
            1.dp,
            Brush.verticalGradient(listOf((accent ?: Palette.StrokeHi).copy(alpha = if (accent != null) 0.55f else 0.9f), Palette.Stroke.copy(alpha = 0.4f))),
            CardShape,
        )
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(contentPadding)
    Column(base, content = content)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = TextStyles.overline)
            subtitle?.let { Text(it, fontSize = 12.sp, color = Palette.TextFaint) }
        }
        trailing?.invoke()
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = if (filled) 1f else 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = if (filled) Palette.Bg else color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}

@Composable
fun RadioBadge(radio: RadioType, label: String = radio.label, modifier: Modifier = Modifier) =
    Pill(label, Palette.radio(radio), modifier)

@Composable
fun GradePill(grade: Grade, modifier: Modifier = Modifier) = Pill(grade.label, Palette.grade(grade), modifier)

@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val t = rememberInfiniteTransition()
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart))
    Canvas(modifier.size(size * 2.4f)) {
        val r = this.size.minDimension / 2
        drawCircle(color.copy(alpha = (1 - p) * 0.5f), radius = r * (0.4f + 0.6f * p))
        drawCircle(color, radius = r * 0.42f)
    }
}

@Composable
fun SignalBars(level: Int?, color: Color, modifier: Modifier = Modifier.size(28.dp, 20.dp)) {
    Canvas(modifier) {
        val n = 5
        val gap = size.width * 0.08f
        val w = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val h = size.height * (0.28f + 0.72f * (i + 1) / n)
            val on = level != null && i <= level
            drawRoundRect(
                if (on) color else Palette.Stroke,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 3),
            )
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = Palette.Cyan,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    GlassCard(modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(label.uppercase(), style = TextStyles.overline, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = TextStyles.metric, maxLines = 1)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = TextStyles.unit, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        caption?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, fontSize = 11.sp, color = Palette.TextFaint, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    valueColor: Color = Palette.Text,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = Palette.TextDim)
            hint?.let { Text(it, fontSize = 11.sp, color = Palette.TextFaint) }
        }
        Spacer(Modifier.width(12.dp))
        Text(value, fontFamily = Mono, fontSize = 14.sp, color = valueColor, fontWeight = FontWeight.Medium)
        if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Palette.TextFaint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Stroke.copy(alpha = 0.6f)))

@Composable
fun TileRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)

@Composable
fun DrillRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { it(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = Palette.Text, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, fontSize = 12.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.invoke()
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Palette.TextFaint)
    }
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, color: Color = Palette.Blue, icon: ImageVector? = null) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 13.sp, color = Palette.TextDim, lineHeight = 18.sp)
    }
}
