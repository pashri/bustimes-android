package org.pashri.bustimes.ui.map

/**
 * Basemap styles.
 *
 * OpenFreeMap serves the full MapLibre style spec with no API key, no signup
 * and no referrer check, from OpenStreetMap data — the same data bustimes.org
 * renders. Positron is a deliberately pale base so coloured route lines and
 * bus markers stay legible on top of it.
 */
object MapStyles {

    /** Pale grey base for light mode. */
    const val POSITRON = "https://tiles.openfreemap.org/styles/positron"

    /** Dark counterpart, used when the system is in dark mode. */
    const val DARK = "https://tiles.openfreemap.org/styles/dark"

    /**
     * Returns the style URL for the current theme.
     *
     * @param darkTheme whether the system is in dark mode.
     * @return the style URL to load.
     */
    fun forTheme(darkTheme: Boolean): String = if (darkTheme) DARK else POSITRON
}
