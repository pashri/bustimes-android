package org.pashri.bustimes.ui.map

import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Source and layer identifiers, and the layers themselves.
 *
 * Layers are created once when the style loads and then only ever have their
 * source data replaced. Nothing is added or removed as the user navigates,
 * which is what keeps stale geometry off the map.
 */
object MapLayers {

    const val SOURCE_VEHICLES = "vehicles-source"
    const val SOURCE_STOPS = "stops-source"
    const val SOURCE_ROUTE = "route-source"
    const val SOURCE_ROUTE_STOPS = "route-stops-source"

    const val LAYER_VEHICLES = "vehicles-layer"
    const val LAYER_VEHICLE_HEADINGS = "vehicle-headings-layer"
    const val LAYER_VEHICLE_LABELS = "vehicle-labels-layer"
    const val LAYER_STOPS = "stops-layer"
    const val LAYER_ROUTE = "route-layer"
    const val LAYER_ROUTE_CASING = "route-casing-layer"
    const val LAYER_ROUTE_STOPS = "route-stops-layer"

    /** All four sources, created empty. */
    fun sources(): List<GeoJsonSource> = listOf(
        GeoJsonSource(SOURCE_ROUTE, MapGeoJson.empty()),
        GeoJsonSource(SOURCE_ROUTE_STOPS, MapGeoJson.empty()),
        GeoJsonSource(SOURCE_STOPS, MapGeoJson.empty()),
        GeoJsonSource(SOURCE_VEHICLES, MapGeoJson.empty()),
    )

    /**
     * The route line, drawn as a wide casing under a narrower coloured line so
     * it stays readable over any basemap.
     */
    fun routeCasing(): LineLayer = LineLayer(LAYER_ROUTE_CASING, SOURCE_ROUTE).withProperties(
        lineColor("#FFFFFF"),
        lineWidth(ROUTE_CASING_WIDTH),
        lineCap(Property.LINE_CAP_ROUND),
        lineJoin(Property.LINE_JOIN_ROUND),
    )

    /** The coloured route line. */
    fun route(): LineLayer = LineLayer(LAYER_ROUTE, SOURCE_ROUTE).withProperties(
        lineColor("#1B5E20"),
        lineWidth(ROUTE_WIDTH),
        lineCap(Property.LINE_CAP_ROUND),
        lineJoin(Property.LINE_JOIN_ROUND),
    )

    /** Calling points of the selected route. */
    fun routeStops(): SymbolLayer = SymbolLayer(LAYER_ROUTE_STOPS, SOURCE_ROUTE_STOPS)
        .withProperties(
            iconImage(MapIcons.STOP_ROUTE),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        )

    /** Ordinary stops, shown from zoom 14 as bustimes.org does. */
    fun stops(): SymbolLayer = SymbolLayer(LAYER_STOPS, SOURCE_STOPS)
        .withProperties(
            iconImage(MapIcons.STOP),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        )
        .apply { minZoom = MapDefaults.STOPS_MIN_ZOOM.toFloat() }

    /**
     * The heading wedge, drawn under each bus so it reads as a direction.
     *
     * Kept as its own layer because a bitmap symbol cannot be tinted per
     * feature, whereas the body below is a circle and can be.
     */
    fun vehicleHeadings(): SymbolLayer = SymbolLayer(LAYER_VEHICLE_HEADINGS, SOURCE_VEHICLES)
        .withProperties(
            iconImage(MapIcons.HEADING),
            iconRotate(get(MapGeoJson.PROPERTY_BEARING)),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
            iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
        )
        .apply { minZoom = MapDefaults.VEHICLES_MIN_ZOOM.toFloat() }

    /**
     * The bus body, filled with the operator's livery colour.
     *
     * A circle layer rather than a bitmap symbol, because `circle-color` can
     * be data-driven per feature while `icon-image` cannot be tinted. This is
     * also why a single layer copes with any number of vehicles: unlike the
     * web map there are no per-marker views to become expensive, so there is
     * no need to degrade to plain dots when a viewport is dense.
     */
    fun vehicles(): CircleLayer = CircleLayer(LAYER_VEHICLES, SOURCE_VEHICLES)
        .withProperties(
            circleColor(get(MapGeoJson.PROPERTY_COLOUR)),
            circleRadius(
                Expression.switchCase(
                    eq(get(MapGeoJson.PROPERTY_SELECTED), literal(true)),
                    literal(SELECTED_RADIUS),
                    literal(RADIUS),
                ),
            ),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(
                Expression.switchCase(
                    eq(get(MapGeoJson.PROPERTY_SELECTED), literal(true)),
                    literal(SELECTED_STROKE),
                    literal(STROKE),
                ),
            ),
        )
        .apply { minZoom = MapDefaults.VEHICLES_MIN_ZOOM.toFloat() }

    /**
     * Line numbers drawn inside the bus markers.
     *
     * The halo does the work of keeping the number readable against whatever
     * livery colour sits behind it, which ranges from white to near-black.
     */
    fun vehicleLabels(): SymbolLayer = SymbolLayer(LAYER_VEHICLE_LABELS, SOURCE_VEHICLES)
        .withProperties(
            textField(get(MapGeoJson.PROPERTY_LABEL)),
            textSize(LABEL_SIZE),
            textFont(arrayOf("Noto Sans Regular")),
            textColor("#111111"),
            textHaloColor("#FFFFFF"),
            textHaloWidth(LABEL_HALO),
            textAllowOverlap(true),
            textIgnorePlacement(true),
        )
        .apply { minZoom = LABEL_MIN_ZOOM }

    private const val ROUTE_WIDTH = 4.5f
    private const val ROUTE_CASING_WIDTH = 8.0f
    private const val LABEL_SIZE = 11.0f
    private const val LABEL_HALO = 1.6f
    private const val LABEL_MIN_ZOOM = 12.0f
    private const val RADIUS = 11.0f
    private const val SELECTED_RADIUS = 15.0f
    private const val STROKE = 2.0f
    private const val SELECTED_STROKE = 3.5f
}
