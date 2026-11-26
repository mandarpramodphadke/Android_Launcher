package com.ailauncher.presentation.home

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ailauncher.domain.model.BatteryGlance
import com.ailauncher.domain.model.ClockFace
import com.ailauncher.domain.model.ClockFont
import com.ailauncher.presentation.theme.LauncherStyle
import com.ailauncher.presentation.theme.WallpaperTint
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The typeface of [font], or null for the theme's default. */
internal fun clockFontFamily(font: ClockFont): FontFamily? = when (font) {
    ClockFont.DEFAULT, ClockFont.BOLD -> null
    ClockFont.CONDENSED -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    ClockFont.SERIF -> FontFamily.Serif
    ClockFont.MONO -> FontFamily.Monospace
}

/** Applies the clock font to a clock style (BOLD makes the thin clock heavy). */
internal fun TextStyle.withClockFont(font: ClockFont): TextStyle {
    val family = clockFontFamily(font)
    val withFamily = if (family != null) copy(fontFamily = family) else this
    return if (font == ClockFont.BOLD) withFamily.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp) else withFamily
}

/** "Thursday, 26 November · 82 %" (with "charging" while plugged in). */
internal fun dateLine(now: ZonedDateTime, locale: Locale, battery: BatteryGlance?, pattern: String = "EEEE, d MMMM"): String {
    val date = now.format(DateTimeFormatter.ofPattern(pattern, locale))
    if (battery == null) return date
    val level = if (battery.charging) "${battery.percent}% charging" else "${battery.percent}%"
    return "$date · $level"
}

/** The clock faces other than the classic one (see [ClockWidget]). */
@Composable
internal fun StyledClock(now: ZonedDateTime, use24Hour: Boolean, tint: WallpaperTint, style: LauncherStyle, battery: BatteryGlance?, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dateStyle = MaterialTheme.typography.titleMedium.copy(shadow = tint.shadow, fontWeight = FontWeight.Medium)
    val spoken = now.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale))
    val modifier = Modifier
        .clickable(onClick = onClick, onClickLabel = "Open clock")
        .semantics { contentDescription = "$spoken, ${dateLine(now, locale, battery)}" }
    when (style.clockFace) {
        ClockFace.STACKED -> Column(modifier) {
            val digits = TextStyle(
                fontSize = 54.sp,
                lineHeight = 52.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1).sp,
                color = tint.content,
                shadow = tint.shadow,
            ).withClockFont(style.clockFont)
            Text(now.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH" else "hh", locale)), style = digits)
            Text(now.format(DateTimeFormatter.ofPattern("mm", locale)), style = digits.copy(color = tint.secondary))
            Text(dateLine(now, locale, battery, "EEE, d MMM"), style = dateStyle, color = tint.secondary)
        }
        ClockFace.ANALOG -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            AnalogDial(now, tint, Modifier.size(104.dp))
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    now.format(DateTimeFormatter.ofPattern("EEEE", locale)),
                    style = MaterialTheme.typography.headlineSmall.copy(shadow = tint.shadow, fontWeight = FontWeight.Medium).withClockFont(style.clockFont),
                    color = tint.content,
                )
                Text(now.format(DateTimeFormatter.ofPattern("d MMMM", locale)), style = dateStyle, color = tint.secondary)
                battery?.let { Text(if (it.charging) "${it.percent}% · charging" else "${it.percent}%", style = dateStyle, color = tint.secondary) }
            }
        }
        ClockFace.WORDS -> Column(modifier) {
            val (hour, minutes) = timeInWords(now)
            val words = TextStyle(fontSize = 36.sp, lineHeight = 40.sp, color = tint.content, shadow = tint.shadow).withClockFont(style.clockFont)
            Text(hour, style = words.copy(fontWeight = if (style.clockFont == ClockFont.BOLD) FontWeight.Black else FontWeight.SemiBold))
            Text(minutes, style = words.copy(fontWeight = if (style.clockFont == ClockFont.BOLD) FontWeight.Bold else FontWeight.Light, color = tint.secondary))
            Spacer(Modifier.size(2.dp))
            Text(dateLine(now, locale, battery), style = dateStyle, color = tint.secondary)
        }
        ClockFace.SLIM -> Row(modifier, verticalAlignment = Alignment.Bottom) {
            Text(
                now.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm", locale)),
                style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Thin, color = tint.content, shadow = tint.shadow).withClockFont(style.clockFont),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                dateLine(now, locale, battery, "EEE d MMM"),
                style = dateStyle,
                color = tint.secondary,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }
        ClockFace.CLASSIC -> Unit // drawn by ClockWidget itself
    }
}

/** A clock dial with hour ticks and two hands, in the wallpaper text colours. */
@Composable
private fun AnalogDial(now: ZonedDateTime, tint: WallpaperTint, modifier: Modifier) {
    val content = tint.content
    val secondary = tint.secondary
    val backing = tint.chip
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(backing, radius = r)
        drawCircle(content.copy(alpha = 0.55f), radius = r - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        for (i in 0 until 12) {
            val angle = i * PI / 6
            val outer = r - 6.dp.toPx()
            val inner = r - if (i % 3 == 0) 14.dp.toPx() else 10.dp.toPx()
            drawLine(
                if (i % 3 == 0) content else secondary,
                start = c + polar(angle, inner),
                end = c + polar(angle, outer),
                strokeWidth = if (i % 3 == 0) 2.5.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val minutes = now.minute + now.second / 60.0
        val hours = (now.hour % 12) + minutes / 60.0
        hand(c, hours * PI / 6, r * 0.5f, 4.dp.toPx(), content)
        hand(c, minutes * PI / 30, r * 0.74f, 2.5.dp.toPx(), content)
        drawCircle(content, radius = 3.5.dp.toPx(), center = c)
    }
}

private fun DrawScope.hand(center: Offset, angle: Double, length: Float, width: Float, color: Color) {
    drawLine(color, start = center, end = center + polar(angle, length), strokeWidth = width, cap = StrokeCap.Round)
}

/** A point at [radius] from the centre, [angle] radians clockwise from twelve o'clock. */
private fun polar(angle: Double, radius: Float): Offset = Offset((sin(angle) * radius).toFloat(), (-cos(angle) * radius).toFloat())

private val ONES = listOf("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty")

/** "Four" / "thirty-seven", "Twelve" / "o'clock", "Nine" / "oh five" (12-hour, English). */
internal fun timeInWords(now: ZonedDateTime): Pair<String, String> {
    val hour12 = (now.hour % 12).let { if (it == 0) 12 else it }
    val m = now.minute
    val minutes = when {
        m == 0 -> "o'clock"
        m < 10 -> "oh ${ONES[m]}"
        m < 20 -> ONES[m]
        m % 10 == 0 -> TENS[m / 10]
        else -> "${TENS[m / 10]}-${ONES[m % 10]}"
    }
    return ONES[hour12].replaceFirstChar { it.uppercase() } to minutes
}
