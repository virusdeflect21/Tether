package dev.tether.core.design

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen atmospheric backdrop. It samples the current artwork when one is
 * available and layers a restrained mesh of brand light over it. The result is
 * a single, consistent stage for every screen instead of a flat app background.
 */
@Composable
fun TetherBackdrop(
    artworkUri: String? = null,
    seed: String = "tether",
    title: String = "Tether",
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalTetherDarkTheme.current
    val base = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier
            .fillMaxSize()
            .background(base),
    ) {
        if (!artworkUri.isNullOrBlank()) {
            val blurModifier = if (Build.VERSION.SDK_INT >= 31) Modifier.blur(86.dp) else Modifier
            TetherCover(
                artworkUri = artworkUri,
                seed = seed,
                title = title,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (dark) 0.55f else 0.24f)
                    .then(blurModifier),
                cornerRadius = 0.dp,
                initialsSize = 1.sp,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                base.copy(alpha = if (dark) 0.46f else 0.58f),
                                base.copy(alpha = if (dark) 0.78f else 0.86f),
                                base.copy(alpha = 0.98f),
                            ),
                        ),
                    ),
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val glow = if (dark) 0.26f else 0.16f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(primary.copy(alpha = glow), Color.Transparent),
                    center = Offset(width * 0.10f, height * 0.05f),
                    radius = width * 0.95f,
                ),
                radius = width * 0.95f,
                center = Offset(width * 0.10f, height * 0.05f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(tertiary.copy(alpha = glow * 0.55f), Color.Transparent),
                    center = Offset(width * 0.98f, height * 0.34f),
                    radius = width * 0.72f,
                ),
                radius = width * 0.72f,
                center = Offset(width * 0.98f, height * 0.34f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(secondary.copy(alpha = glow * 0.42f), Color.Transparent),
                    center = Offset(width * 0.20f, height * 0.92f),
                    radius = width * 0.90f,
                ),
                radius = width * 0.90f,
                center = Offset(width * 0.20f, height * 0.92f),
            )
        }

        content()
    }
}

/**
 * Frosted panel used for navigation, the mini player, cards and controls.
 * It is deliberately translucent rather than an opaque Material surface: the
 * backdrop stays visible through every layer.
 */
@Composable
fun TetherGlass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tone: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalTetherDarkTheme.current
    val highlight = if (dark) Color.White else Color.Black
    val tint = tone ?: MaterialTheme.colorScheme.background
    val top = highlight.copy(alpha = if (dark) 0.115f else 0.28f)
    val bottom = highlight.copy(alpha = if (dark) 0.035f else 0.08f)
    val border = Brush.linearGradient(
        listOf(
            highlight.copy(alpha = if (dark) 0.26f else 0.28f),
            highlight.copy(alpha = if (dark) 0.06f else 0.08f),
            highlight.copy(alpha = if (dark) 0.12f else 0.14f),
        ),
    )
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(top, bottom),
                ),
            )
            .background(tint.copy(alpha = if (dark) 0.34f else 0.58f))
            .border(borderWidth, border, shape)
            .padding(contentPadding),
        content = content,
    )
}
