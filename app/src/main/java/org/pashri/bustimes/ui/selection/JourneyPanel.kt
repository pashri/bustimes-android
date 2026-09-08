package org.pashri.bustimes.ui.selection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pashri.bustimes.data.model.StopTime

/**
 * The schedule of the selected journey.
 *
 * Shows aimed times always and live times when the operator supplies them.
 * Most journeys outside London and the largest operators are never tracked,
 * so the aimed-only layout is the common case and is presented as normal
 * rather than as missing data.
 *
 * @param journey the selected journey and its loaded schedule.
 * @param onStopClicked called with an ATCO code when a calling point is tapped.
 * @param onLineClicked called with the service id when the line number is tapped.
 * @param onHeaderClicked called when the header is tapped, to recentre the map.
 * @param modifier layout modifier.
 */
@Composable
fun JourneyPanel(
    journey: SelectionState.Journey,
    onStopClicked: (String) -> Unit,
    onLineClicked: (Long) -> Unit,
    onHeaderClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        JourneyHeader(
            journey = journey,
            onLineClicked = onLineClicked,
            onHeaderClicked = onHeaderClicked,
        )
        HorizontalDivider()
        when {
            journey.loading -> PanelSpinner()
            journey.failed -> PanelMessage(text = "Couldn't load this journey")
            else -> CallingPoints(
                times = journey.trip?.times.orEmpty(),
                onStopClicked = onStopClicked,
            )
        }
    }
}

/**
 * The peek-height content: line, destination and how it is running.
 *
 * Tapping it recentres the map on the bus, which is the way back after
 * panning along a route with the sheet collapsed.
 */
@Composable
private fun JourneyHeader(
    journey: SelectionState.Journey,
    onLineClicked: (Long) -> Unit,
    onHeaderClicked: () -> Unit,
) {
    val serviceId = journey.serviceId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onHeaderClicked)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AssistChip(
            onClick = { if (serviceId != null) onLineClicked(serviceId) },
            enabled = serviceId != null,
            label = { Text(text = journey.lineName ?: "—", fontWeight = FontWeight.Bold) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = journey.headsign.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
            )
            JourneySubtitle(journey = journey)
        }
    }
}

@Composable
private fun JourneySubtitle(journey: SelectionState.Journey) {
    val nextStop = journey.nextStopName
    val subtitle = when {
        journey.untracked -> "Not tracked"
        nextStop != null -> "Next stop $nextStop"
        else -> journey.vehicle?.vehicle?.name
    }
    if (subtitle != null) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val delay = journey.vehicle?.delay
    if (delay != null) {
        LatenessLabel(lateness = latenessFromDelay(delay))
    }
}

/** Turns the vehicle feed's delay in seconds into a lateness. */
private fun latenessFromDelay(seconds: Double): Lateness {
    val minutes = (seconds / SECONDS_PER_MINUTE).toLong()
    return when {
        minutes >= 1 -> Lateness.Late(minutes)
        minutes <= -1 -> Lateness.Early(-minutes)
        else -> Lateness.OnTime
    }
}

@Composable
private fun CallingPoints(times: List<StopTime>, onStopClicked: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(times) { time -> CallingPoint(time = time, onStopClicked = onStopClicked) }
    }
}

@Composable
private fun CallingPoint(time: StopTime, onStopClicked: (String) -> Unit) {
    val atco = time.stop.atcoCode
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = atco != null) { if (atco != null) onStopClicked(atco) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimeColumn(
            aimed = time.aimedDepartureTime ?: time.aimedArrivalTime,
            live = time.actualDepartureTime ?: time.expectedDepartureTime,
            modifier = Modifier.width(64.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = time.stop.name, style = MaterialTheme.typography.bodyLarge)
            if (!time.pickUp) {
                Text(
                    text = "Set down only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LatenessLabel(lateness = LatenessCalculator.forStopTime(time))
        }
    }
    HorizontalDivider()
}

/** Shared spinner for a loading panel. */
@Composable
fun PanelSpinner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Shared message for an empty or failed panel.
 *
 * @param text the message to show.
 * @param modifier layout modifier.
 */
@Composable
fun PanelMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(24.dp),
    )
}

private const val SECONDS_PER_MINUTE = 60.0
