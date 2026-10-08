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

/** A [CameraStore] backed by a preferences DataStore. */
class DataStoreCameraStore(private val context: Context) : CameraStore {

    /**
     * Reads the persisted camera.
     *
     * @return the last camera position, or null on a first run.
     */
    override suspend fun read(): CameraState? {
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
    override suspend fun write(camera: CameraState) {
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
