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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pashri.bustimes.R
import org.pashri.bustimes.data.model.Departure

/**
 * The departure board for the selected stop.
 *
 * A whole row is the tap target and opens that departure's schedule. There is
 * deliberately no per-row link to the full timetable: the timetable is one tap
 * from the journey panel that opens next, so putting it on every row here
 * would give two different destinations for one row.
 *
 * @param stop the selected stop and its loaded board.
 * @param onDepartureClicked called with a departure's trip id and journey id;
 *   a tracked departure has only the latter and needs resolving.
 * @param isFavourite whether this stop is starred.
 * @param onToggleFavourite called to star or un-star it.
 * @param modifier layout modifier.
 */
@Composable
fun StopPanel(
    stop: SelectionState.Stop,
    onDepartureClicked: (Long?, Long?) -> Unit,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stop.name ?: stop.atcoCode,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            // The same control stars and un-stars, so there is one place to
            // manage a favourite rather than a separate list to edit.
            IconButton(onClick = onToggleFavourite, enabled = stop.canBeStarred) {
                Icon(
                    imageVector = if (isFavourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavourite) {
                        stringResource(R.string.remove_favourite)
                    } else {
                        stringResource(R.string.add_favourite)
                    },
                    tint = if (isFavourite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
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
            )
        }
    }
}

@Composable
private fun DepartureList(
    departures: List<Departure>,
    onDepartureClicked: (Long?, Long?) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(departures) { departure ->
            DepartureRow(departure = departure, onDepartureClicked = onDepartureClicked)
            HorizontalDivider()
        }
    }
}

@Composable
private fun DepartureRow(departure: Departure, onDepartureClicked: (Long?, Long?) -> Unit) {
    val openable = departure.tripId != null || departure.journeyId != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = openable) {
                onDepartureClicked(departure.tripId, departure.journeyId)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = departure.lineName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(52.dp),
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
            if (departure.cancelled) {
                Text(
                    text = "Cancelled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        TimeColumn(
            aimed = departure.aimedTime,
            live = departure.expectedTime,
            cancelled = departure.cancelled,
            alignment = Alignment.End,
        )
    }
}
