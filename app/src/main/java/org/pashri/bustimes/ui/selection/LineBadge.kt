package org.pashri.bustimes.ui.selection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Corner radius for the badge's outline. */
private val BADGE_SHAPE = RoundedCornerShape(8.dp)

/** Width every badge occupies, regardless of digit count, so rows align. */
private val BADGE_MIN_WIDTH = 44.dp

/**
 * A line number, shown as an outlined box in the app's accent colour.
 *
 * Used everywhere a line name appears — the departure board, the journey
 * header, and the timetable — so a line reads the same wherever it shows up.
 * Every badge shares [BADGE_MIN_WIDTH], so a one-digit line and "376a" both
 * leave the destination text starting at the same x position.
 *
 * @param lineName the line's published name, e.g. "12" or "X5".
 * @param modifier layout modifier.
 */
@Composable
fun LineBadge(lineName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = BADGE_MIN_WIDTH)
            .clip(BADGE_SHAPE)
            .border(
                border = BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.tertiary),
                shape = BADGE_SHAPE,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = lineName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}
