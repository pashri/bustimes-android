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
     * Builds the direction-of-travel arrowhead.
     *
     * Drawn pointing up, offset from the centre of a deliberately oversized
     * bitmap. MapLibre rotates a symbol about its anchor, and the anchor is
     * the bitmap's centre, so putting the arrow above the centre and leaving
     * the middle empty makes it orbit the bus at a fixed radius rather than
     * hanging off one side of it. The empty middle is where the coloured body
     * circle shows through.
     *
     * @param density the display density, so the icon is crisp on any screen.
     * @return the arrowhead bitmap.
     */
    fun heading(density: Float): Bitmap {
        val size = (BITMAP_SIZE_DP * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centre = size / 2f
        val tip = centre - (ORBIT_DP + ARROW_HEIGHT_DP) * density
        val base = centre - ORBIT_DP * density
        val halfWidth = ARROW_HALF_WIDTH_DP * density

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
                strokeWidth = 2f * density
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
            color = if (onRoute) Color.rgb(27, 94, 32) else Color.rgb(90, 90, 90)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centre, centre, centre - RING_DP * density, fill)
        return bitmap
    }

    /** Distance from the bus position to the arrow's base. */
    private const val ORBIT_DP = 13f
    private const val ARROW_HEIGHT_DP = 9f
    private const val ARROW_HALF_WIDTH_DP = 6f

    /** Twice the furthest the arrow reaches, so rotation never clips it. */
    private const val BITMAP_SIZE_DP = 2f * (ORBIT_DP + ARROW_HEIGHT_DP)
    private const val STOP_SIZE_DP = 12f
    private const val STOP_ROUTE_SIZE_DP = 16f
    private const val RING_DP = 2f
}
