package org.pashri.bustimes.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.pashri.bustimes.data.model.Timetable
import org.pashri.bustimes.data.model.TimetableJourney
import org.pashri.bustimes.data.parse.TimetableParseException
import org.pashri.bustimes.data.repo.BustimesRepository

/** The timetable screen's state. */
data class TimetableUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val timetable: Timetable? = null,
    /** Trip ids keyed by the journey's first departure, so columns can be opened. */
    val tripIdsByStart: Map<String, Long> = emptyMap(),
    val selectedGrouping: Int = 0,
) {
    /**
     * Whether a journey can be opened.
     *
     * Resolution is per journey rather than all-or-nothing: a timetable can
     * list journeys the trips API does not return, and those rows should be
     * inert while their neighbours stay tappable.
     *
     * @param journey the journey to test.
     * @return true when a trip id is known for it.
     */
    fun isOpenable(journey: TimetableJourney): Boolean = tripIdFor(journey) != null

    /**
     * Finds the trip behind a journey.
     *
     * @param journey the journey the user tapped.
     * @return the trip id, or null when it could not be matched.
     */
    fun tripIdFor(journey: TimetableJourney): Long? {
        val start = journey.departureTime ?: return null
        return tripIdsByStart["%02d:%02d".format(start.hour, start.minute)]
    }
}

/**
 * Loads a service's full timetable.
 *
 * The CSV export is used rather than the HTML timetable because the HTML grid
 * compresses repeated journeys into "then every N minutes" cells spanning
 * multiple columns, which would have to be reconstructed. The CSV is fully
 * expanded. Trip ids are not in the CSV, so they are matched separately from
 * the trips API by each journey's first departure time.
 */
class TimetableViewModel(
    private val repository: BustimesRepository,
    private val serviceId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(TimetableUiState())

    /** The timetable screen's state. */
    val state: StateFlow<TimetableUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Selects which direction to show. */
    fun onGroupingSelected(index: Int) {
        _state.update { it.copy(selectedGrouping = index) }
    }

    /** Reloads after a failure. */
    fun onRetry() {
        _state.update { it.copy(loading = true, failed = false) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val timetable = repository.timetable(serviceId)
                _state.update { it.copy(timetable = timetable, loading = false) }
                loadTripIds()
            } catch (error: IOException) {
                _state.update { it.copy(loading = false, failed = true) }
            } catch (error: TimetableParseException) {
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    private suspend fun loadTripIds() {
        try {
            val trips = repository.tripsForService(
                serviceId = serviceId,
                date = LocalDate.now().toString(),
            )
            val byStart = trips
                .mapNotNull { trip -> trip.start?.take(START_LENGTH)?.let { it to trip.id } }
                .toMap()
            _state.update { it.copy(tripIdsByStart = byStart) }
        } catch (error: IOException) {
            // Tappable columns are a bonus; the timetable is useful without them.
        }
    }

    /** Creates [TimetableViewModel] instances for one service. */
    class Factory(
        private val repository: BustimesRepository,
        private val serviceId: Long,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TimetableViewModel(repository, serviceId) as T
    }

    private companion object {
        const val START_LENGTH = 5
    }
}
