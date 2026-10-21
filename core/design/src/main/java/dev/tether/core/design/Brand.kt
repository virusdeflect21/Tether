package dev.tether.core.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Tether feather mark. The bitmap already carries its own gradient tile, so
 * it is clipped to slightly round the corners without repainting the artwork.
 */
@Composable
fun TetherMark(
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = size * 0.24f,
) {
    Image(
        painter = painterResource(R.drawable.tether_logo),
        contentDescription = "Tether",
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun TetherWordmark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showTagline: Boolean = true,
    titleSize: Int = 28,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "TETHER",
            color = color,
            fontSize = titleSize.sp,
            lineHeight = (titleSize * 1.05f).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (titleSize * 0.16f).sp,
        )
        if (showTagline) {
            Text(
                text = "MUSIC, YOUR WAY",
                color = subtitleColor,
                fontSize = (titleSize * 0.28f).coerceAtLeast(10f).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (titleSize * 0.06f).sp,
            )
        }
    }
}

@Composable
fun TetherBrandLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 48.dp,
    titleSize: Int = 22,
    showTagline: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TetherMark(markSize)
        Spacer(Modifier.width(12.dp))
        TetherWordmark(titleSize = titleSize, showTagline = showTagline)
    }
}
