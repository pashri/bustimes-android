package org.pashri.bustimes.ui.map

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.maplibre.android.geometry.LatLng
import org.pashri.bustimes.R
import org.pashri.bustimes.data.net.BoundingBox

/** Size and spacing of the locate button, shared with the map's compass. */
internal val FAB_SIZE = 56.dp
internal val FAB_MARGIN = 16.dp
internal val COMPASS_GAP = 8.dp
private val FAB_CORNER = 16.dp
private val FAB_ELEVATION = 6.dp

/**
 * The opening screen: a map of live buses and stops.
 *
 * Location is requested a moment after the map appears rather than before it,
 * so the user has a working map on screen when the system dialog arrives. A
 * denial is an ordinary outcome — the locate button stays available and
 * nothing nags.
 *
 * @param state everything to render.
 * @param darkTheme selects the basemap style.
 * @param bottomInset height obscured by the sheet, so the camera and the
 *   on-map controls both sit above it.
 * @param onCameraIdle forwarded to the map.
 * @param onVehicleTapped called when a bus is tapped.
 * @param onStopTapped called when a stop is tapped.
 * @param onLocateRequested called when the user asks to be located.
 * @param onPermissionGranted called when location permission is newly granted.
 * @param onCameraMoveHandled called once a requested camera move is applied.
 * @param onFitRouteHandled called once the camera has framed the route.
 * @param onResumed called when the screen becomes visible, to resume polling.
 * @param onPaused called when the screen is hidden, to stop polling.
 * @param onDiagnosticsRequested called on a long press of the locate button.
 * @param modifier layout modifier.
 */
@Composable
fun MapScreen(
    state: MapUiState,
    darkTheme: Boolean,
    bottomInset: Dp,
    onCameraIdle: (CameraState, BoundingBox, Boolean) -> Unit,
    onVehicleTapped: (Long) -> Unit,
    onStopTapped: (String) -> Unit,
    onLocateRequested: () -> Unit,
    onPermissionGranted: () -> Unit,
    onCameraMoveHandled: () -> Unit,
    onFitRouteHandled: () -> Unit,
    onResumed: () -> Unit,
    onPaused: () -> Unit,
    onDiagnosticsRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var askedForPermission by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    // Tracked apart from `granted`, because holding coarse is not holding
    // precise: an install upgraded from a coarse-only version otherwise looks
    // fully permitted and is never asked for precise at all.
    var precise by remember { mutableStateOf(hasPreciseLocation(context)) }
    var pillDismissed by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        granted = hasLocationPermission(context)
        precise = hasPreciseLocation(context)
        if (granted) onPermissionGranted()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onResumed() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onPaused() }

    // Ask when precise is missing, not merely when nothing is granted.
    LaunchedEffect(state.camera != null) {
        if (state.camera != null && !askedForPermission && !precise) {
            askedForPermission = true
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val camera = state.camera
        if (camera != null) {
            BustimesMap(
                decorations = state.decorations,
                initialCamera = camera,
                darkTheme = darkTheme,
                locationEnabled = granted,
                bottomInset = bottomInset,
                onCameraIdle = onCameraIdle,
                onVehicleTapped = onVehicleTapped,
                onStopTapped = onStopTapped,
                moveTo = state.moveCameraTo?.let { LatLng(it.latitude, it.longitude) },
                onMoveHandled = onCameraMoveHandled,
                fitRoute = state.fitRoute,
                onFitRouteHandled = onFitRouteHandled,
                modifier = Modifier.fillMaxSize(),
            )
        }
        MapOverlays(
            state = state,
            bottomInset = bottomInset,
            // Asking again here is not a nag: locating is the one action that
            // needs precise location, so it is the natural place to request it.
            onLocateRequested = {
                if (!precise) permissionLauncher.launch(LOCATION_PERMISSIONS)
                if (granted) onLocateRequested()
            },
            onDiagnosticsRequested = onDiagnosticsRequested,
            // Android stops showing the dialog after enough refusals, at which
            // point a request does nothing at all, silently. Offering Settings
            // is the difference between a vague fix and a vague fix you can fix.
            showPreciseHint = granted && !precise && !pillDismissed &&
                !shouldShowRequestPermissionRationale(context),
            onPreciseHintClicked = { openAppSettings(context) },
            onPreciseHintDismissed = { pillDismissed = true },
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        )
    }
}

/** Hints, progress and the locate button drawn over the map. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MapOverlays(
    state: MapUiState,
    bottomInset: Dp,
    onLocateRequested: () -> Unit,
    onDiagnosticsRequested: () -> Unit,
    showPreciseHint: Boolean,
    onPreciseHintClicked: () -> Unit,
    onPreciseHintDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.offline) {
                MapPill(text = stringResource(R.string.offline_banner))
            }
            if (state.zoomedOutForVehicles) {
                MapPill(text = stringResource(R.string.zoom_in_for_buses))
            }
            if (showPreciseHint) {
                MapPill(
                    text = stringResource(R.string.approximate_location_hint),
                    modifier = Modifier.combinedClickable(
                        onClick = onPreciseHintClicked,
                        onLongClick = onPreciseHintDismissed,
                    ),
                )
            }
        }
        if (state.loadingVehicles) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            )
        }
        LocateButton(
            onClick = onLocateRequested,
            onLongClick = onDiagnosticsRequested,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = FAB_MARGIN, bottom = FAB_MARGIN + bottomInset),
        )
    }
}

/**
 * The locate button.
 *
 * Built from a Surface rather than a FloatingActionButton because that has no
 * long-press support: its own internal clickable sits inside any modifier
 * added from outside and swallows the gesture, so the diagnostics screen was
 * unreachable. A long press here opens it — deliberately obscure, since it
 * exists for the rare occasion something went wrong.
 *
 * @param onClick called on a tap, to centre on the user.
 * @param onLongClick called on a long press, to open diagnostics.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocateButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(FAB_CORNER),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = FAB_ELEVATION,
        modifier = modifier
            .size(FAB_SIZE)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = stringResource(R.string.locate),
                onLongClickLabel = stringResource(R.string.diagnostics),
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = stringResource(R.string.locate),
            )
        }
    }
}

/** A rounded label legible over any basemap. */
@Composable
private fun MapPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * Both location permissions, requested together.
 *
 * Fine location is what the app wants — approximate can be kilometres out,
 * which is useless for finding your own stop. Coarse is requested alongside
 * it because Android's dialog offers the user a choice between them, and the
 * app still works, less precisely, if they pick approximate.
 */
private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun hasLocationPermission(context: Context): Boolean =
    LOCATION_PERMISSIONS.any { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

/** Whether the user granted precise rather than approximate location. */
private fun hasPreciseLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Whether Android would still show a permission dialog if asked.
 *
 * False once the user has refused enough times that the system answers on
 * their behalf, in which case launching a request does nothing at all.
 */
private fun shouldShowRequestPermissionRationale(context: Context): Boolean {
    val activity = context as? Activity ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
}

/** Opens this app's page in system settings, where precise can be enabled. */
private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
