package dev.tether.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.abs

private val coverPalettes = listOf(
    listOf(Color(0xFF16205A), Color(0xFF6655D9), Color(0xFFD7C7FF)),
    listOf(Color(0xFF22103B), Color(0xFFA32C8D), Color(0xFFFFC2E5)),
    listOf(Color(0xFF062B2A), Color(0xFF087C71), Color(0xFFB6FFE9)),
    listOf(Color(0xFF2E1A0B), Color(0xFFB9662B), Color(0xFFFFD9A8)),
    listOf(Color(0xFF181A3D), Color(0xFF3457C9), Color(0xFF9FE0FF)),
    listOf(Color(0xFF2C0E24), Color(0xFFB02A5B), Color(0xFFFFB8C7)),
    listOf(Color(0xFF0C2038), Color(0xFF147A9E), Color(0xFFB9F1FF)),
    listOf(Color(0xFF251F05), Color(0xFF948018), Color(0xFFFFEDAA)),
)

private fun paletteFor(seed: String): List<Color> {
    val index = abs(seed.hashCode()) % coverPalettes.size
    return coverPalettes[index]
}

/**
 * Album artwork with a deterministic generative fallback. The fallback uses only
 * the track's own identity, so the same album keeps the same cover everywhere.
 * Covers are treated as physical objects: a clipped sheen edge keeps the artwork
 * from dissolving into the glass surfaces around it.
 */
@Composable
fun TetherCover(
    artworkUri: String?,
    seed: String,
    title: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    initialsSize: TextUnit = 28.sp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val edge = if (cornerRadius > 0.dp) {
        Modifier.border(1.dp, Color.White.copy(alpha = 0.13f), shape)
    } else {
        Modifier
    }
    Box(
        modifier
            .clip(shape)
            .background(if (artworkUri.isNullOrBlank()) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
            .then(edge),
    ) {
        if (artworkUri.isNullOrBlank()) {
            GeneratedCover(seed, title, initialsSize)
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { GeneratedCover(seed, title, initialsSize) },
                error = { GeneratedCover(seed, title, initialsSize) },
            )
        }
    }
}

@Composable
private fun GeneratedCover(seed: String, title: String, initialsSize: TextUnit) {
    val palette = remember(seed) { paletteFor(seed) }
    val monogram = remember(title) {
        title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(1)
            .joinToString("") { it.take(1).uppercase() }
            .ifBlank { "\u266a" }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.linearGradient(
                    colors = palette,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(size.width * 0.78f, size.height * 0.16f),
                    radius = size.minDimension * 0.62f,
                ),
                radius = size.minDimension * 0.62f,
                center = Offset(size.width * 0.78f, size.height * 0.16f),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.14f),
                radius = size.minDimension * 0.47f,
                center = Offset(size.width * 0.18f, size.height * 0.92f),
            )
            listOf(0.56f, 0.68f, 0.80f).forEachIndexed { index, fraction ->
                drawArc(
                    color = Color.White.copy(alpha = 0.10f + index * 0.025f),
                    startAngle = 200f,
                    sweepAngle = 118f,
                    useCenter = false,
                    topLeft = Offset(
                        size.width * (0.5f - fraction / 2f),
                        size.height * (0.5f - fraction / 2f),
                    ),
                    size = Size(size.width * fraction, size.height * fraction),
                    style = Stroke(width = size.minDimension * 0.008f, cap = StrokeCap.Round),
                )
            }
            for (index in 0 until 7) {
                val x = size.width * (0.12f + index * 0.13f)
                val amplitude = size.height * (0.035f + (index % 3) * 0.022f)
                drawLine(
                    color = Color.White.copy(alpha = 0.16f),
                    start = Offset(x, size.height * 0.78f - amplitude),
                    end = Offset(x, size.height * 0.78f + amplitude),
                    strokeWidth = size.minDimension * 0.012f,
                    cap = StrokeCap.Round,
                )
            }
            drawLine(
                color = Color.White.copy(alpha = 0.22f),
                start = Offset(size.width * 0.14f, size.height * 0.66f),
                end = Offset(size.width * 0.86f, size.height * 0.22f),
                strokeWidth = size.minDimension * 0.006f,
                cap = StrokeCap.Round,
            )
        }
        Text(
            text = monogram,
            modifier = Modifier.align(Alignment.Center),
            color = Color.White.copy(alpha = 0.18f),
            fontSize = initialsSize * 2.1f,
            lineHeight = initialsSize * 2.1f,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
