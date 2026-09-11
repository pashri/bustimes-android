package org.pashri.bustimes.ui.selection

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * OpenType feature enabling tabular (fixed-width) digits.
 *
 * Without it, a live time refreshing beside an aimed time shifts horizontally
 * as its digit widths change, which reads as jitter on every poll.
 */
private const val TABULAR_FIGURES = "tnum"

/** Times arrive as `HH:MM`, sometimes with seconds attached. */
private const val TIME_LENGTH = 5

/** How a scheduled time and a live time should be shown together. */
data class TimePresentation(
    val aimed: String,
    /** The live time, only when it differs from what is already shown. */
    val live: String?,
    val struckThrough: Boolean,
) {
    /** True when only one time is worth showing. */
    val isSingleTime: Boolean get() = live == null
}

/**
 * Decides how to present a scheduled time beside a live one.
 *
 * Comparison is on the text that would be rendered, not the underlying
 * values. Times are published to the minute, so a bus running forty seconds
 * behind produces a live time that differs numerically but displays
 * identically — striking through `11:35` to show `11:35` beneath it is pure
 * noise, and the app was doing exactly that for every punctual bus.
 *
 * @param aimed the scheduled time, possibly carrying seconds.
 * @param live the expected or actual time, if any.
 * @param cancelled whether the journey is cancelled.
 * @return what to draw.
 */
fun presentTimes(
    aimed: String?,
    live: String?,
    cancelled: Boolean = false,
): TimePresentation {
    val aimedText = aimed?.take(TIME_LENGTH).orEmpty()
    val liveText = live?.take(TIME_LENGTH)
    val differs = liveText != null && liveText != aimedText
    return TimePresentation(
        aimed = aimedText,
        live = if (differs) liveText else null,
        struckThrough = cancelled || differs,
    )
}

/**
 * Shows a scheduled time and, when it differs, the live one.
 *
 * @param aimed the scheduled time.
 * @param live the expected or actual time, if any.
 * @param cancelled whether the journey is cancelled, which strikes the time
 *   through regardless of any live estimate.
 * @param alignment horizontal alignment within the column.
 * @param modifier layout modifier.
 */
@Composable
fun TimeColumn(
    aimed: String?,
    live: String?,
    cancelled: Boolean = false,
    alignment: Alignment.Horizontal = Alignment.Start,
    modifier: Modifier = Modifier,
) {
    val shown = presentTimes(aimed = aimed, live = live, cancelled = cancelled)

    val timeStyle = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES)
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = shown.aimed,
            style = timeStyle,
            color = if (shown.isSingleTime) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (shown.isSingleTime) FontWeight.Bold else FontWeight.Normal,
            textDecoration = if (shown.struckThrough) TextDecoration.LineThrough else null,
        )
        if (shown.live != null) {
            Text(
                text = shown.live,
                style = timeStyle,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
