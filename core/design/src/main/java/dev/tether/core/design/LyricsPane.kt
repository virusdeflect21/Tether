package dev.tether.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tether.core.model.LyricsDocument
import kotlin.math.abs

/**
 * Renders real lyrics only. Synced lyrics highlight and auto-scroll; plain lyrics
 * stay still and never pretend to be timed. Tap a timed line to seek to it.
 */
@Composable
fun LyricsPane(
    document: LyricsDocument,
    positionMs: Long,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 19.sp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 120.dp),
    onSeek: ((Long) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val activeIndex = if (document.synced) {
        document.lines.indexOfLast { line -> (line.timeMs ?: Long.MAX_VALUE) <= positionMs + 120 }
    } else {
        -1
    }
    LaunchedEffect(activeIndex, document.trackId) {
        if (document.synced && activeIndex >= 0) {
            val target = (activeIndex - 2).coerceAtLeast(0)
            if (abs(listState.firstVisibleItemIndex - target) > 1) {
                listState.animateScrollToItem(target)
            }
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(if (document.synced) 18.dp else 12.dp),
    ) {
        itemsIndexed(document.lines) { index, line ->
            val active = index == activeIndex
            val seekTo = line.timeMs
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (active) Modifier.background(activeColor.copy(alpha = 0.10f)) else Modifier,
                    )
                    .then(
                        if (onSeek != null && seekTo != null) {
                            Modifier.clickable { onSeek(seekTo) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = line.text.ifBlank { " " },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = textSize,
                        lineHeight = textSize * 1.42f,
                    ),
                    color = when {
                        document.synced && active -> activeColor
                        document.synced -> inactiveColor.copy(alpha = 0.72f)
                        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f)
                    },
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
