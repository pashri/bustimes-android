package org.pashri.bustimes.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.pashri.bustimes.ui.map.CameraState

private val Context.cameraDataStore: DataStore<Preferences> by preferencesDataStore(name = "camera")

/**
 * Remembers where the map was last looking.
 *
 * The map must render immediately on launch, because a GPS fix can take ten
 * seconds and a grey rectangle for ten seconds reads as a broken app. Opening
 * at the last known camera position gives something real to look at while a
 * location is found, which is the same trick bustimes.org plays with
 * `localStorage`.
 */
class CameraStore(private val context: Context) {

    /**
     * Reads the persisted camera.
     *
     * @return the last camera position, or null on a first run.
     */
    suspend fun read(): CameraState? {
        val preferences = context.cameraDataStore.data.first()
        val latitude = preferences[LATITUDE] ?: return null
        val longitude = preferences[LONGITUDE] ?: return null
        val zoom = preferences[ZOOM] ?: return null
        return CameraState(latitude = latitude, longitude = longitude, zoom = zoom)
    }

    /**
     * Persists the camera.
     *
     * @param camera the position to remember.
     */
    suspend fun write(camera: CameraState) {
        context.cameraDataStore.edit { preferences ->
            preferences[LATITUDE] = camera.latitude
            preferences[LONGITUDE] = camera.longitude
            preferences[ZOOM] = camera.zoom
        }
    }

    private companion object {
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val ZOOM = doublePreferencesKey("zoom")
    }
}
