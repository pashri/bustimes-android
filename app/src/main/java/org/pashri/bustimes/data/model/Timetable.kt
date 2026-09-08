package org.pashri.bustimes.data.model

/** A whole service timetable: one [TimetableGrouping] per direction. */
data class Timetable(
    val groupings: List<TimetableGrouping>,
)

/**
 * One direction of a service.
 *
 * The CSV separates groupings with a blank row, each followed by its own
 * header row. The header carries no direction name, so groupings are
 * identified by their first and last stop instead.
 */
data class TimetableGrouping(
    val lineNames: List<String>,
    val rows: List<TimetableRow>,
) {
    /** How many journeys this grouping contains. */
    val columnCount: Int get() = lineNames.size

    val origin: String? get() = rows.firstOrNull()?.stopName
    val destination: String? get() = rows.lastOrNull()?.stopName

    /**
     * The journeys, transposed out of the stop-major CSV and put in time order.
     *
     * The CSV is a grid of stops down and journeys across, but a phone reads
     * far better as a list of journeys, so this flips it.
     *
     * Column order follows the printed timetable, which groups journeys by
     * stopping pattern rather than by time, so a 05:40 can sit after a 05:45.
     * A list read top to bottom has to be chronological or scanning it for the
     * next departure does not work.
     *
     * @return the journeys, earliest departure first.
     */
    fun journeys(): List<TimetableJourney> = rawJourneys()
        .sortedBy { it.departureTime?.minutesSinceMidnight ?: Int.MAX_VALUE }

    /** The journeys in the CSV's own column order. */
    fun rawJourneys(): List<TimetableJourney> = (0 until columnCount).map { column ->
        TimetableJourney(
            lineName = lineNames[column],
            calls = rows.mapNotNull { row ->
                row.times.getOrNull(column)?.let { time ->
                    TimetableCall(row.stopName, row.atcoCode, time)
                }
            },
        )
    }
}

data class TimetableRow(
    val stopName: String,
    val naptanCode: String?,
    val atcoCode: String?,
    /** One entry per journey; null where that journey does not call here. */
    val times: List<TimetableTime?>,
)

/** A single journey read down the grid — the phone-native view of a timetable. */
data class TimetableJourney(
    val lineName: String,
    val calls: List<TimetableCall>,
) {
    val departureTime: TimetableTime? get() = calls.firstOrNull()?.time
}

data class TimetableCall(
    val stopName: String,
    val atcoCode: String?,
    val time: TimetableTime,
)

/**
 * A scheduled time.
 *
 * Times after midnight arrive from the CSV suffixed with a superscript plus
 * one (`00:34⁺¹`), meaning they belong to the following day. Keeping that as
 * [nextDay] rather than discarding it stops the last bus of the night sorting
 * as the first of the morning.
 */
data class TimetableTime(
    val hour: Int,
    val minute: Int,
    val nextDay: Boolean,
) {
    /** Minutes since midnight of the service day, so ordering survives midnight. */
    val minutesSinceMidnight: Int get() = hour * 60 + minute + if (nextDay) 24 * 60 else 0

    override fun toString(): String =
        "%02d:%02d%s".format(hour, minute, if (nextDay) "⁺¹" else "")
}
