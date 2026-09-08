package org.pashri.bustimes.ui.map

import org.pashri.bustimes.data.model.StopFeature
import org.pashri.bustimes.data.model.StopTime
import org.pashri.bustimes.data.model.Vehicle
import org.pashri.bustimes.data.net.BoundingBox

/**
 * Everything drawn on the map, as one value.
 *
 * The map is rendered as a pure function of this state and never mutated
 * imperatively. Adding and removing layers by hand as the user drills from a
 * bus to a stop and back reliably leaves orphaned polylines behind; deriving
 * the whole picture from navigation state makes that class of bug impossible,
 * because nothing is ever "removed" — it simply stops being present.
 */
data class MapDecorations(
    val stops: List<StopFeature> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    /** Road geometry of the selected trip, as `[lon, lat]` pairs per leg. */
    val routeLegs: List<List<List<Double>>> = emptyList(),
    /**
     * True when [routeLegs] joins stops in straight lines rather than
     * following roads.
     *
     * Some services carry no road geometry at all — bustimes has none for
     * them either — so the only line that can be drawn is stop to stop. It is
     * drawn dashed, because a solid line would imply the bus takes that path
     * across the fields.
     */
    val routeIsApproximate: Boolean = false,
    /** Dimmed when a stop on the route is selected, so context is kept. */
    val routeDimmed: Boolean = false,
    val selectedVehicleId: Long? = null,
    /**
     * The service whose buses keep their colours, if any.
     *
     * Everything not running it is drawn in one flat grey, so a route and its
     * calling points can be read without competing with every other bus and
     * stop on screen. Null means nothing is played down.
     */
    val focusedServiceId: Long? = null,
    /**
     * Routes of the other buses running the focused service.
     *
     * Each carries its own colour, taken from the bus's livery — the colour
     * of its circle — because bustimes exposes no route colour in any JSON.
     */
    val siblingRoutes: List<SiblingRoute> = emptyList(),
    val selectedStopAtco: String? = null,
    /** Stops of the selected trip, drawn larger than surrounding stops. */
    val routeStops: List<StopTime> = emptyList(),
) {
    /**
     * Everything drawn for a selection, removed.
     *
     * Enumerating the selection-related fields at each call site meant one of
     * them could be — and was — forgotten: clearing a selection left the
     * focused service behind, so every other bus stayed greyed out with
     * nothing selected to explain it. Keeping the reset here means a new
     * selection field is cleared by construction.
     *
     * @return the same stops and vehicles, with no selection drawn.
     */
    fun withoutSelection(): MapDecorations = MapDecorations(
        stops = stops,
        vehicles = vehicles,
    )
}

/**
 * Another bus's route on the focused service.
 *
 * @property vehicleId the bus this route belongs to.
 * @property legs `[lon, lat]` pairs, one list per leg.
 * @property colour the line's colour as `#rrggbb`.
 */
data class SiblingRoute(
    val vehicleId: Long,
    val legs: List<List<List<Double>>>,
    val colour: String,
)

/** The map camera, persisted so the app reopens where it was left. */
data class CameraState(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
) {
    /** True when vehicles should be fetched and drawn at this zoom. */
    val showsVehicles: Boolean get() = zoom >= MapDefaults.VEHICLES_MIN_ZOOM

    /** True when stops should be fetched and drawn at this zoom. */
    val showsStops: Boolean get() = zoom >= MapDefaults.STOPS_MIN_ZOOM

    companion object {
        /** The whole-UK view used when there is nothing better to show. */
        val UnitedKingdom = CameraState(
            latitude = MapDefaults.UK_LATITUDE,
            longitude = MapDefaults.UK_LONGITUDE,
            zoom = MapDefaults.UK_ZOOM,
        )
    }
}

/**
 * Tracks the largest area already fetched, so panning inside it costs nothing.
 *
 * bustimes.org calls this a high-water mark: if the new viewport is contained
 * by what was last fetched, the vehicles already in hand cover it and only the
 * routine refresh is needed. Zooming in therefore never triggers a request.
 */
class VehicleHighWaterMark {

    private var fetched: BoundingBox? = null
    private var lastCount: Int = 0

    /**
     * Decides whether a viewport needs a fresh fetch.
     *
     * @param viewport the current camera bounds.
     * @return true when the box is not already covered.
     */
    fun needsFetch(viewport: BoundingBox): Boolean {
        val covered = fetched?.contains(viewport) ?: false
        // Upstream also refetches unconditionally past 1000 vehicles. There is
        // no server-side cap — a national bbox returns far more — so a large
        // response means the picture is dense enough that containment is not
        // a safe reason to skip a refresh.
        return !covered || lastCount >= DENSE_RESPONSE
    }

    /**
     * Records a completed fetch.
     *
     * @param box the box that was fetched.
     * @param count how many vehicles came back.
     */
    fun record(box: BoundingBox, count: Int) {
        fetched = box
        lastCount = count
    }

    /** Forgets what has been fetched, forcing the next viewport to refetch. */
    fun reset() {
        fetched = null
        lastCount = 0
    }

    private companion object {
        const val DENSE_RESPONSE = 1_000
    }
}
