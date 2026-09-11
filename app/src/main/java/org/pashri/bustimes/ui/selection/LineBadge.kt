package org.pashri.bustimes.ui.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Corner radius that reads as a pill at the badge's usual height. */
private val BADGE_SHAPE = RoundedCornerShape(50)

/**
 * A line number, shown as a filled pill in the app's accent colour.
 *
 * Used everywhere a line name appears — the departure board, the journey
 * header, and the timetable — so a line reads the same wherever it shows up.
 *
 * @param lineName the line's published name, e.g. "12" or "X5".
 * @param modifier layout modifier.
 */
@Composable
fun LineBadge(lineName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color = MaterialTheme.colorScheme.tertiary, shape = BADGE_SHAPE)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = lineName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiary,
        )
    }
}
