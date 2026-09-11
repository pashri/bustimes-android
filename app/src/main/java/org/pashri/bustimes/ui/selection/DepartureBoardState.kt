package org.pashri.bustimes.ui.selection

import org.pashri.bustimes.data.model.DepartureBoard
import org.pashri.bustimes.data.parse.DeparturesParseException
import org.pashri.bustimes.ui.map.MapDefaults

/**
 * Why a departure board fetch did not produce a fresh board.
 *
 * @property UNREADABLE bustimes.org's template could not be parsed.
 * @property UNAVAILABLE the request failed, or anything else unexpected.
 */
enum class DepartureFailure {
    UNREADABLE,
    UNAVAILABLE,
}

/**
 * Folds a departure fetch's outcome into a stop's selection state.
 *
 * A failure never clears an already-loaded [SelectionState.Stop.board]: the
 * user is reading it, and there is nothing to gain from replacing a usable
 * board with an error. [SelectionState.Stop.loadedAt] only moves on success,
 * so the age shown always reflects the board actually on screen rather than
 * the moment of the last, possibly failed, attempt.
 *
 * @param current the stop before this fetch.
 * @param result the fetch's outcome.
 * @param now the fetch's completion time, in
 *   [org.pashri.bustimes.data.net.ClockSkew] time.
 * @return the stop's next state.
 */
fun boardStateAfter(
    current: SelectionState.Stop,
    result: Result<DepartureBoard>,
    now: Long,
): SelectionState.Stop {
    val board = result.getOrNull()
    if (board != null) {
        return current.copy(board = board, loading = false, loadedAt = now, failure = null)
    }
    val failure = when (result.exceptionOrNull()) {
        is DeparturesParseException -> DepartureFailure.UNREADABLE
        else -> DepartureFailure.UNAVAILABLE
    }
    return current.copy(loading = false, failure = failure)
}

/**
 * True when a loaded board's age, or a standing failure, should be flagged.
 *
 * @param failure the stop's current failure, if any.
 * @param ageMillis how old [SelectionState.Stop.board] is.
 */
fun isBoardStale(failure: DepartureFailure?, ageMillis: Long): Boolean =
    failure != null || ageMillis > MapDefaults.BOARD_STALE_MILLIS

/**
 * How old a loaded board is, in a form the footer can turn into a string.
 *
 * Kept as data rather than pre-formatted text so [boardAgeLabel] stays a pure
 * function the state logic can be tested against without an Android
 * `Context`; [org.pashri.bustimes.ui.selection.stringResourceFor] resolves it
 * to the matching `strings.xml` entry.
 */
sealed interface BoardAge {
    /** Loaded within the last minute. */
    data object JustNow : BoardAge

    /** @property minutes how many whole minutes old the board is; at least 1. */
    data class MinutesAgo(val minutes: Long) : BoardAge

    /** @property hours how many whole hours old the board is; at least 1. */
    data class HoursAgo(val hours: Long) : BoardAge
}

/**
 * Buckets a board's age for the footer.
 *
 * @param ageMillis time since [SelectionState.Stop.loadedAt].
 */
fun boardAgeLabel(ageMillis: Long): BoardAge {
    val minutes = ageMillis / MILLIS_PER_MINUTE
    return when {
        minutes < 1 -> BoardAge.JustNow
        minutes < MINUTES_PER_HOUR -> BoardAge.MinutesAgo(minutes)
        else -> BoardAge.HoursAgo(minutes / MINUTES_PER_HOUR)
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
