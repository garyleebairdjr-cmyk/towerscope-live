package io.towerscope.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.towerscope.calc.Grade
import io.towerscope.model.RadioType

object Palette {
    val Bg = Color(0xFF060A13)
    val BgTop = Color(0xFF0B1324)
    val Surface = Color(0xFF0E1627)
    val SurfaceHi = Color(0xFF142039)
    val Stroke = Color(0xFF1F2E4D)
    val StrokeHi = Color(0xFF2C406A)
    val Text = Color(0xFFE7EEF9)
    val TextDim = Color(0xFF8D9CB8)
    val TextFaint = Color(0xFF56658A)

    val Cyan = Color(0xFF22D3EE)
    val Blue = Color(0xFF60A5FA)
    val Violet = Color(0xFFA78BFA)
    val Green = Color(0xFF34D399)
    val Lime = Color(0xFFA3E635)
    val Amber = Color(0xFFFBBF24)
    val Orange = Color(0xFFFB923C)
    val Red = Color(0xFFF87171)
    val Pink = Color(0xFFF472B6)

    fun grade(g: Grade): Color = when (g) {
        Grade.EXCELLENT -> Green
        Grade.GOOD -> Lime
        Grade.FAIR -> Amber
        Grade.POOR -> Orange
        Grade.BAD -> Red
        Grade.UNKNOWN -> TextFaint
    }

    fun radio(r: RadioType): Color = when (r) {
        RadioType.NR -> Violet
        RadioType.LTE -> Cyan
        RadioType.WCDMA, RadioType.TDSCDMA -> Blue
        RadioType.GSM -> Amber
        RadioType.CDMA -> Orange
        RadioType.UNKNOWN -> TextDim
    }
}

val Mono = FontFamily.Monospace

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium),
    )
}

object TextStyles {
    val metric = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Palette.Text)
    val metricLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Light, fontSize = 44.sp, color = Palette.Text, letterSpacing = (-1).sp)
    val unit = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Palette.TextDim)
    val overline = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp, color = Palette.TextDim)
    val mono = TextStyle(fontFamily = Mono, fontSize = 14.sp, color = Palette.Text)
}

@Composable
fun TowerScopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Cyan,
            onPrimary = Color(0xFF00222A),
            primaryContainer = Color(0xFF0B3A47),
            onPrimaryContainer = Palette.Cyan,
            secondary = Palette.Violet,
            tertiary = Palette.Green,
            background = Palette.Bg,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.SurfaceHi,
            onSurfaceVariant = Palette.TextDim,
            surfaceContainer = Palette.Surface,
            surfaceContainerHigh = Palette.SurfaceHi,
            surfaceContainerHighest = Palette.SurfaceHi,
            outline = Palette.StrokeHi,
            outlineVariant = Palette.Stroke,
            error = Palette.Red,
        ),
        typography = AppTypography,
        content = content,
    )
}
