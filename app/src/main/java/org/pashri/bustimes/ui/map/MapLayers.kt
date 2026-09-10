package org.pashri.bustimes.ui.map

import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeOpacity
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
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOpacity
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
    const val SOURCE_SIBLING_ROUTES = "sibling-routes-source"
    const val SOURCE_ROUTE_STOPS = "route-stops-source"

    const val LAYER_VEHICLES = "vehicles-layer"
    const val LAYER_VEHICLE_HEADINGS = "vehicle-headings-layer"
    const val LAYER_VEHICLE_LABELS = "vehicle-labels-layer"
    const val LAYER_STOPS = "stops-layer"
    const val LAYER_ROUTE = "route-layer"
    const val LAYER_ROUTE_CASING = "route-casing-layer"
    const val LAYER_ROUTE_DASHED = "route-dashed-layer"
    const val LAYER_SIBLING_ROUTES = "sibling-routes-layer"
    const val LAYER_ROUTE_CASING_DASHED = "route-casing-dashed-layer"
    const val LAYER_ROUTE_STOPS = "route-stops-layer"

    /** Every source, created empty. */
    fun sources(): List<GeoJsonSource> = listOf(
        GeoJsonSource(SOURCE_SIBLING_ROUTES, MapGeoJson.empty()),
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

    /**
     * The route line for a service with no road geometry.
     *
     * A separate layer rather than a dash pattern toggled on the solid one.
     * "No dashes" cannot be expressed as a dash array: the spec reads the
     * array as alternating dash and gap lengths, so a single-element array
     * meant to mean "solid" renders as a dotted line instead. Two layers with
     * one visible at a time has no such ambiguity.
     */
    fun routeDashed(): LineLayer = LineLayer(LAYER_ROUTE_DASHED, SOURCE_ROUTE).withProperties(
        lineColor("#1B5E20"),
        lineWidth(ROUTE_WIDTH),
        lineCap(Property.LINE_CAP_BUTT),
        lineJoin(Property.LINE_JOIN_ROUND),
        lineDasharray(arrayOf(DASH_ON, DASH_OFF)),
    )

    /** The casing beneath [routeDashed], dashed to match so it does not fill the gaps. */
    fun routeCasingDashed(): LineLayer =
        LineLayer(LAYER_ROUTE_CASING_DASHED, SOURCE_ROUTE).withProperties(
            lineColor("#FFFFFF"),
            lineWidth(ROUTE_CASING_WIDTH),
            lineCap(Property.LINE_CAP_BUTT),
            lineJoin(Property.LINE_JOIN_ROUND),
            lineDasharray(arrayOf(CASING_DASH_ON, CASING_DASH_OFF)),
        )

    /**
     * Routes of the other buses running the focused service.
     *
     * Thinner than the selected journey's own line and drawn beneath it, so
     * the bus that was actually tapped stays unambiguous while its siblings
     * give the service's shape.
     */
    fun siblingRoutes(): LineLayer =
        LineLayer(LAYER_SIBLING_ROUTES, SOURCE_SIBLING_ROUTES).withProperties(
            lineColor(get(MapGeoJson.PROPERTY_COLOUR)),
            lineWidth(SIBLING_WIDTH),
            lineOpacity(SIBLING_OPACITY),
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

    /**
     * Ordinary stops.
     *
     * A circle layer rather than a bitmap symbol so the radius can follow the
     * zoom and the colour can be data-driven. At the zoom floor a stop-sized
     * marker would cover a whole street, and a screen holding three hundred
     * of them turns into a rash; shrinking them lets the floor come down a
     * level without that happening.
     */
    fun stops(): CircleLayer = CircleLayer(LAYER_STOPS, SOURCE_STOPS)
        .withProperties(
            circleColor(
                Expression.switchCase(
                    eq(get(MapGeoJson.PROPERTY_DIMMED), literal(true)),
                    literal(DIMMED_GREY),
                    literal(STOP_COLOUR),
                ),
            ),
            circleRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(STOP_MIN_ZOOM_STEP, literal(STOP_MIN_RADIUS)),
                    Expression.stop(STOP_MAX_ZOOM_STEP, literal(STOP_MAX_RADIUS)),
                ),
            ),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(
                Expression.switchCase(
                    eq(get(MapGeoJson.PROPERTY_DIMMED), literal(true)),
                    literal(0.0f),
                    literal(STOP_STROKE),
                ),
            ),
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
            // is itself zoom-scaled, and scaled up again when selected so the
            // arrow clears the larger selected circle instead of vanishing
            // underneath it.
            iconSize(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(
                        RADIUS_MIN_ZOOM,
                        selectedOr(MIN_ICON_SCALE * SELECTED_ICON_FACTOR, MIN_ICON_SCALE),
                    ),
                    Expression.stop(
                        RADIUS_MAX_ZOOM,
                        selectedOr(MAX_ICON_SCALE * SELECTED_ICON_FACTOR, MAX_ICON_SCALE),
                    ),
                ),
            ),
        )
        .withFilter(Expression.all(notDimmed(), notStale()))
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
            circleColor(
                Expression.switchCase(
                    eq(get(MapGeoJson.PROPERTY_DIMMED), literal(true)),
                    literal(DIMMED_GREY),
                    get(MapGeoJson.PROPERTY_COLOUR),
                ),
            ),
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
            // The white ring has to fade with the body. Left solid, it draws
            // a crisp outline around a bus whose position is old, which reads
            // as more certain than the fill it surrounds.
            circleOpacity(get(MapGeoJson.PROPERTY_OPACITY)),
            circleStrokeOpacity(get(MapGeoJson.PROPERTY_OPACITY)),
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
            textOpacity(get(MapGeoJson.PROPERTY_OPACITY)),
        )
        // A line number greyed to the same tone as its circle is an
        // illegible mark that still draws the eye, so dimmed buses lose
        // their labels and their arrows entirely rather than keeping
        // unreadable ones.
        .withFilter(notDimmed())
        .apply { minZoom = LABEL_MIN_ZOOM }

    /** Matches only features that are not being played down. */
    private fun notDimmed(): Expression =
        Expression.not(eq(get(MapGeoJson.PROPERTY_DIMMED), literal(true)))

    /** Matches only features whose position is recent enough to have a bearing. */
    private fun notStale(): Expression =
        Expression.not(eq(get(MapGeoJson.PROPERTY_STALE), literal(true)))

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

    private const val DASH_ON = 1.6f
    private const val DASH_OFF = 1.4f

    private const val SIBLING_WIDTH = 2.5f
    private const val SIBLING_OPACITY = 0.85f
    private const val ROUTE_WIDTH = 4.5f
    private const val ROUTE_CASING_WIDTH = 8.0f

    /**
     * The casing's dashes, in units of its own wider line width.
     *
     * Scaled down from the line's pattern because dash lengths multiply by
     * line width: reusing the same numbers on a wider line would make the
     * white casing's dashes longer than the green ones they sit under.
     */
    private const val CASING_DASH_ON = DASH_ON * ROUTE_WIDTH / ROUTE_CASING_WIDTH
    private const val CASING_DASH_OFF = DASH_OFF * ROUTE_WIDTH / ROUTE_CASING_WIDTH
    private const val LABEL_SIZE = 11.0f
    private const val LABEL_HALO = 1.6f
    private const val LABEL_MIN_ZOOM = 12.0f
    private const val MIN_RADIUS = 5.0f
    private const val MIN_RADIUS_AT_MAX = 13.0f
    private const val SELECTED_MIN_RADIUS = 8.0f
    private const val SELECTED_MAX_RADIUS = 17.0f
    private const val RADIUS_MIN_ZOOM = 10
    private const val RADIUS_MAX_ZOOM = 16
    /** The single tone everything played down is drawn in. */
    private const val DIMMED_GREY = "#C9CDD2"
    private const val STOP_COLOUR = "#5A5A5A"
    private const val STOP_MIN_RADIUS = 3.5f
    private const val STOP_MAX_RADIUS = 6.0f
    private const val STOP_STROKE = 1.5f
    private const val STOP_MIN_ZOOM_STEP = 13
    private const val STOP_MAX_ZOOM_STEP = 16

    private const val MIN_ICON_SCALE = 0.45f
    private const val MAX_ICON_SCALE = 1.0f

    /**
     * How much bigger the arrow is drawn for the selected bus.
     *
     * The selected body circle grows from 13 px to 17 px, which reaches past
     * where the arrow orbits, so without this the direction indicator is
     * hidden on exactly the bus whose direction is being looked at.
     */
    private const val SELECTED_ICON_FACTOR = 1.6f
    private const val STROKE = 2.0f
    private const val SELECTED_STROKE = 3.5f
}
