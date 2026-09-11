package org.pashri.bustimes.ui.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Amber for late, green for early or on time; neutral when unknown. */
private val LateColour = Color(0xFFB4530A)
private val EarlyColour = Color(0xFF1B5E20)

/** Diameter of the status dot placed before the lateness text. */
private val DOT_SIZE = 6.dp

/**
 * Renders a one-line summary of how a service is running.
 *
 * A coloured dot precedes the text, so status is legible from colour alone
 * and not only from wording — useful at a glance across a list of rows.
 *
 * @param lateness the assessed lateness.
 * @param stale true when the figure was computed from a position old enough
 *   that it should no longer look authoritative. The words are unchanged and
 *   only the colour is dropped: a green "on time" reads as a promise about
 *   now, whereas the same words in grey are plainly a report about earlier.
 * @param modifier layout modifier.
 */
@Composable
fun LatenessLabel(
    lateness: Lateness,
    stale: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val text = describe(lateness) ?: return
    val colour = when {
        stale -> MaterialTheme.colorScheme.onSurfaceVariant
        lateness is Lateness.Late -> LateColour
        lateness is Lateness.Early || lateness == Lateness.OnTime -> EarlyColour
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Row(modifier = Modifier.size(DOT_SIZE).background(color = colour, shape = CircleShape)) {}
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colour,
        )
    }
}

/**
 * Puts lateness into words.
 *
 * @param lateness the assessed lateness.
 * @return the label, or null when there is nothing worth saying.
 */
fun describe(lateness: Lateness): String? = when (lateness) {
    Lateness.Unknown -> null
    Lateness.OnTime -> "On time"
    is Lateness.Late -> "${lateness.minutes} min late"
    is Lateness.Early -> "${lateness.minutes} min early"
}
