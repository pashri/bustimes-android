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
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
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
            // The icon is centred on the bus and drawn with an empty middle,
            // so rotation swings the arrowhead around the position rather
            // than displacing the whole glyph away from it.
            iconAnchor(Property.ICON_ANCHOR_CENTER),
            // Scaled with zoom to stay proportional to the body circle, which
            // is itself zoom-scaled.
            iconSize(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(RADIUS_MIN_ZOOM, literal(MIN_ICON_SCALE)),
                    Expression.stop(RADIUS_MAX_ZOOM, literal(MAX_ICON_SCALE)),
                ),
            ),
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
            // A single radius cannot serve every zoom: 11 px covers a few
            // hundred metres of ground at z10, so every bus appears to sit on
            // top of the buildings around it however accurate the fix is.
            circleRadius(radiusByZoom()),
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

    /**
     * Interpolates the body radius across the zooms vehicles are drawn at,
     * with the selected bus drawn larger.
     *
     * The zoom expression has to be the input of the *outermost* interpolate:
     * the style spec forbids a zoom expression nested inside another
     * expression, and a `switchCase` wrapping two `interpolate`s is silently
     * rejected — which takes the whole paint property with it, so the layer
     * falls back to a default radius and a default black fill.
     *
     * @return a zoom-driven, selection-aware radius expression.
     */
    private fun radiusByZoom(): Expression =
        Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(RADIUS_MIN_ZOOM, selectedOr(SELECTED_MIN_RADIUS, MIN_RADIUS)),
            Expression.stop(RADIUS_MAX_ZOOM, selectedOr(SELECTED_MAX_RADIUS, MIN_RADIUS_AT_MAX)),
        )

    /** Chooses between two sizes depending on whether a feature is selected. */
    private fun selectedOr(whenSelected: Float, otherwise: Float): Expression =
        Expression.switchCase(
            eq(get(MapGeoJson.PROPERTY_SELECTED), literal(true)),
            literal(whenSelected),
            literal(otherwise),
        )

    private const val ROUTE_WIDTH = 4.5f
    private const val ROUTE_CASING_WIDTH = 8.0f
    private const val LABEL_SIZE = 11.0f
    private const val LABEL_HALO = 1.6f
    private const val LABEL_MIN_ZOOM = 12.0f
    private const val MIN_RADIUS = 5.0f
    private const val MIN_RADIUS_AT_MAX = 13.0f
    private const val SELECTED_MIN_RADIUS = 8.0f
    private const val SELECTED_MAX_RADIUS = 17.0f
    private const val RADIUS_MIN_ZOOM = 10
    private const val RADIUS_MAX_ZOOM = 16
    private const val MIN_ICON_SCALE = 0.45f
    private const val MAX_ICON_SCALE = 1.0f
    private const val STROKE = 2.0f
    private const val SELECTED_STROKE = 3.5f
}
