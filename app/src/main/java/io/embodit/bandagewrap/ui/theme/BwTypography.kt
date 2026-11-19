package io.embodit.bandagewrap.ui.theme

import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Typography
import io.embodit.bandagewrap.R

/**
 * One type family for the whole app: Noto Sans for Latin and digits, Noto Sans SC for Chinese, both
 * bundled (SIL OFL 1.1, see third_party/). The system font only covers what both lack.
 *
 * Compose resolves a FontFamily to a single font per weight, so the per-glyph order
 * "Noto Sans -> Noto Sans SC -> system" is built as one platform Typeface with a custom fallback.
 * The Chinese font is a variable-font subset (tools/subset_cjk_font.py); one instance per weight.
 */
private val Weights = intArrayOf(400, 500, 600, 700)

private fun buildFontFamily(resources: Resources): FontFamily =
    try {
        fun latin(id: Int, weight: Int): PlatformFont = PlatformFont.Builder(resources, id).setWeight(weight).build()
        fun chinese(weight: Int): PlatformFont =
            PlatformFont.Builder(resources, R.font.noto_sans_sc_subset)
                .setFontVariationSettings("'wght' $weight")
                .setWeight(weight)
                .build()
        val latinFamily =
            PlatformFontFamily.Builder(latin(R.font.noto_sans_regular, 400))
                .addFont(latin(R.font.noto_sans_medium, 500))
                .addFont(latin(R.font.noto_sans_semibold, 600))
                .addFont(latin(R.font.noto_sans_bold, 700))
                .build()
        val chineseBuilder = PlatformFontFamily.Builder(chinese(Weights[0]))
        for (i in 1 until Weights.size) chineseBuilder.addFont(chinese(Weights[i]))
        val typeface =
            Typeface.CustomFallbackBuilder(latinFamily)
                .addCustomFallback(chineseBuilder.build())
                .setSystemFallback("sans-serif")
                .build()
        FontFamily(typeface)
    } catch (t: Throwable) {
        FontFamily(
            Font(R.font.noto_sans_regular, FontWeight.Normal),
            Font(R.font.noto_sans_medium, FontWeight.Medium),
            Font(R.font.noto_sans_semibold, FontWeight.SemiBold),
            Font(R.font.noto_sans_bold, FontWeight.Bold),
        )
    }

@Composable
internal fun rememberCtFontFamily(): FontFamily {
    val resources = LocalContext.current.resources
    return remember(resources) { buildFontFamily(resources) }
}

/** Keeps the PicoTheme type scale and swaps the family of every role. */
internal fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.inFamily(): TextStyle = copy(fontFamily = family)
    return copy(
        bodyLarge = bodyLarge.inFamily(),
        bodyLargeMultiline = bodyLargeMultiline.inFamily(),
        bodyMedium = bodyMedium.inFamily(),
        bodyMediumMultiline = bodyMediumMultiline.inFamily(),
        bodySmall = bodySmall.inFamily(),
        bodyTiny = bodyTiny.inFamily(),
        displayLarge = displayLarge.inFamily(),
        displayMedium = displayMedium.inFamily(),
        displaySmall = displaySmall.inFamily(),
        headlineLarge = headlineLarge.inFamily(),
        headlineMedium = headlineMedium.inFamily(),
        headlineSmall = headlineSmall.inFamily(),
        labelLarge = labelLarge.inFamily(),
        labelMedium = labelMedium.inFamily(),
        labelSmall = labelSmall.inFamily(),
        titleLarge = titleLarge.inFamily(),
        titleMedium = titleMedium.inFamily(),
        titleSmall = titleSmall.inFamily(),
    )
}



/**
 * Type roles of the design (typography): headlineLarge for the turn's line, titleMedium for titles, bodyLarge /
 * bodyMedium, labelLarge. Sizes are the design's × ~1.15: the system opens the window farther than the design's 0.85 m.
 */
object BwType {
    val big: androidx.compose.ui.text.TextStyle
        @Composable get() = PicoTheme.typography.headlineLarge.copy(fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)

    val bigEn: androidx.compose.ui.text.TextStyle
        @Composable get() = PicoTheme.typography.headlineLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)

    val titleLarge: androidx.compose.ui.text.TextStyle
        @Composable get() = PicoTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)

    val title: androidx.compose.ui.text.TextStyle
        @Composable get() = PicoTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)

    val body: androidx.compose.ui.text.TextStyle
        @Composable get() = PicoTheme.typography.bodyLargeMultiline.copy(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium)

    val small: androidx.compose.ui.text.TextStyle
        @Composable get() = PicoTheme.typography.bodyMediumMultiline.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)

    val label: androidx.compose.ui.text.TextStyle
        @Composable get() = PicoTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)

    val button: androidx.compose.ui.text.TextStyle
        @Composable get() = PicoTheme.typography.labelLarge.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)

    /** The 3D turn label: first line (turn n / N) and the short cue. */
    val tagTop: androidx.compose.ui.text.TextStyle
        @Composable get() = PicoTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

    val tagSub: androidx.compose.ui.text.TextStyle
        @Composable get() = PicoTheme.typography.labelLarge.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
}
