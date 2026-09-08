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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.pashri.bustimes.data.model.StopTime

/**
 * The schedule of the selected bus.
 *
 * Shows aimed times always and live times when the operator supplies them.
 * Most services outside London have no live feed, so the aimed-only layout is
 * the common case and is treated as normal rather than as missing data.
 *
 * @param bus the selected bus and its loaded schedule.
 * @param onStopClicked called with an ATCO code when a calling point is tapped.
 * @param onLineClicked called with the service id when the line number is tapped.
 * @param modifier layout modifier.
 */
@Composable
fun BusPanel(
    bus: SelectionState.Bus,
    onStopClicked: (String) -> Unit,
    onLineClicked: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BusHeader(bus = bus, onLineClicked = onLineClicked)
        HorizontalDivider()
        when {
            bus.loading -> PanelSpinner()
            bus.failed -> PanelMessage(text = "Couldn't load this journey")
            else -> CallingPoints(times = bus.trip?.times.orEmpty(), onStopClicked = onStopClicked)
        }
    }
}

@Composable
private fun BusHeader(bus: SelectionState.Bus, onLineClicked: (Long) -> Unit) {
    val serviceId = bus.serviceId
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AssistChip(
            onClick = { if (serviceId != null) onLineClicked(serviceId) },
            enabled = serviceId != null,
            label = { Text(text = bus.lineName ?: "—", fontWeight = FontWeight.Bold) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bus.trip?.headsign ?: bus.vehicle?.destination ?: "",
                style = MaterialTheme.typography.titleMedium,
            )
            val progress = bus.vehicle?.progress
            val subtitle = when {
                progress?.nextStop != null -> "Next stop ${progress.nextStop}"
                bus.vehicle?.vehicle?.name != null -> bus.vehicle.vehicle.name
                else -> null
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val delay = bus.vehicle?.delay
            if (delay != null) {
                LatenessLabel(lateness = latenessFromDelay(delay))
            }
        }
    }
}

/** Turns the vehicle feed's delay in seconds into a lateness. */
private fun latenessFromDelay(seconds: Int): Lateness {
    val minutes = seconds / 60L
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
        Column(modifier = Modifier.width(64.dp)) {
            Text(
                text = (time.aimedDepartureTime ?: time.aimedArrivalTime).orEmpty().take(TIME_LENGTH),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (time.hasLiveTime) TextDecoration.LineThrough else null,
            )
            val live = time.actualDepartureTime ?: time.expectedDepartureTime
            if (live != null) {
                Text(
                    text = live.take(TIME_LENGTH),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
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

private const val TIME_LENGTH = 5

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
