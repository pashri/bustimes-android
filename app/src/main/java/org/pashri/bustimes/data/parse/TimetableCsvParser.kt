package org.pashri.bustimes.data.parse

import java.io.StringReader
import org.apache.commons.csv.CSVFormat
import org.pashri.bustimes.data.model.Timetable
import org.pashri.bustimes.data.model.TimetableGrouping
import org.pashri.bustimes.data.model.TimetableRow
import org.pashri.bustimes.data.model.TimetableTime

/** Raised when the timetable CSV does not have the shape we expect. */
class TimetableParseException(message: String) : Exception(message)

/**
 * Parses `/services/<id>/timetable.csv`.
 *
 * This endpoint is preferred over the HTML timetable because the HTML grid
 * compresses repeated journeys into "then every N minutes" cells and splits
 * rows for wait times, using `rowspan` and `colspan` that would have to be
 * reconstructed. The CSV is fully expanded: every cell is a real time.
 *
 * Layout is `stop, NaPTAN code, ATCO code, <line>, <line>, …` with one row per
 * stop, and groupings (directions) separated by a blank row followed by a
 * fresh header.
 */
object TimetableCsvParser {

    private const val NEXT_DAY_SUFFIX = "⁺¹"
    private const val STOP_COLUMNS = 3

    /**
     * Parses a timetable.
     *
     * @param csv the raw CSV body.
     * @return the parsed timetable, one grouping per direction.
     * @throws TimetableParseException if no grouping has a usable header row.
     */
    fun parse(csv: String): Timetable {
        val records = CSVFormat.DEFAULT.parse(StringReader(csv)).records
        val groupings = mutableListOf<TimetableGrouping>()
        var lineNames: List<String>? = null
        var rows = mutableListOf<TimetableRow>()

        for (record in records) {
            val cells = record.toList()
            // Groupings are separated by a blank row, but Commons CSV's default
            // format discards empty lines, so the separator cannot be relied on.
            // The header row that starts each grouping is the real boundary.
            if (isHeader(cells)) {
                flush(lineNames, rows, groupings)
                lineNames = cells.drop(STOP_COLUMNS)
                rows = mutableListOf()
                continue
            }
            if (cells.all { it.isBlank() }) continue
            val header = lineNames ?: continue
            rows += readRow(cells, header.size)
        }
        flush(lineNames, rows, groupings)

        if (groupings.isEmpty()) {
            throw TimetableParseException("timetable CSV contained no usable groupings")
        }
        return Timetable(groupings)
    }

    /**
     * Recognises the header row that opens each grouping.
     *
     * @param cells one CSV record.
     * @return true when this row names the stop columns rather than a stop.
     */
    private fun isHeader(cells: List<String>): Boolean =
        cells.size > STOP_COLUMNS &&
            cells[0].trim().equals("stop", ignoreCase = true) &&
            cells[1].trim().equals("NaPTAN code", ignoreCase = true)

    private fun flush(
        lineNames: List<String>?,
        rows: List<TimetableRow>,
        into: MutableList<TimetableGrouping>,
    ) {
        if (lineNames != null && rows.isNotEmpty()) {
            into += TimetableGrouping(lineNames, rows)
        }
    }

    private fun readRow(cells: List<String>, columnCount: Int): TimetableRow {
        val times = (0 until columnCount).map { column ->
            parseTime(cells.getOrNull(STOP_COLUMNS + column).orEmpty())
        }
        return TimetableRow(
            stopName = cells.getOrNull(0).orEmpty().trim(),
            naptanCode = cells.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() },
            atcoCode = cells.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() },
            times = times,
        )
    }

    /**
     * Parses one `HH:mm` cell, honouring the next-day marker.
     *
     * @param cell the raw cell text, possibly blank or suffixed with `⁺¹`.
     * @return the time, or null when the journey does not call at this stop.
     */
    fun parseTime(cell: String): TimetableTime? {
        val trimmed = cell.trim()
        if (trimmed.isEmpty()) return null
        val nextDay = trimmed.endsWith(NEXT_DAY_SUFFIX)
        val bare = if (nextDay) trimmed.removeSuffix(NEXT_DAY_SUFFIX) else trimmed
        val parts = bare.split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return TimetableTime(hour = hour, minute = minute, nextDay = nextDay)
    }
}
