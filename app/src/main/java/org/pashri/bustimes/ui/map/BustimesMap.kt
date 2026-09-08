package org.pashri.bustimes.ui.map

import android.animation.ValueAnimator
import android.content.Context
import android.view.Gravity
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.pashri.bustimes.data.net.BoundingBox

/**
 * A MapLibre map, driven entirely by [decorations].
 *
 * There is no Compose-native MapLibre binding, so the `MapView` is wrapped
 * once here and its lifecycle forwarded. Callers never touch the map object:
 * they hand over a state value and the map is reconciled to match it, which is
 * what stops route lines and selections leaking between screens.
 *
 * @param decorations everything to draw.
 * @param initialCamera where to open; only read on first composition.
 * @param darkTheme selects the basemap style.
 * @param locationEnabled whether to show the user's own position.
 * @param bottomInset height obscured by the sheet. Applied as map padding, so
 *   the camera treats the visible area as the map, and used to lift the
 *   compass clear of the sheet.
 * @param favouritesVisible whether a favourites button is stacked above the
 *   locate button, which the compass must clear.
 * @param compassHidden whether to hide the compass, because the favourites
 *   menu is open where it would otherwise sit.
 * @param onCameraIdle called when the camera settles, with the new viewport.
 * @param onVehicleTapped called with a tapped vehicle's id.
 * @param onStopTapped called with a tapped stop's ATCO code.
 * @param moveTo when non-null, the camera eases to this position.
 * @param onMoveHandled called once a [moveTo] has been applied.
 * @param fitRoute when true, the camera frames the whole drawn route.
 * @param onFitRouteHandled called once the route has been framed.
 * @param modifier layout modifier.
 */
@Composable
fun BustimesMap(
    decorations: MapDecorations,
    initialCamera: CameraState,
    darkTheme: Boolean,
    locationEnabled: Boolean,
    bottomInset: Dp,
    favouritesVisible: Boolean,
    compassHidden: Boolean,
    onCameraIdle: (CameraState, BoundingBox, Boolean) -> Unit,
    onVehicleTapped: (Long) -> Unit,
    onStopTapped: (String) -> Unit,
    moveTo: LatLng?,
    onMoveHandled: () -> Unit,
    fitRoute: Boolean,
    onFitRouteHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { MapController(density.density) }
    val insetPx = with(density) { bottomInset.roundToPx() }
    // The locate button sits inside safeDrawingPadding, but the map view runs
    // edge to edge and its compass margins are measured from the view's own
    // bottom. Without adding the system inset the compass lands roughly a
    // navigation bar too low, which put it underneath the button.
    val safeDrawing = WindowInsets.safeDrawing
    val systemBottomPx = safeDrawing.getBottom(density)
    val systemRightPx = safeDrawing.getRight(density, LocalLayoutDirection.current)
    // The compass sits outermost of the stacked buttons, because it comes and
    // goes with the map's rotation and a transient control must not displace
    // the persistent ones beneath it.
    val stackHeight = if (favouritesVisible) FAB_SIZE * 2 + FAB_STACK_GAP else FAB_SIZE
    val compassBottomPx = with(density) {
        systemBottomPx + (bottomInset + FAB_MARGIN + stackHeight + COMPASS_GAP).roundToPx()
    }
    val compassRightPx = with(density) { systemRightPx + FAB_MARGIN.roundToPx() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> controller.onLifecycleEvent(event) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            MapLibre.getInstance(context)
            MapView(context).also { view ->
                controller.attach(
                    view = view,
                    context = context,
                    initialCamera = initialCamera,
                    darkTheme = darkTheme,
                    onCameraIdle = onCameraIdle,
                    onVehicleTapped = onVehicleTapped,
                    onStopTapped = onStopTapped,
                )
            }
        },
        update = {
            controller.setStyleForTheme(darkTheme)
            controller.setChrome(
                bottomPaddingPx = insetPx,
                compassBottomPx = compassBottomPx,
                compassRightPx = compassRightPx,
                compassVisible = !compassHidden,
            )
            controller.setLocationEnabled(locationEnabled)
            controller.render(decorations)
            if (moveTo != null && controller.easeTo(moveTo)) {
                onMoveHandled()
            }
            if (fitRoute && controller.frameRoute(decorations.routeLegs)) {
                onFitRouteHandled()
            }
        },
    )
}

/**
 * Holds the `MapView` and reconciles it against [MapDecorations].
 *
 * Kept outside composition so the map survives recomposition; a map is
 * expensive to create and recreating one on every state change would be
 * visible as a flash of empty basemap.
 */
private class MapController(private val density: Float) {

    private var view: MapView? = null
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var context: Context? = null
    private var currentStyleUrl: String? = null
    private var pendingDecorations: MapDecorations? = null
    private var pendingTarget: LatLng? = null
    private var locationActive = false
    private var onVehicleTapped: ((Long) -> Unit)? = null
    private var onStopTapped: ((String) -> Unit)? = null

    /** Where each vehicle is currently drawn, which is not where it was reported. */
    private val drawnPositions = mutableMapOf<Long, DoubleArray>()
    private var tween: ValueAnimator? = null
    private var latestVehicles: MapDecorations? = null

    fun attach(
        view: MapView,
        context: Context,
        initialCamera: CameraState,
        darkTheme: Boolean,
        onCameraIdle: (CameraState, BoundingBox, Boolean) -> Unit,
        onVehicleTapped: (Long) -> Unit,
        onStopTapped: (String) -> Unit,
    ) {
        this.view = view
        this.context = context
        this.onVehicleTapped = onVehicleTapped
        this.onStopTapped = onStopTapped
        view.onCreate(null)
        view.getMapAsync { ready ->
            map = ready
            ready.cameraPosition = initialCamera.toCameraPosition()
            // Facing north is the norm, so the compass hides itself until the
            // map is actually rotated.
            ready.uiSettings.setCompassGravity(Gravity.BOTTOM or Gravity.END)
            ready.uiSettings.setCompassFadeFacingNorth(true)
            loadStyle(MapStyles.forTheme(darkTheme), ready)
            pendingTarget?.let { target ->
                pendingTarget = null
                easeTo(target)
            }
            ready.addOnCameraIdleListener { reportCameraIdle(ready, onCameraIdle) }
            ready.addOnMapClickListener { point -> handleClick(ready, point) }
        }
    }

    fun onLifecycleEvent(event: Lifecycle.Event) {
        val view = view ?: return
        when (event) {
            Lifecycle.Event.ON_START -> view.onStart()
            Lifecycle.Event.ON_RESUME -> view.onResume()
            Lifecycle.Event.ON_PAUSE -> view.onPause()
            Lifecycle.Event.ON_STOP -> view.onStop()
            Lifecycle.Event.ON_DESTROY -> {
                tween?.cancel()
                view.onDestroy()
            }
            else -> Unit
        }
    }

    /**
     * Positions the map's own chrome and camera around the sheet.
     *
     * Map padding makes the camera treat the unobscured part of the map as the
     * whole map, so centring on a bus puts it above the sheet rather than
     * under it. The compass is moved to sit directly above the locate button,
     * because its default top-right position lands under the status bar and is
     * awkward to reach.
     */
    fun setChrome(
        bottomPaddingPx: Int,
        compassBottomPx: Int,
        compassRightPx: Int,
        compassVisible: Boolean,
    ) {
        val map = map ?: return
        map.uiSettings.isCompassEnabled = compassVisible
        // setPadding is deprecated in favour of CameraUpdateFactory.paddingTo,
        // but that issues a camera *movement* rather than declaring padding,
        // which is the wrong shape for something applied on every
        // recomposition. setPadding is idempotent, so it can be reapplied
        // freely, and is what keeps a selected bus above the sheet.
        @Suppress("DEPRECATION")
        map.setPadding(0, 0, 0, bottomPaddingPx)
        map.uiSettings.setCompassMargins(0, 0, compassRightPx, compassBottomPx)
    }

    /**
     * Shows or hides the user's own position.
     *
     * The accuracy ring is deliberately visible: it is the only thing that
     * tells the user whether a vague-looking position is the app's fault or
     * the fix's.
     */
    fun setLocationEnabled(enabled: Boolean) {
        val map = map ?: return
        val style = style ?: return
        val context = context ?: return
        if (!enabled) {
            if (locationActive) {
                map.locationComponent.isLocationComponentEnabled = false
                locationActive = false
            }
            return
        }
        if (locationActive) return
        try {
            map.locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style).build(),
            )
            map.locationComponent.isLocationComponentEnabled = true
            map.locationComponent.cameraMode = CameraMode.NONE
            map.locationComponent.renderMode = RenderMode.COMPASS
            locationActive = true
        } catch (error: SecurityException) {
            // Permission was revoked between the check and here.
            locationActive = false
        }
    }

    fun setStyleForTheme(darkTheme: Boolean) {
        val wanted = MapStyles.forTheme(darkTheme)
        val map = map ?: return
        if (wanted != currentStyleUrl) {
            locationActive = false
            loadStyle(wanted, map)
        }
    }

    /** Replaces every source's data so the map matches [decorations] exactly. */
    fun render(decorations: MapDecorations) {
        val style = style
        if (style == null || !style.isFullyLoaded) {
            pendingDecorations = decorations
            return
        }
        source(style, MapLayers.SOURCE_STOPS)?.setGeoJson(
            MapGeoJson.stops(
                stops = decorations.stops,
                selectedAtco = decorations.selectedStopAtco,
                dimmed = decorations.focusedServiceId != null,
            ),
        )
        source(style, MapLayers.SOURCE_SIBLING_ROUTES)
            ?.setGeoJson(MapGeoJson.siblingRoutes(decorations.siblingRoutes))
        source(style, MapLayers.SOURCE_ROUTE)
            ?.setGeoJson(MapGeoJson.routeLines(decorations.routeLegs))
        source(style, MapLayers.SOURCE_ROUTE_STOPS)
            ?.setGeoJson(MapGeoJson.routeStops(decorations.routeStops))
        applyRouteStyle(style, decorations)
        startTween(decorations)
    }

    /**
     * Animates vehicles from where they are drawn to where they were reported.
     *
     * Positions arrive every twelve seconds, so a bus at 30 mph moves about
     * 160 m between fixes — most of the screen at close zoom. Snapping reads
     * as a glitch, and the jump can visually cut across buildings. Every frame
     * drawn here is an interpolation between two genuinely reported positions,
     * so no position is ever invented ahead of the data.
     */
    private fun startTween(decorations: MapDecorations) {
        latestVehicles = decorations
        val targets = decorations.vehicles.associate { vehicle ->
            vehicle.id to doubleArrayOf(vehicle.longitude, vehicle.latitude)
        }
        val origins = targets.mapValues { (id, target) -> drawnPositions[id] ?: target }
        drawnPositions.keys.retainAll(targets.keys)

        tween?.cancel()
        if (origins.none { (id, from) -> !from.contentEquals(targets[id]) }) {
            drawnPositions.putAll(targets)
            drawVehicles(decorations, targets)
            return
        }
        tween = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MapDefaults.VEHICLE_TWEEN_MILLIS
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val frame = targets.mapValues { (id, to) ->
                    val from = origins.getValue(id)
                    doubleArrayOf(
                        from[0] + (to[0] - from[0]) * fraction,
                        from[1] + (to[1] - from[1]) * fraction,
                    )
                }
                drawnPositions.putAll(frame)
                latestVehicles?.let { drawVehicles(it, frame) }
            }
            start()
        }
    }

    private fun drawVehicles(decorations: MapDecorations, positions: Map<Long, DoubleArray>) {
        val style = style ?: return
        source(style, MapLayers.SOURCE_VEHICLES)?.setGeoJson(
            MapGeoJson.vehicles(
                vehicles = decorations.vehicles,
                selectedId = decorations.selectedVehicleId,
                positions = positions,
                dimOtherServices = decorations.focusedServiceId,
            ),
        )
    }

    /**
     * Eases the camera to [target].
     *
     * @param target where to move to.
     * @return true when the move was applied. A map that has not finished
     *   initialising cannot move yet, so the target is remembered and applied
     *   when it is ready; reporting success in that case would let the caller
     *   clear the request and lose it.
     */
    fun easeTo(target: LatLng): Boolean {
        val map = map
        if (map == null) {
            pendingTarget = target
            return false
        }
        val zoom = maxOf(map.cameraPosition.zoom, MapDefaults.LOCATED_ZOOM)
        map.easeCamera(CameraUpdateFactory.newLatLngZoom(target, zoom), EASE_MILLIS)
        return true
    }

    /**
     * Frames a whole route.
     *
     * Used when a journey is opened from a departure board or timetable rather
     * than by tapping a bus, where there may be no vehicle to centre on and
     * the useful view is the route end to end.
     *
     * @param legs the route's per-leg geometry.
     * @return true when the camera was moved.
     */
    fun frameRoute(legs: List<List<List<Double>>>): Boolean {
        val map = map ?: return false
        val points = legs.flatten()
        if (points.isEmpty()) return false
        val bounds = LatLngBounds.from(
            points.maxOf { it[1] },
            points.maxOf { it[0] },
            points.minOf { it[1] },
            points.minOf { it[0] },
        )
        val padding = (ROUTE_PADDING_DP * density).toInt()
        map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding), EASE_MILLIS)
        return true
    }

    private fun loadStyle(url: String, map: MapLibreMap) {
        currentStyleUrl = url
        map.setStyle(Style.Builder().fromUri(url)) { loaded ->
            style = loaded
            addImages(loaded)
            MapLayers.sources().forEach(loaded::addSource)
            addLayers(loaded)
            pendingDecorations?.let { render(it) }
            pendingDecorations = null
        }
    }

    private fun addImages(style: Style) {
        style.addImage(MapIcons.HEADING, MapIcons.heading(density))
        style.addImage(MapIcons.STOP, MapIcons.stop(density, onRoute = false))
        style.addImage(MapIcons.STOP_ROUTE, MapIcons.stop(density, onRoute = true))
    }

    /** Order matters: route under stops, stops under buses. */
    private fun addLayers(style: Style) {
        style.addLayer(MapLayers.siblingRoutes())
        style.addLayer(MapLayers.routeCasing())
        style.addLayer(MapLayers.route())
        style.addLayer(MapLayers.routeCasingDashed())
        style.addLayer(MapLayers.routeDashed())
        style.addLayer(MapLayers.stops())
        style.addLayer(MapLayers.routeStops())
        style.addLayer(MapLayers.vehicleHeadings())
        style.addLayer(MapLayers.vehicles())
        style.addLayer(MapLayers.vehicleLabels())
    }

    /**
     * Sets the route line's opacity and dash pattern.
     *
     * Dimming keeps the route on screen, rather than removing it, when a stop
     * on it is selected, so the reason you were looking at that stop stays
     * visible. Dashing marks a line that only joins the calling points.
     */
    private fun applyRouteStyle(style: Style, decorations: MapDecorations) {
        val opacity = if (decorations.routeDimmed) DIMMED_OPACITY else 1.0f
        val dashed = decorations.routeIsApproximate
        // Whether the route follows roads decides which pair of layers is
        // visible, rather than a dash pattern being switched on one pair. A
        // dash array cannot express "solid": the spec reads it as alternating
        // dash and gap lengths, so the single-element array previously used to
        // mean solid turned every road-following route into dots.
        setRouteLayer(style, MapLayers.LAYER_ROUTE, visible = !dashed, opacity = opacity)
        setRouteLayer(
            style,
            MapLayers.LAYER_ROUTE_CASING,
            visible = !dashed,
            opacity = opacity * CASING_FACTOR,
        )
        setRouteLayer(style, MapLayers.LAYER_ROUTE_DASHED, visible = dashed, opacity = opacity)
        setRouteLayer(
            style,
            MapLayers.LAYER_ROUTE_CASING_DASHED,
            visible = dashed,
            opacity = opacity * CASING_FACTOR,
        )
    }

    private fun setRouteLayer(style: Style, id: String, visible: Boolean, opacity: Float) {
        style.getLayer(id)?.setProperties(
            PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE),
            PropertyFactory.lineOpacity(opacity),
        )
    }

    private fun source(style: Style, id: String): GeoJsonSource? = style.getSourceAs(id)

    private fun reportCameraIdle(
        map: MapLibreMap,
        onCameraIdle: (CameraState, BoundingBox, Boolean) -> Unit,
    ) {
        val position = map.cameraPosition
        val bounds = map.projection.visibleRegion.latLngBounds
        val camera = CameraState(
            latitude = position.target?.latitude ?: 0.0,
            longitude = position.target?.longitude ?: 0.0,
            zoom = position.zoom,
        )
        val box = BoundingBox(
            west = bounds.longitudeWest,
            south = bounds.latitudeSouth,
            east = bounds.longitudeEast,
            north = bounds.latitudeNorth,
        )
        onCameraIdle(camera, box, true)
    }

    /**
     * Resolves a tap to a feature.
     *
     * Buses are queried before stops so a bus sitting at its stop is selected
     * in preference to the stop underneath it.
     */
    private fun handleClick(map: MapLibreMap, point: LatLng): Boolean {
        val screenPoint = map.projection.toScreenLocation(point)
        val vehicle = map.queryRenderedFeatures(screenPoint, MapLayers.LAYER_VEHICLES).firstOrNull()
        if (vehicle != null) {
            val id = vehicle.getNumberProperty(MapGeoJson.PROPERTY_VEHICLE_ID)?.toLong()
            if (id != null) {
                onVehicleTapped?.invoke(id)
                return true
            }
        }
        val stopLayers = arrayOf(MapLayers.LAYER_ROUTE_STOPS, MapLayers.LAYER_STOPS)
        val stop = map.queryRenderedFeatures(screenPoint, *stopLayers).firstOrNull()
        val atco = stop?.getStringProperty(MapGeoJson.PROPERTY_ATCO)
        if (!atco.isNullOrBlank()) {
            onStopTapped?.invoke(atco)
            return true
        }
        return false
    }

    private companion object {
        const val EASE_MILLIS = 600
        const val DIMMED_OPACITY = 0.35f
        const val CASING_FACTOR = 0.8f
        const val ROUTE_PADDING_DP = 48f
    }
}

/** Converts app camera state into MapLibre's own representation. */
private fun CameraState.toCameraPosition(): CameraPosition = CameraPosition.Builder()
    .target(LatLng(latitude, longitude))
    .zoom(zoom)
    .build()
