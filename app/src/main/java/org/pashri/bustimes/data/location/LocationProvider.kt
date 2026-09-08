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

/** A position on the ground. */
data class DevicePosition(val latitude: Double, val longitude: Double)

/**
 * Supplies the user's position.
 *
 * Fine location is requested because approximate can be one to three
 * kilometres out, which is useless for finding the stop you are standing at.
 * Android's dialog still lets the user hand over approximate instead, so
 * coarse is accepted as a fallback and the app simply works less precisely.
 *
 * There is no background location: polling stops when the app leaves the
 * foreground, so there is nothing to locate in the background.
 */
class LocationProvider(private val context: Context) {

    /** True when either location permission has been granted. */
    fun hasPermission(): Boolean = PERMISSIONS.any { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** True when the user granted precise rather than approximate location. */
    fun hasPrecisePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
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
    suspend fun lastKnown(): DevicePosition? {
        if (!hasPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { continuation ->
            client.lastLocation
                .addOnSuccessListener { location ->
                    continuation.resume(location?.let { DevicePosition(it.latitude, it.longitude) })
                }
                .addOnFailureListener { continuation.resume(null) }
        }
    }

    /**
     * Requests a single fresh position at the best accuracy available.
     *
     * @return the current position, or null if unavailable or not permitted.
     */
    @SuppressLint("MissingPermission")
    suspend fun current(): DevicePosition? {
        if (!hasPermission()) return null
        val priority = if (hasPrecisePermission()) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(priority, null)
                .addOnSuccessListener { location ->
                    continuation.resume(location?.let { DevicePosition(it.latitude, it.longitude) })
                }
                .addOnFailureListener { continuation.resume(null) }
        }
    }

    private companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
