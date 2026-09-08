package org.pashri.bustimes.data.parse

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.pashri.bustimes.data.model.Departure
import org.pashri.bustimes.data.model.DepartureBoard

/** Raised when the departures fragment does not have the shape we expect. */
class DeparturesParseException(message: String) : Exception(message)

/**
 * Parses the HTML fragment served by `/stops/<atco>/departures`.
 *
 * bustimes.org has no JSON departures endpoint and one cannot be synthesised:
 * the trips API cannot be filtered by stop, so rebuilding a board would mean
 * fetching every trip on every service calling there.
 *
 * This is a deliberately brittle-by-design parser: it throws rather than
 * returning an empty board, so a template change upstream surfaces as a
 * failing test and a visible error rather than a silently blank panel.
 */
object DeparturesParser {

    /** Soft hyphens are injected into headers (`Sched&shy;uled`) for line breaking. */
    private const val SOFT_HYPHEN = '­'

    /**
     * Parses a departure board.
     *
     * @param html the raw fragment.
     * @return the parsed board; empty departures is valid (no more buses today).
     * @throws DeparturesParseException if the `#departures` container is absent
     *   or a table has no recognisable header.
     */
    fun parse(html: String): DepartureBoard {
        // A stop with nothing scheduled and no date parameter renders nothing
        // at all: the whole template is wrapped in `{% if when or departures %}`.
        // That is an empty board, not a broken one.
        if (html.isBlank()) {
            return DepartureBoard(emptyList(), hasLive = false, hasScheduled = false)
        }

        val document = Jsoup.parse(html)
        document.selectFirst("#departures")
            ?: throw DeparturesParseException("no #departures element in fragment")

        val departures = mutableListOf<Departure>()
        var hasLive = false
        var hasScheduled = false
        var dateHeading: String? = null

        for (node in document.select("#departures > h3, #departures > table")) {
            if (node.tagName() == "h3") {
                dateHeading = node.text().trim().ifEmpty { null }
                continue
            }
            val columns = readColumns(node)
            hasLive = hasLive || columns.expected != null
            hasScheduled = hasScheduled || columns.scheduled != null
            for (row in node.select("tbody > tr")) {
                departures += readRow(row, columns, dateHeading)
            }
        }
        return DepartureBoard(departures, hasLive = hasLive, hasScheduled = hasScheduled)
    }

    /**
     * Locates columns by header text rather than fixed index.
     *
     * The Scheduled and Expected columns are each conditional on the stop
     * having that kind of data, and stop *areas* add a trailing indicator
     * column, so positions are not stable between stops.
     */
    private fun readColumns(table: Element): Columns {
        val headers = table.select("thead th, thead td")
            .map { it.text().filter { char -> char != SOFT_HYPHEN }.trim().lowercase() }
        if (headers.isEmpty()) {
            throw DeparturesParseException("departures table has no header row")
        }
        val scheduled = headers.indexOf("scheduled").takeIf { it >= 0 }
        val expected = headers.indexOf("expected").takeIf { it >= 0 }
        val to = headers.indexOf("to").takeIf { it >= 0 } ?: 1
        val indicator = headers.indices.lastOrNull()
            ?.takeIf { it != scheduled && it != expected && it != to && it > to }
        if (scheduled == null && expected == null) {
            throw DeparturesParseException(
                "departures table has neither Scheduled nor Expected column: $headers",
            )
        }
        return Columns(line = 0, to = to, scheduled = scheduled, expected = expected, indicator = indicator)
    }

    private fun readRow(row: Element, columns: Columns, dateHeading: String?): Departure {
        val cells = row.select("> td")
        val lineCell = cells.getOrNull(columns.line)
        val destinationCell = cells.getOrNull(columns.to)
        val scheduledCell = columns.scheduled?.let(cells::getOrNull)
        val expectedCell = columns.expected?.let(cells::getOrNull)

        val link = scheduledCell?.selectFirst("a[href]") ?: expectedCell?.selectFirst("a[href]")
        val href = link?.attr("href").orEmpty()

        return Departure(
            lineName = lineCell?.text()?.trim().orEmpty(),
            serviceSlug = lineCell?.selectFirst("a[href^=/services/]")
                ?.attr("href")?.removePrefix("/services/")?.takeIf { it.isNotBlank() },
            destination = destinationCell?.ownText()?.trim()
                ?: destinationCell?.text()?.trim().orEmpty(),
            vehicle = destinationCell?.selectFirst("div.vehicle")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() },
            aimedTime = scheduledCell?.text()?.trim()?.takeIf { it.isNotEmpty() },
            expectedTime = expectedCell?.text()?.trim()?.takeIf { it.isNotEmpty() },
            cancelled = scheduledCell?.selectFirst("s") != null,
            tripId = idFrom(href, "/trips/"),
            journeyId = idFrom(href, "/journeys/"),
            indicator = columns.indicator?.let(cells::getOrNull)?.text()?.trim()
                ?.takeIf { it.isNotEmpty() },
            dateHeading = dateHeading,
        )
    }

    private fun idFrom(href: String, prefix: String): Long? =
        href.takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.substringBefore('?')
            ?.toLongOrNull()

    private data class Columns(
        val line: Int,
        val to: Int,
        val scheduled: Int?,
        val expected: Int?,
        val indicator: Int?,
    )
}
