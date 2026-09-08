package org.pashri.bustimes.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** A position accurate enough to centre a map on. */
data class CoarsePosition(val latitude: Double, val longitude: Double)

/**
 * Supplies the user's approximate position.
 *
 * Only [Manifest.permission.ACCESS_COARSE_LOCATION] is requested: one to three
 * kilometres is ample for choosing where to point a map and which bounding box
 * to fetch, and Android presents it to the user as the less alarming
 * "approximate location" option, which materially reduces denials.
 */
class CoarseLocationProvider(private val context: Context) {

    /** True when the user has granted coarse location. */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Returns the last position the system already knows, without waiting.
     *
     * This is what makes centring feel instant; a fresh fix is requested
     * afterwards and the camera eased across when it lands.
     *
     * @return the cached position, or null if unavailable or not permitted.
     */
    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): CoarsePosition? {
        if (!hasPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { continuation ->
            client.lastLocation
                .addOnSuccessListener { location ->
                    continuation.resume(location?.let { CoarsePosition(it.latitude, it.longitude) })
                }
                .addOnFailureListener { continuation.resume(null) }
        }
    }

    /**
     * Requests a single fresh position.
     *
     * @return the current position, or null if unavailable or not permitted.
     */
    @SuppressLint("MissingPermission")
    suspend fun current(): CoarsePosition? {
        if (!hasPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    continuation.resume(location?.let { CoarsePosition(it.latitude, it.longitude) })
                }
                .addOnFailureListener { continuation.resume(null) }
        }
    }
}
