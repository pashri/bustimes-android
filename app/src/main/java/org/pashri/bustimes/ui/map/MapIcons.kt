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

    /** Style image id for an ordinary stop. */
    const val STOP = "stop-marker"

    /** Style image id for a stop on the selected route. */
    const val STOP_ROUTE = "stop-marker-route"

    /**
     * Builds the direction-of-travel wedge.
     *
     * Drawn pointing up in a square bitmap so MapLibre's `icon-rotate` aims it
     * along the vehicle's heading, and sized larger than the bus body so the
     * tip protrudes past the circle drawn over it.
     *
     * @param density the display density, so the icon is crisp on any screen.
     * @return the wedge bitmap.
     */
    fun heading(density: Float): Bitmap {
        val size = (WEDGE_SIZE_DP * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centre = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 40, 40, 40)
            style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(centre, 0f)
            lineTo(centre - size * WEDGE_HALF_WIDTH, size * WEDGE_HEIGHT)
            lineTo(centre + size * WEDGE_HALF_WIDTH, size * WEDGE_HEIGHT)
            close()
        }
        canvas.drawPath(path, paint)
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
            color = if (onRoute) Color.rgb(27, 94, 32) else Color.rgb(90, 90, 90)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centre, centre, centre - RING_DP * density, fill)
        return bitmap
    }

    private const val WEDGE_SIZE_DP = 34f
    private const val WEDGE_HALF_WIDTH = 0.16f
    private const val WEDGE_HEIGHT = 0.40f
    private const val STOP_SIZE_DP = 12f
    private const val STOP_ROUTE_SIZE_DP = 16f
    private const val RING_DP = 2f
}
