package org.pashri.bustimes.ui.map

import com.google.gson.JsonObject
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.pashri.bustimes.data.model.Freshness
import org.pashri.bustimes.data.model.StopFeature
import org.pashri.bustimes.data.model.StopTime
import org.pashri.bustimes.data.model.Vehicle

/**
 * Converts app models into the GeoJSON that MapLibre's sources consume.
 *
 * Everything on the map is a symbol, circle or line layer fed from a single
 * GeoJSON source, rather than one view per bus. That is the difference that
 * matters on Android: bustimes.org's web map switches to plain dots past a
 * thousand vehicles because a thousand DOM nodes is too many, but a symbol
 * layer draws tens of thousands without breaking a sweat, so the detailed
 * marker can be kept at every density.
 */
object MapGeoJson {

    /** Feature property holding a vehicle's id. */
    const val PROPERTY_VEHICLE_ID = "vehicleId"

    /** Feature property holding a stop's ATCO code. */
    const val PROPERTY_ATCO = "atco"

    /** Feature property holding a display label. */
    const val PROPERTY_LABEL = "label"

    /** Feature property holding a bearing in degrees clockwise from north. */
    const val PROPERTY_BEARING = "bearing"

    /** Feature property holding a livery colour as `#rrggbb`. */
    const val PROPERTY_COLOUR = "colour"

    /** Feature property flagging the selected feature. */
    const val PROPERTY_SELECTED = "selected"

    /**
     * Feature property flagging a feature to be played down.
     *
     * Set on everything that is not part of the selected service, so a route
     * and its calling points can be read against a quiet background rather
     * than competing with every other bus and stop on screen.
     */
    const val PROPERTY_DIMMED = "dimmed"

    /**
     * Feature property holding how solidly to draw a vehicle, 0 to 1.
     *
     * Computed in Kotlin rather than as a style `interpolate` expression,
     * because it is derived from the clock rather than from the zoom or a
     * flag: an age is app state that happens to be rendered, and keeping the
     * ramp out of the style is what makes it unit-testable.
     */
    const val PROPERTY_OPACITY = "opacity"

    /**
     * Feature property flagging a position too old to imply a direction.
     *
     * Drives the heading layer's filter, the same way [PROPERTY_DIMMED] gates
     * the layers that would be illegible.
     */
    const val PROPERTY_STALE = "stale"

    /**
     * Builds the vehicle layer's source data.
     *
     * @param vehicles the vehicles to draw.
     * @param selectedId the vehicle drawn in the selected style, if any.
     * @param nowMillis the current time from `ClockSkew.now()`, against which
     *   each position's age is measured. Deliberately has no default: a
     *   caller that forgot to pass a clock would silently draw a stale fleet
     *   as current, which is the very bug this property exists to fix.
     * @param positions overrides a vehicle's drawn position by id, used to
     *   animate between two reported fixes. Vehicles absent from the map are
     *   drawn where they were reported.
     * @param dimOtherServices when set, every vehicle not running this service
     *   is flagged to be played down.
     * @return a collection of point features, one per vehicle.
     */
    fun vehicles(
        vehicles: List<Vehicle>,
        selectedId: Long?,
        nowMillis: Long,
        positions: Map<Long, DoubleArray> = emptyMap(),
        dimOtherServices: Long? = null,
    ): FeatureCollection {
        val features = vehicles.map { vehicle ->
            val drawn = positions[vehicle.id]
            val age = Freshness.ageSeconds(vehicle.datetime, nowMillis)
            val properties = JsonObject().apply {
                addProperty(PROPERTY_VEHICLE_ID, vehicle.id)
                addProperty(PROPERTY_LABEL, vehicle.service?.lineName.orEmpty())
                addProperty(PROPERTY_BEARING, vehicle.heading ?: 0.0)
                addProperty(PROPERTY_COLOUR, liveryColour(vehicle))
                addProperty(PROPERTY_SELECTED, vehicle.id == selectedId)
                addProperty(PROPERTY_OPACITY, Freshness.opacityForAge(age))
                addProperty(PROPERTY_STALE, Freshness.isArrowStale(age))
                addProperty(
                    PROPERTY_DIMMED,
                    dimOtherServices != null && vehicle.serviceId != dimOtherServices,
                )
            }
            Feature.fromGeometry(
                Point.fromLngLat(
                    drawn?.get(0) ?: vehicle.longitude,
                    drawn?.get(1) ?: vehicle.latitude,
                ),
                properties,
                vehicle.id.toString(),
            )
        }
        return FeatureCollection.fromFeatures(features)
    }

    /**
     * Builds the stop layer's source data.
     *
     * @param stops the stops to draw.
     * @param selectedAtco the stop drawn in the selected style, if any.
     * @param dimmed whether these stops should be played down, because a
     *   route's own calling points are the ones being read.
     * @return a collection of point features, one per stop.
     */
    fun stops(
        stops: List<StopFeature>,
        selectedAtco: String?,
        dimmed: Boolean = false,
    ): FeatureCollection {
        val features = stops.map { stop ->
            val properties = JsonObject().apply {
                addProperty(PROPERTY_ATCO, stop.atcoCode.orEmpty())
                addProperty(PROPERTY_LABEL, stop.properties.name)
                addProperty(PROPERTY_BEARING, stop.properties.bearing ?: 0.0)
                addProperty(PROPERTY_SELECTED, stop.atcoCode != null && stop.atcoCode == selectedAtco)
                addProperty(PROPERTY_DIMMED, dimmed)
            }
            Feature.fromGeometry(
                Point.fromLngLat(stop.longitude, stop.latitude),
                properties,
                stop.atcoCode,
            )
        }
        return FeatureCollection.fromFeatures(features)
    }

    /**
     * Builds the source for the ring marking the selected stop.
     *
     * Its own source, rather than a filter over the stop layers, because the
     * selected stop is not always present in either of them: below the stop
     * layer's minimum zoom, or before the surrounding viewport has loaded,
     * there would be nothing to filter.
     *
     * @param stop the selected stop, if any.
     * @return a single point feature, or an empty collection when nothing is
     *   selected or the stop's position is unknown.
     */
    fun selectedStop(stop: SelectedStop?): FeatureCollection {
        val longitude = stop?.longitude
        val latitude = stop?.latitude
        if (longitude == null || latitude == null) return empty()
        val properties = JsonObject().apply {
            addProperty(PROPERTY_ATCO, stop.atcoCode)
        }
        val feature = Feature.fromGeometry(
            Point.fromLngLat(longitude, latitude),
            properties,
            stop.atcoCode,
        )
        return FeatureCollection.fromFeatures(listOf(feature))
    }

    /**
     * Builds the route line from a trip's per-leg road geometry.
     *
     * Each leg is kept as its own feature rather than concatenated, because
     * legs are not guaranteed to join exactly and a single line string would
     * draw visible spurs between them.
     *
     * @param legs `[lon, lat]` pairs per leg, in route order.
     * @return one line feature per leg.
     */
    fun routeLines(legs: List<List<List<Double>>>): FeatureCollection {
        val features = legs.filter { it.size >= 2 }.map { leg ->
            LineString.fromLngLats(leg.map { Point.fromLngLat(it[0], it[1]) })
        }.map(Feature::fromGeometry)
        return FeatureCollection.fromFeatures(features)
    }

    /**
     * Builds the stops belonging to the selected trip.
     *
     * These are drawn on their own layer so they stay visible below the zoom
     * at which the general stop layer appears — having tapped a bus, its
     * calling points are the thing you want to see.
     *
     * @param times the trip's stop times.
     * @return a collection of point features for stops with a known position.
     */
    fun routeStops(times: List<StopTime>): FeatureCollection {
        val features = times.mapNotNull { time ->
            val location = time.stop.location ?: return@mapNotNull null
            val properties = JsonObject().apply {
                addProperty(PROPERTY_ATCO, time.stop.atcoCode.orEmpty())
                addProperty(PROPERTY_LABEL, time.stop.name)
            }
            Feature.fromGeometry(
                Point.fromLngLat(location[0], location[1]),
                properties,
                time.stop.atcoCode,
            )
        }
        return FeatureCollection.fromFeatures(features)
    }

    /**
     * Builds the lines for other buses on the focused service.
     *
     * Each is coloured by its own bus's livery, which for a branded service
     * is that route's colour, so several buses on one corridor stay tellable
     * apart from each other and from everything greyed out around them.
     *
     * @param routes the sibling routes to draw.
     * @return one line feature per leg, carrying its colour.
     */
    fun siblingRoutes(routes: List<SiblingRoute>): FeatureCollection {
        val features = routes.flatMap { route ->
            route.legs.filter { leg -> leg.size >= 2 }.map { leg ->
                val properties = JsonObject().apply {
                    addProperty(PROPERTY_COLOUR, route.colour)
                    addProperty(PROPERTY_VEHICLE_ID, route.vehicleId)
                }
                Feature.fromGeometry(
                    LineString.fromLngLats(leg.map { Point.fromLngLat(it[0], it[1]) }),
                    properties,
                )
            }
        }
        return FeatureCollection.fromFeatures(features)
    }

    /**
     * Picks a drawable colour for a vehicle.
     *
     * `colour` is a flat livery colour when the operator has one. `css` is a
     * gradient for multi-colour liveries and is only usable when it happens
     * to be a single hex value, matching what the site's own map does.
     *
     * @param vehicle the bus to colour.
     * @return a `#rrggbb` colour.
     */
    fun liveryColour(vehicle: Vehicle): String {
        val detail = vehicle.vehicle
        val flat = detail?.colour?.takeIf { it.startsWith("#") && it.length == HEX_LENGTH }
        val css = detail?.css?.takeIf { it.startsWith("#") && it.length == HEX_LENGTH }
        return flat ?: css ?: DEFAULT_COLOUR
    }

    /** An empty collection, used to clear a source without removing its layer. */
    fun empty(): FeatureCollection = FeatureCollection.fromFeatures(emptyList<Feature>())

    private const val HEX_LENGTH = 7
    private const val DEFAULT_COLOUR = "#2E7D32"
}
