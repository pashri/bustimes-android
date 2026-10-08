package org.pashri.bustimes.data.location

import kotlin.math.cos
import kotlin.math.hypot

/** A position on the ground. */
data class DevicePosition(val latitude: Double, val longitude: Double) {

    /**
     * Whether moving to this position from [other] is worth a camera move.
     *
     * Two fixes a few metres apart describe the same place, and easing the
     * camera between them just makes the map twitch. The threshold is roughly
     * the spacing of neighbouring bus stops, below which the same stops stay
     * on screen anyway.
     *
     * @param other the position to compare against.
     * @return true when the two are far enough apart to matter.
     */
    fun isFurtherThanAStopFrom(other: DevicePosition): Boolean {
        val northing = (latitude - other.latitude) * METRES_PER_DEGREE_LATITUDE
        val easting = (longitude - other.longitude) *
            METRES_PER_DEGREE_LATITUDE * cos(Math.toRadians(latitude))
        return hypot(easting, northing) > SIGNIFICANT_MOVE_METRES
    }

    private companion object {
        const val METRES_PER_DEGREE_LATITUDE = 110_540.0
        const val SIGNIFICANT_MOVE_METRES = 100.0
    }
}

/**
 * Supplies the user's position.
 *
 * Both reads return null rather than throwing when there is no permission or
 * no fix, since a map without the user's position is still a working map.
 */
interface LocationProvider {

    /**
     * Returns the last position the system already knows, without waiting.
     *
     * @return the cached position, or null if unavailable or not permitted.
     */
    suspend fun lastKnown(): DevicePosition?

    /**
     * Requests a single fresh position at the best accuracy available.
     *
     * @return the current position, or null if unavailable or not permitted.
     */
    suspend fun current(): DevicePosition?
}
