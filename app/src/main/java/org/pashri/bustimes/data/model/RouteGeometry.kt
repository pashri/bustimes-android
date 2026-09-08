package org.pashri.bustimes.data.model

/**
 * The lines to draw for a journey.
 *
 * @property legs `[lon, lat]` pairs, one list per drawable leg.
 * @property approximate true when [legs] only joins calling points, because
 *   the service has no road geometry.
 */
data class RouteGeometry(
    val legs: List<List<List<Double>>>,
    val approximate: Boolean,
) {
    /** True when there is nothing to draw. */
    val isEmpty: Boolean get() = legs.isEmpty()

    companion object {

        /** Nothing to draw. */
        val None = RouteGeometry(legs = emptyList(), approximate = false)

        /**
         * Works out the best line available for a journey.
         *
         * Prefers the per-leg road geometry bustimes derives from route links.
         * Some services have none at all — bustimes' own service geometry for
         * them is two points per segment, i.e. straight stop-to-stop lines —
         * so the fallback joins the calling points and says so, letting the
         * map draw it dashed rather than implying a road that isn't known.
         *
         * @param times the journey's calling points.
         * @return the geometry to draw.
         */
        fun forTimes(times: List<StopTime>): RouteGeometry {
            val tracked = times.mapNotNull { time ->
                time.track?.takeIf { leg -> leg.size >= MIN_POINTS && leg.all(::isPoint) }
            }
            if (tracked.isNotEmpty()) {
                return RouteGeometry(legs = tracked, approximate = false)
            }
            val stops = times.mapNotNull { time -> time.stop.location?.takeIf(::isPoint) }
            if (stops.size < MIN_POINTS) {
                return None
            }
            return RouteGeometry(legs = listOf(stops), approximate = true)
        }

        /** Guards against a coordinate pair that is not a pair. */
        private fun isPoint(point: List<Double>): Boolean = point.size >= 2

        private const val MIN_POINTS = 2
    }
}
