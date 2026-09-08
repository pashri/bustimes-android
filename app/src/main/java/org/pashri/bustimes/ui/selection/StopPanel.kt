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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.pashri.bustimes.data.model.Departure

/**
 * The departure board for the selected stop.
 *
 * @param stop the selected stop and its loaded board.
 * @param onDepartureClicked called with a departure's trip id, when it has one.
 * @param onLineClicked called with a service slug when a line number is tapped.
 * @param modifier layout modifier.
 */
@Composable
fun StopPanel(
    stop: SelectionState.Stop,
    onDepartureClicked: (Long) -> Unit,
    onLineClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stop.name ?: stop.atcoCode,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        HorizontalDivider()
        val board = stop.board
        when {
            stop.loading -> PanelSpinner()
            stop.unreadable -> PanelMessage(
                text = "Couldn't read the departure board — bustimes.org may have changed.",
            )
            board == null -> PanelMessage(text = "Couldn't load departures")
            board.departures.isEmpty() -> PanelMessage(text = "No more departures today")
            else -> DepartureList(
                departures = board.departures,
                onDepartureClicked = onDepartureClicked,
                onLineClicked = onLineClicked,
            )
        }
    }
}

@Composable
private fun DepartureList(
    departures: List<Departure>,
    onDepartureClicked: (Long) -> Unit,
    onLineClicked: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(departures) { departure ->
            DepartureRow(
                departure = departure,
                onDepartureClicked = onDepartureClicked,
                onLineClicked = onLineClicked,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun DepartureRow(
    departure: Departure,
    onDepartureClicked: (Long) -> Unit,
    onLineClicked: (String) -> Unit,
) {
    val tripId = departure.tripId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tripId != null) { if (tripId != null) onDepartureClicked(tripId) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = departure.lineName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .width(52.dp)
                .clickable(enabled = departure.serviceSlug != null) {
                    departure.serviceSlug?.let(onLineClicked)
                },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = departure.destination, style = MaterialTheme.typography.bodyLarge)
            if (departure.vehicle != null) {
                Text(
                    text = departure.vehicle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LatenessLabel(
                lateness = LatenessCalculator.forDeparture(
                    aimedText = departure.aimedTime,
                    expectedText = departure.expectedTime,
                ),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = departure.aimedTime.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = when {
                    departure.cancelled -> TextDecoration.LineThrough
                    departure.isTracked -> TextDecoration.LineThrough
                    else -> null
                },
            )
            if (departure.expectedTime != null) {
                Text(
                    text = departure.expectedTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (departure.cancelled) {
                Text(
                    text = "Cancelled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
