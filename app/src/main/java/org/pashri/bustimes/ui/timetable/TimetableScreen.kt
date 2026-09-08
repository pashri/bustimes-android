package org.pashri.bustimes.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pashri.bustimes.data.model.TimetableGrouping
import org.pashri.bustimes.data.model.TimetableJourney

/**
 * A service's full timetable, laid out for a phone.
 *
 * bustimes.org shows a grid of stops down and journeys across, which for a
 * frequent service is nearly eighty columns. Rather than a frozen column and
 * horizontal scrolling, this reflows the same data into a list of journeys:
 * one row per departure, expanding to its calling points. The information is
 * identical; the shape suits the screen.
 *
 * @param state the loaded timetable.
 * @param onBack called when the user navigates back.
 * @param onGroupingSelected called with the index of the direction to show.
 * @param onJourneyClicked called with a journey the user tapped.
 * @param onRetry called to retry after a failure.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    state: TimetableUiState,
    onBack: () -> Unit,
    onGroupingSelected: (Int) -> Unit,
    onJourneyClicked: (TimetableJourney) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Timetable") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Centred { CircularProgressIndicator() }
                state.failed -> Centred {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Couldn't load the timetable")
                        TextButton(onClick = onRetry) { Text(text = "Retry") }
                    }
                }
                else -> TimetableBody(
                    state = state,
                    onGroupingSelected = onGroupingSelected,
                    onJourneyClicked = onJourneyClicked,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimetableBody(
    state: TimetableUiState,
    onGroupingSelected: (Int) -> Unit,
    onJourneyClicked: (TimetableJourney) -> Unit,
) {
    val groupings = state.timetable?.groupings.orEmpty()
    if (groupings.isEmpty()) {
        Centred { Text(text = "No timetable for today") }
        return
    }
    val index = state.selectedGrouping.coerceIn(groupings.indices)
    if (groupings.size > 1) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groupings.forEachIndexed { position, grouping ->
                FilterChip(
                    selected = position == index,
                    onClick = { onGroupingSelected(position) },
                    label = { Text(text = directionLabel(grouping)) },
                )
            }
        }
    }
    JourneyList(
        grouping = groupings[index],
        hasTripIds = state.tripIdsByStart.isNotEmpty(),
        onJourneyClicked = onJourneyClicked,
    )
}

/**
 * Names a direction by where it ends, since the CSV carries no label.
 *
 * Full stop names run to "Cambridge Drummer St Bus Station (Bay 7)", which
 * wraps a chip onto three lines, so the bay or stand qualifier is dropped.
 *
 * @param grouping the direction to name.
 * @return a short label for the chip.
 */
private fun directionLabel(grouping: TimetableGrouping): String =
    grouping.destination
        ?.substringBefore('(')
        ?.substringBefore(',')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "Direction"

@Composable
private fun JourneyList(
    grouping: TimetableGrouping,
    hasTripIds: Boolean,
    onJourneyClicked: (TimetableJourney) -> Unit,
) {
    val journeys = grouping.journeys().filter { it.calls.isNotEmpty() }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(journeys) { journey ->
            JourneyRow(
                journey = journey,
                clickable = hasTripIds,
                onJourneyClicked = onJourneyClicked,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun JourneyRow(
    journey: TimetableJourney,
    clickable: Boolean,
    onJourneyClicked: (TimetableJourney) -> Unit,
) {
    val first = journey.calls.first()
    val last = journey.calls.last()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = clickable) { onJourneyClicked(journey) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                text = first.time.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = last.time.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = first.stopName, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "to ${last.stopName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${journey.calls.size} stops",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}
