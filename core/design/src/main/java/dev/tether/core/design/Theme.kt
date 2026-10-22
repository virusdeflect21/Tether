package dev.tether.core.design

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tether.core.model.ThemeMode

/** Brand palette sampled from the Tether feather mark. */
val TetherInk = Color(0xFF05060D)
val TetherInkSoft = Color(0xFF0A0B15)
val TetherSurface = Color(0xFF10111C)
val TetherSurfaceHigh = Color(0xFF191B29)
val TetherLine = Color(0xFF2B2D3F)
val TetherBlue = Color(0xFF8AA6FF)
val TetherViolet = Color(0xFFBFA8FF)
val TetherMagenta = Color(0xFFFF9ED4)
val TetherText = Color(0xFFF7F5FF)
val TetherTextDim = Color(0xFFA9A7BA)
val TetherDanger = Color(0xFFFF8FA0)
val TetherSuccess = Color(0xFF8DE3C0)
val TetherPaper = Color(0xFFF4F2FA)
val TetherPaperSurface = Color(0xFFFFFFFF)

val TetherGlassBorder = Color(0x2EFFFFFF)
val TetherGlassHighlight = Color(0x1FFFFFFF)

val LocalTetherDarkTheme = staticCompositionLocalOf { true }

private val TetherFont = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

val TetherShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val TetherTypography
    @Composable get() = androidx.compose.material3.Typography(
        displayLarge = TextStyle(
            fontFamily = TetherFont,
            fontWeight = FontWeight.Bold,
            fontSize = 48.sp,
            lineHeight = 54.sp,
            letterSpacing = (-1.4).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = TetherFont,
            fontWeight = FontWeight.Bold,
            fontSize = 37.sp,
            lineHeight = 44.sp,
            letterSpacing = (-1.0).sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = TetherFont,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.6).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = TetherFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.35).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = TetherFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.15).sp,
        ),
        titleMedium = TextStyle(
            fontFamily = TetherFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = TetherFont,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = TetherFont,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 21.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = TetherFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.2.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = TetherFont,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = TetherFont,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.6.sp,
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
            onPrimary = Color(0xFF081130),
            primaryContainer = Color(0xFF25305A),
            onPrimaryContainer = Color(0xFFE2E7FF),
            secondary = TetherViolet,
            onSecondary = Color(0xFF1C1033),
            secondaryContainer = Color(0xFF2B2145),
            onSecondaryContainer = Color(0xFFF0E6FF),
            tertiary = TetherMagenta,
            onTertiary = Color(0xFF2A0A22),
            background = TetherInk,
            onBackground = TetherText,
            surface = TetherSurface,
            onSurface = TetherText,
            surfaceVariant = TetherSurfaceHigh,
            onSurfaceVariant = TetherTextDim,
            outline = TetherLine,
            outlineVariant = Color(0xFF222333),
            error = TetherDanger,
            onError = Color(0xFF35080E),
            errorContainer = Color(0xFF4A1620),
            onErrorContainer = Color(0xFFFFD9DE),
            scrim = Color(0xFF000000),
        )
    } else {
        lightColorScheme(
            primary = accent ?: Color(0xFF3D55D6),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE0E4FF),
            onPrimaryContainer = Color(0xFF111A55),
            secondary = Color(0xFF7137B8),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFEDDCFF),
            onSecondaryContainer = Color(0xFF28103F),
            tertiary = Color(0xFFB02B87),
            onTertiary = Color(0xFFFFFFFF),
            background = TetherPaper,
            onBackground = Color(0xFF17151F),
            surface = TetherPaperSurface,
            onSurface = Color(0xFF17151F),
            surfaceVariant = Color(0xFFECE8F4),
            onSurfaceVariant = Color(0xFF5D596E),
            outline = Color(0xFFC7C1D6),
            outlineVariant = Color(0xFFE3DEEC),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            scrim = Color(0xFF000000),
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalTetherDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TetherTypography,
            shapes = TetherShapes,
            content = content,
        )
    }
}

@Composable
private fun rememberAccent(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    return runCatching { Color(AndroidColor.parseColor(value)) }.getOrNull()
}

val TetherAmber = TetherViolet
val TetherMint = TetherBlue
