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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.pashri.bustimes.data.model.Freshness
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
 * @param nowMillis the clock the vehicle's position is aged against, shared
 *   with the map so both cannot disagree about the same bus.
 * @param onStopClicked called with an ATCO code when a calling point is tapped.
 * @param onLineClicked called with the service id when the line number is tapped.
 * @param onHeaderClicked called when the header is tapped, to recentre the map.
 * @param modifier layout modifier.
 */
@Composable
fun JourneyPanel(
    journey: SelectionState.Journey,
    nowMillis: Long,
    onStopClicked: (String) -> Unit,
    onLineClicked: (Long) -> Unit,
    onHeaderClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        JourneyHeader(
            journey = journey,
            nowMillis = nowMillis,
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
    nowMillis: Long,
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
        LineBadge(
            lineName = journey.lineName ?: "—",
            modifier = Modifier.clickable(enabled = serviceId != null) {
                if (serviceId != null) onLineClicked(serviceId)
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = journey.headsign.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
            )
            JourneySubtitle(journey = journey, nowMillis = nowMillis)
        }
    }
}

@Composable
private fun JourneySubtitle(journey: SelectionState.Journey, nowMillis: Long) {
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
    LivenessRow(journey = journey, nowMillis = nowMillis)
}

/**
 * How the bus is running, and how long ago that was true.
 *
 * The age sits next to the lateness rather than on its own row because it
 * qualifies it: a delay is computed from a position, so a figure taken from a
 * fix five minutes old is itself five minutes old. Shown whenever a vehicle
 * is reporting, even when current — saying nothing would leave the reader
 * unable to tell a fresh position from an app that never mentions freshness.
 */
@Composable
private fun LivenessRow(journey: SelectionState.Journey, nowMillis: Long) {
    val vehicle = journey.vehicle ?: return
    val age = Freshness.ageSeconds(datetime = vehicle.datetime, nowMillis = nowMillis)
    val delay = vehicle.delay
    // Neutral, so the age never competes with the lateness for attention.
    val ageColour = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (delay != null) {
            LatenessLabel(
                lateness = latenessFromDelay(delay),
                stale = Freshness.isStale(age),
            )
            Text(text = "·", style = MaterialTheme.typography.labelMedium, color = ageColour)
        }
        Text(
            text = Freshness.describeAge(age),
            style = MaterialTheme.typography.labelMedium,
            color = ageColour,
        )
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
