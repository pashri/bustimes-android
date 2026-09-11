package org.pashri.bustimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pashri.bustimes.data.model.DepartureBoard
import org.pashri.bustimes.data.parse.DeparturesParseException
import org.pashri.bustimes.ui.selection.BoardAge
import org.pashri.bustimes.ui.selection.DepartureFailure
import org.pashri.bustimes.ui.selection.SelectionState
import org.pashri.bustimes.ui.selection.boardAgeLabel
import org.pashri.bustimes.ui.selection.boardStateAfter
import org.pashri.bustimes.ui.selection.isBoardStale
import java.io.IOException

class DepartureBoardStateTest {

    private val stop = SelectionState.Stop(atcoCode = "0500CCITY001")
    private val board = DepartureBoard(departures = emptyList(), hasLive = true, hasScheduled = true)

    @Test
    fun `a success sets the board, stamps loadedAt and clears any failure`() {
        val failing = stop.copy(failure = DepartureFailure.UNAVAILABLE)

        val next = boardStateAfter(failing, Result.success(board), now = 1_000L)

        assertEquals(board, next.board)
        assertEquals(1_000L, next.loadedAt)
        assertFalse(next.loading)
        assertNull(next.failure)
    }

    @Test
    fun `a failure over an existing board keeps the board and does not move loadedAt`() {
        val loaded = stop.copy(board = board, loadedAt = 1_000L)

        val next = boardStateAfter(loaded, Result.failure(IOException()), now = 5_000L)

        assertEquals(board, next.board)
        assertEquals(1_000L, next.loadedAt)
        assertEquals(DepartureFailure.UNAVAILABLE, next.failure)
        assertFalse(next.loading)
    }

    @Test
    fun `a failure with no board leaves the board null and reports unavailable`() {
        val next = boardStateAfter(stop, Result.failure(IOException()), now = 1_000L)

        assertNull(next.board)
        assertEquals(DepartureFailure.UNAVAILABLE, next.failure)
    }

    @Test
    fun `a parse failure reports unreadable rather than unavailable`() {
        val next = boardStateAfter(stop, Result.failure(DeparturesParseException("bad shape")), now = 1_000L)

        assertNull(next.board)
        assertEquals(DepartureFailure.UNREADABLE, next.failure)
    }

    @Test
    fun `a board under a minute old is not stale`() {
        assertFalse(isBoardStale(failure = null, ageMillis = 30_000L))
    }

    @Test
    fun `a board past the stale threshold is stale even with no failure`() {
        assertTrue(isBoardStale(failure = null, ageMillis = 91_000L))
    }

    @Test
    fun `any standing failure is stale regardless of age`() {
        assertTrue(isBoardStale(failure = DepartureFailure.UNAVAILABLE, ageMillis = 0L))
    }

    @Test
    fun `boardAgeLabel reads just now under a minute`() {
        assertEquals(BoardAge.JustNow, boardAgeLabel(30_000L))
    }

    @Test
    fun `boardAgeLabel reads one minute ago at the one-minute boundary`() {
        assertEquals(BoardAge.MinutesAgo(1), boardAgeLabel(60_000L))
    }

    @Test
    fun `boardAgeLabel reads two minutes ago just under the next boundary`() {
        assertEquals(BoardAge.MinutesAgo(2), boardAgeLabel(179_999L))
    }

    @Test
    fun `boardAgeLabel switches to hours past sixty minutes`() {
        val label = boardAgeLabel(90 * 60_000L)

        assertNotNull(label)
        assertEquals(BoardAge.HoursAgo(1), label)
    }
}
