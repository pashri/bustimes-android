package org.pashri.bustimes.data.prefs

import org.pashri.bustimes.ui.map.CameraState

/**
 * Remembers where the map was last looking.
 *
 * The map must render immediately on launch, because a GPS fix can take ten
 * seconds and a grey rectangle for ten seconds reads as a broken app. Opening
 * at the last known camera position gives something real to look at while a
 * location is found, which is the same trick bustimes.org plays with
 * `localStorage`.
 */
interface CameraStore {

    /**
     * Reads the persisted camera.
     *
     * @return the last camera position, or null on a first run.
     */
    suspend fun read(): CameraState?

    /**
     * Persists the camera.
     *
     * @param camera the position to remember.
     */
    suspend fun write(camera: CameraState)
}
