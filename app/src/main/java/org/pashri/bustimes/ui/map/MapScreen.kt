package org.pashri.bustimes.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.maplibre.android.geometry.LatLng
import org.pashri.bustimes.R

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
 * @param onCameraIdle forwarded to the map.
 * @param onVehicleTapped called when a bus is tapped.
 * @param onStopTapped called when a stop is tapped.
 * @param onLocateRequested called when the user asks to be located.
 * @param onPermissionGranted called when location permission is newly granted.
 * @param onCameraMoveHandled called once a requested camera move is applied.
 * @param onResumed called when the screen becomes visible, to resume polling.
 * @param onPaused called when the screen is hidden, to stop polling.
 * @param modifier layout modifier.
 */
@Composable
fun MapScreen(
    state: MapUiState,
    darkTheme: Boolean,
    onCameraIdle: (CameraState, org.pashri.bustimes.data.net.BoundingBox, Boolean) -> Unit,
    onVehicleTapped: (Long) -> Unit,
    onStopTapped: (String) -> Unit,
    onLocateRequested: () -> Unit,
    onPermissionGranted: () -> Unit,
    onCameraMoveHandled: () -> Unit,
    onResumed: () -> Unit,
    onPaused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var askedForPermission by remember { mutableStateOf(false) }
    val alreadyGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onPermissionGranted() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onResumed() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onPaused() }

    // Ask a beat after the map is on screen, and only when there is something
    // to ask for. Re-requesting an already-granted permission returns granted
    // immediately, which previously re-centred the map on every launch and
    // discarded the position the user had left it at.
    LaunchedEffect(state.camera != null) {
        if (state.camera != null && !askedForPermission && !alreadyGranted) {
            askedForPermission = true
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val camera = state.camera
        if (camera != null) {
            BustimesMap(
                decorations = state.decorations,
                initialCamera = camera,
                darkTheme = darkTheme,
                onCameraIdle = onCameraIdle,
                onVehicleTapped = onVehicleTapped,
                onStopTapped = onStopTapped,
                moveTo = state.moveCameraTo?.let { LatLng(it.latitude, it.longitude) },
                onMoveHandled = onCameraMoveHandled,
                modifier = Modifier.fillMaxSize(),
            )
        }
        MapOverlays(
            state = state,
            onLocateRequested = {
                if (alreadyGranted) {
                    onLocateRequested()
                } else {
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        )
    }
}

/** Hints, progress and the locate button drawn over the map. */
@Composable
private fun MapOverlays(
    state: MapUiState,
    onLocateRequested: () -> Unit,
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
        }
        if (state.loadingVehicles) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            )
        }
        FloatingActionButton(
            onClick = onLocateRequested,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
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
