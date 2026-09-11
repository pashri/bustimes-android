package org.pashri.bustimes.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Draws the map's marker bitmaps in code.
 *
 * Symbol layers need bitmaps rather than vectors, and generating them avoids
 * shipping a set of density-specific PNGs for what are simple shapes.
 */
object MapIcons {

    /** Style image id for the direction-of-travel wedge. */
    const val HEADING = "vehicle-heading"

    /** Style image id for a stop's flag-facing wedge. */
    const val STOP_HEADING = "stop-heading"

    /** Style image id for an ordinary stop. */
    const val STOP = "stop-marker"

    /** Style image id for a stop on the selected route. */
    const val STOP_ROUTE = "stop-marker-route"

    /**
     * Builds a direction wedge: a bus's direction of travel, or a stop's
     * flag-facing direction.
     *
     * Drawn pointing up, offset from the centre of a deliberately oversized
     * bitmap. MapLibre rotates a symbol about its anchor, and the anchor is
     * the bitmap's centre, so putting the arrow above the centre and leaving
     * the middle empty makes it orbit the marker at a fixed radius rather
     * than hanging off one side of it. The empty middle is where the
     * coloured body circle shows through.
     *
     * A stop's wedge is generated at its own, smaller size rather than by
     * scaling the bus one down: a 44dp bitmap downscaled to a stop's 13dp
     * blurs, and the 2dp white outline that keeps the wedge legible over the
     * dark basemap would shrink to 0.6dp and vanish.
     *
     * @param density the display density, so the icon is crisp on any screen.
     * @param forStop draws the smaller stop wedge instead of the bus one.
     * @return the arrowhead bitmap.
     */
    fun heading(density: Float, forStop: Boolean = false): Bitmap {
        val orbitDp = if (forStop) STOP_ORBIT_DP else ORBIT_DP
        val arrowHeightDp = if (forStop) STOP_ARROW_HEIGHT_DP else ARROW_HEIGHT_DP
        val halfWidthDp = if (forStop) STOP_ARROW_HALF_WIDTH_DP else ARROW_HALF_WIDTH_DP
        val strokeDp = if (forStop) STOP_HEADING_STROKE_DP else HEADING_STROKE_DP
        // Twice the furthest the arrow reaches, so rotation never clips it.
        val size = (2f * (orbitDp + arrowHeightDp) * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centre = size / 2f
        val tip = centre - (orbitDp + arrowHeightDp) * density
        val base = centre - orbitDp * density
        val halfWidth = halfWidthDp * density

        val path = Path().apply {
            moveTo(centre, tip)
            lineTo(centre - halfWidth, base)
            lineTo(centre + halfWidth, base)
            close()
        }
        // A white outline keeps the arrow legible over dark buildings and
        // water as well as over the pale basemap.
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = strokeDp * density
                strokeJoin = Paint.Join.ROUND
            },
        )
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(235, 34, 34, 34)
                style = Paint.Style.FILL
            },
        )
        return bitmap
    }

    /**
     * Builds a stop marker: a small filled circle with a white ring.
     *
     * @param density the display density.
     * @param onRoute whether this stop belongs to the selected route, which is
     *   drawn larger so calling points stand out from surrounding stops.
     * @return the marker bitmap.
     */
    fun stop(density: Float, onRoute: Boolean): Bitmap {
        val size = ((if (onRoute) STOP_ROUTE_SIZE_DP else STOP_SIZE_DP) * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centre = size / 2f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centre, centre, centre, ring)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (onRoute) Color.rgb(184, 134, 11) else Color.rgb(90, 90, 90)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centre, centre, centre - RING_DP * density, fill)
        return bitmap
    }

    /** Distance from the bus position to the arrow's base. */
    private const val ORBIT_DP = 13f
    private const val ARROW_HEIGHT_DP = 9f
    private const val ARROW_HALF_WIDTH_DP = 6f
    private const val HEADING_STROKE_DP = 2f

    /**
     * The stop wedge's own, smaller geometry.
     *
     * Orbit derived from the vehicle wedge's own orbit:radius ratio (~1.15)
     * applied to the stop body's [MapLayers] `STOP_MIN_RADIUS` of 3.5.
     */
    private const val STOP_ORBIT_DP = 4f
    private const val STOP_ARROW_HEIGHT_DP = 4f
    private const val STOP_ARROW_HALF_WIDTH_DP = 2.6f
    private const val STOP_HEADING_STROKE_DP = 1f

    private const val STOP_SIZE_DP = 12f
    private const val STOP_ROUTE_SIZE_DP = 16f
    private const val RING_DP = 2f
}
