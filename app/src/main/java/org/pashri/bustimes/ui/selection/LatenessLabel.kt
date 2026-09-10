package org.pashri.bustimes.ui.selection

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/** Amber for late, green for early or on time; neutral when unknown. */
private val LateColour = Color(0xFFB4530A)
private val EarlyColour = Color(0xFF1B5E20)

/**
 * Renders a one-line summary of how a service is running.
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
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = when {
            stale -> MaterialTheme.colorScheme.onSurfaceVariant
            lateness is Lateness.Late -> LateColour
            lateness is Lateness.Early || lateness == Lateness.OnTime -> EarlyColour
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
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
