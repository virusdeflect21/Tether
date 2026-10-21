package dev.tether.core.design

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tether.core.model.ThemeMode

/** Brand palette sampled from the Tether feather mark. */
val TetherInk = Color(0xFF07070F)
val TetherInkSoft = Color(0xFF0D0D18)
val TetherSurface = Color(0xFF13131F)
val TetherSurfaceHigh = Color(0xFF1B1B2B)
val TetherLine = Color(0xFF2A2A3D)
val TetherBlue = Color(0xFF8FA6FF)
val TetherViolet = Color(0xFFC3A6FF)
val TetherMagenta = Color(0xFFF0A6FF)
val TetherText = Color(0xFFF5F3FF)
val TetherTextDim = Color(0xFFA8A6BC)
val TetherDanger = Color(0xFFFF8A9A)
val TetherSuccess = Color(0xFF8DE3C0)
val TetherPaper = Color(0xFFF7F5FB)
val TetherPaperSurface = Color(0xFFFFFFFF)

val TetherShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val TetherTypography
    @Composable get() = androidx.compose.material3.Typography(
        displayLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 52.sp,
            lineHeight = 58.sp,
            letterSpacing = (-1.2).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            letterSpacing = (-0.8).sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.5).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.3).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp,
            lineHeight = 27.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 23.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.2.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        ),
    )

@Composable
fun TetherTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    customAccent: String? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val accent = rememberAccent(customAccent)
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent ?: TetherBlue,
            onPrimary = Color(0xFF0A1030),
            primaryContainer = Color(0xFF20264A),
            onPrimaryContainer = Color(0xFFDCE2FF),
            secondary = TetherViolet,
            onSecondary = Color(0xFF1E1233),
            secondaryContainer = Color(0xFF2B2141),
            onSecondaryContainer = Color(0xFFEFE3FF),
            tertiary = TetherMagenta,
            onTertiary = Color(0xFF2A0A2E),
            background = TetherInk,
            onBackground = TetherText,
            surface = TetherSurface,
            onSurface = TetherText,
            surfaceVariant = TetherSurfaceHigh,
            onSurfaceVariant = TetherTextDim,
            outline = TetherLine,
            outlineVariant = Color(0xFF202030),
            error = TetherDanger,
            onError = Color(0xFF35080E),
            errorContainer = Color(0xFF4A1620),
            onErrorContainer = Color(0xFFFFD9DE),
            scrim = Color(0xFF000000),
        )
    } else {
        lightColorScheme(
            primary = accent ?: Color(0xFF4258D8),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE0E4FF),
            onPrimaryContainer = Color(0xFF111A55),
            secondary = Color(0xFF7137B8),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFEDDCFF),
            onSecondaryContainer = Color(0xFF28103F),
            tertiary = Color(0xFF9B2C8A),
            onTertiary = Color(0xFFFFFFFF),
            background = TetherPaper,
            onBackground = Color(0xFF16141F),
            surface = TetherPaperSurface,
            onSurface = Color(0xFF16141F),
            surfaceVariant = Color(0xFFECE8F4),
            onSurfaceVariant = Color(0xFF5E5A6E),
            outline = Color(0xFFC9C3D8),
            outlineVariant = Color(0xFFE3DEEC),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            scrim = Color(0xFF000000),
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = TetherTypography,
        shapes = TetherShapes,
        content = content,
    )
}

@Composable
private fun rememberAccent(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    return runCatching { Color(AndroidColor.parseColor(value)) }.getOrNull()
}

val TetherAmber = TetherViolet
val TetherMint = TetherBlue
