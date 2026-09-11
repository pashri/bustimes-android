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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.pashri.bustimes.R
import org.pashri.bustimes.data.model.Departure
import org.pashri.bustimes.data.net.ClockSkew

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
 * @param onRetryRequested called to retry a failed or stale board.
 * @param modifier layout modifier.
 */
@Composable
fun StopPanel(
    stop: SelectionState.Stop,
    onDepartureClicked: (Long?, Long?) -> Unit,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onRetryRequested: () -> Unit,
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
            stop.loading && board == null -> PanelSpinner()
            board == null -> FailedBoardMessage(
                failure = stop.failure,
                onRetryRequested = onRetryRequested,
            )
            board.departures.isEmpty() -> PanelMessage(text = stringResource(R.string.departures_empty))
            else -> DepartureList(
                departures = board.departures,
                onDepartureClicked = onDepartureClicked,
            )
        }
        if (board != null && stop.loadedAt != null) {
            DepartureBoardFooter(
                loadedAt = stop.loadedAt,
                failure = stop.failure,
                refreshing = stop.loading,
                onRetryRequested = onRetryRequested,
            )
        }
    }
}

/**
 * The message shown when there is no board to display at all.
 *
 * Reachable on the very first fetch, before any board has ever loaded, so it
 * carries its own Retry rather than relying on [DepartureBoardFooter], which
 * only appears once a board exists.
 *
 * @param failure why the fetch did not produce a board.
 * @param onRetryRequested called when Retry is tapped.
 */
@Composable
private fun FailedBoardMessage(failure: DepartureFailure?, onRetryRequested: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PanelMessage(
            text = if (failure == DepartureFailure.UNREADABLE) {
                stringResource(R.string.departures_unreadable)
            } else {
                stringResource(R.string.departures_unavailable)
            },
        )
        TextButton(
            onClick = onRetryRequested,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(text = stringResource(R.string.retry))
        }
    }
}

/**
 * The board's age, with a warning and Retry once it is stale.
 *
 * Always shown for a loaded board — never only past the stale threshold —
 * so the label is a steady source of truth rather than something that
 * appears only when there is already a problem.
 *
 * @param loadedAt when the board on screen was last successfully fetched.
 * @param failure the stop's current failure, if the last refresh did not
 *   land.
 * @param refreshing true while a fetch is outstanding; shows a small spinner
 *   in place of Retry without hiding the board underneath it.
 * @param onRetryRequested called when Retry is tapped.
 */
@Composable
private fun DepartureBoardFooter(
    loadedAt: Long,
    failure: DepartureFailure?,
    refreshing: Boolean,
    onRetryRequested: () -> Unit,
) {
    val ageMillis by produceState(initialValue = ClockSkew.now() - loadedAt, loadedAt) {
        while (true) {
            value = ClockSkew.now() - loadedAt
            delay(FOOTER_TICK_MILLIS)
        }
    }
    val stale = isBoardStale(failure = failure, ageMillis = ageMillis)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (stale) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = ageLabelText(boardAgeLabel(ageMillis)),
            style = MaterialTheme.typography.labelMedium,
            color = if (stale) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        when {
            refreshing -> FooterSpinner()
            stale -> TextButton(onClick = onRetryRequested) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

/** Small spinner for a footer refresh in progress, sized to sit beside text. */
@Composable
private fun FooterSpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = modifier.width(16.dp), strokeWidth = 2.dp)
}

/** Resolves a [BoardAge] to its `strings.xml` text. */
@Composable
private fun ageLabelText(age: BoardAge): String = when (age) {
    BoardAge.JustNow -> stringResource(R.string.departures_updated_just_now)
    is BoardAge.MinutesAgo -> stringResource(R.string.departures_updated_minutes_ago, age.minutes)
    is BoardAge.HoursAgo -> stringResource(R.string.departures_updated_hours_ago, age.hours)
}

/** How often the footer's age re-renders while the sheet is open. */
private const val FOOTER_TICK_MILLIS = 10_000L

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
