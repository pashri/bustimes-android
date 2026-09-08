package org.pashri.bustimes.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
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
 * @param onCameraIdle called when the camera settles, with the new viewport.
 * @param onVehicleTapped called with a tapped vehicle's id.
 * @param onStopTapped called with a tapped stop's ATCO code.
 * @param moveTo when non-null, the camera eases to this position.
 * @param onMoveHandled called once a [moveTo] has been applied.
 * @param modifier layout modifier.
 */
@Composable
fun BustimesMap(
    decorations: MapDecorations,
    initialCamera: CameraState,
    darkTheme: Boolean,
    onCameraIdle: (CameraState, BoundingBox, Boolean) -> Unit,
    onVehicleTapped: (Long) -> Unit,
    onStopTapped: (String) -> Unit,
    moveTo: LatLng?,
    onMoveHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { MapController(density) }

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
            controller.render(decorations)
            if (moveTo != null && controller.easeTo(moveTo)) {
                onMoveHandled()
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
    private var currentStyleUrl: String? = null
    private var pendingDecorations: MapDecorations? = null
    private var pendingTarget: LatLng? = null
    private var onVehicleTapped: ((Long) -> Unit)? = null
    private var onStopTapped: ((String) -> Unit)? = null

    fun attach(
        view: MapView,
        initialCamera: CameraState,
        darkTheme: Boolean,
        onCameraIdle: (CameraState, BoundingBox, Boolean) -> Unit,
        onVehicleTapped: (Long) -> Unit,
        onStopTapped: (String) -> Unit,
    ) {
        this.view = view
        this.onVehicleTapped = onVehicleTapped
        this.onStopTapped = onStopTapped
        view.onCreate(null)
        view.getMapAsync { ready ->
            map = ready
            ready.cameraPosition = initialCamera.toCameraPosition()
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
            Lifecycle.Event.ON_DESTROY -> view.onDestroy()
            else -> Unit
        }
    }

    fun setStyleForTheme(darkTheme: Boolean) {
        val wanted = MapStyles.forTheme(darkTheme)
        val map = map ?: return
        if (wanted != currentStyleUrl) {
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
        source(style, MapLayers.SOURCE_VEHICLES)
            ?.setGeoJson(MapGeoJson.vehicles(decorations.vehicles, decorations.selectedVehicleId))
        source(style, MapLayers.SOURCE_STOPS)
            ?.setGeoJson(MapGeoJson.stops(decorations.stops, decorations.selectedStopAtco))
        source(style, MapLayers.SOURCE_ROUTE)
            ?.setGeoJson(MapGeoJson.routeLines(decorations.routeLegs))
        source(style, MapLayers.SOURCE_ROUTE_STOPS)
            ?.setGeoJson(MapGeoJson.routeStops(decorations.routeStops))
        applyRouteDimming(style, decorations.routeDimmed)
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
        style.addLayer(MapLayers.routeCasing())
        style.addLayer(MapLayers.route())
        style.addLayer(MapLayers.stops())
        style.addLayer(MapLayers.routeStops())
        style.addLayer(MapLayers.vehicleHeadings())
        style.addLayer(MapLayers.vehicles())
        style.addLayer(MapLayers.vehicleLabels())
    }

    /**
     * Dims the route rather than removing it when a stop on it is selected, so
     * the reason you were looking at that stop stays on screen.
     */
    private fun applyRouteDimming(style: Style, dimmed: Boolean) {
        val opacity = if (dimmed) DIMMED_OPACITY else 1.0f
        style.getLayer(MapLayers.LAYER_ROUTE)?.setProperties(
            org.maplibre.android.style.layers.PropertyFactory.lineOpacity(opacity),
        )
        style.getLayer(MapLayers.LAYER_ROUTE_CASING)?.setProperties(
            org.maplibre.android.style.layers.PropertyFactory.lineOpacity(opacity * CASING_FACTOR),
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
    }
}

/** Converts app camera state into MapLibre's own representation. */
private fun CameraState.toCameraPosition(): CameraPosition = CameraPosition.Builder()
    .target(LatLng(latitude, longitude))
    .zoom(zoom)
    .build()
