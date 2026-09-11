package org.pashri.bustimes.ui.map

/**
 * Zoom thresholds and timings, matched to bustimes.org's own map.
 *
 * The zoom floors are taken from `shouldShowStops` and `shouldShowVehicles`
 * in the upstream frontend so this app shows the same things at the same
 * zooms. Note that upstream switches between detailed markers and plain dots
 * by vehicle *count*, not zoom — a symbol layer makes that switch unnecessary
 * here, since there are no per-marker views to be expensive.
 */
object MapDefaults {

    /**
     * Below this zoom, stops are not fetched or drawn.
     *
     * One level below bustimes.org's own floor of 14. A phone viewport at 13
     * holds about three hundred stops for 89 KB, which the hour-long cache on
     * the endpoint absorbs; the markers shrink with zoom so that many dots
     * still read as texture rather than clutter.
     */
    const val STOPS_MIN_ZOOM = 13.0

    /** Below this zoom, vehicles are not fetched or drawn. */
    const val VEHICLES_MIN_ZOOM = 6.0

    /** Gap between vehicle polls, chained after each response lands. */
    const val VEHICLE_POLL_MILLIS = 12_000L

    /**
     * Gap between departure board refreshes, chained after each response lands.
     *
     * A stop's board ticks once a minute at most, so 12s would be five
     * requests a minute for text that rarely changes, and 60s risks a full
     * tick of staleness. 30s sits between the two.
     */
    const val DEPARTURES_REFRESH_MILLIS = 30_000L

    /**
     * Age, or time since a refresh failed, at which a loaded board is shown
     * as stale with a warning and a Retry affordance.
     *
     * Three refresh cycles: one dropped response should not flash a warning,
     * but a board stuck here for any reason — not only a parse or network
     * failure — needs a way to recover.
     */
    const val BOARD_STALE_MILLIS = 90_000L

    /** Settling delay after a pan that needs fresh vehicles. */
    const val PAN_DEBOUNCE_MILLIS = 200L

    /** Duration of the tween that carries a bus from one fix to the next. */
    const val VEHICLE_TWEEN_MILLIS = 1_000L

    /** Where the map opens on a first run with no permission and no history. */
    const val UK_LATITUDE = 54.0
    const val UK_LONGITUDE = -2.5
    const val UK_ZOOM = 5.0

    /** Zoom used when centring on the user's coarse location. */
    const val LOCATED_ZOOM = 15.0
}
