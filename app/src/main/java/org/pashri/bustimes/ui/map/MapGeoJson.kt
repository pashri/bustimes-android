package org.pashri.bustimes.ui.map

import com.google.gson.JsonObject
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
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
     * Builds the vehicle layer's source data.
     *
     * @param vehicles the vehicles to draw.
     * @param selectedId the vehicle drawn in the selected style, if any.
     * @return a collection of point features, one per vehicle.
     */
    fun vehicles(vehicles: List<Vehicle>, selectedId: Long?): FeatureCollection {
        val features = vehicles.map { vehicle ->
            val properties = JsonObject().apply {
                addProperty(PROPERTY_VEHICLE_ID, vehicle.id)
                addProperty(PROPERTY_LABEL, vehicle.service?.lineName.orEmpty())
                addProperty(PROPERTY_BEARING, vehicle.heading ?: 0.0)
                addProperty(PROPERTY_COLOUR, liveryColour(vehicle))
                addProperty(PROPERTY_SELECTED, vehicle.id == selectedId)
            }
            Feature.fromGeometry(
                Point.fromLngLat(vehicle.longitude, vehicle.latitude),
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
     * @return a collection of point features, one per stop.
     */
    fun stops(stops: List<StopFeature>, selectedAtco: String?): FeatureCollection {
        val features = stops.map { stop ->
            val properties = JsonObject().apply {
                addProperty(PROPERTY_ATCO, stop.atcoCode.orEmpty())
                addProperty(PROPERTY_LABEL, stop.properties.name)
                addProperty(PROPERTY_BEARING, stop.properties.bearing ?: 0.0)
                addProperty(PROPERTY_SELECTED, stop.atcoCode != null && stop.atcoCode == selectedAtco)
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

    /** An empty collection, used to clear a source without removing its layer. */
    fun empty(): FeatureCollection = FeatureCollection.fromFeatures(emptyList<Feature>())

    /**
     * Picks a drawable colour for a vehicle.
     *
     * `colour` is a flat livery colour when the operator has one. `css` is a
     * gradient for multi-colour liveries and is only usable when it happens
     * to be a single hex value, matching what the site's own map does.
     */
    private fun liveryColour(vehicle: Vehicle): String {
        val detail = vehicle.vehicle
        val flat = detail?.colour?.takeIf { it.startsWith("#") && it.length == HEX_LENGTH }
        val css = detail?.css?.takeIf { it.startsWith("#") && it.length == HEX_LENGTH }
        return flat ?: css ?: DEFAULT_COLOUR
    }

    private const val HEX_LENGTH = 7
    private const val DEFAULT_COLOUR = "#2E7D32"
}
