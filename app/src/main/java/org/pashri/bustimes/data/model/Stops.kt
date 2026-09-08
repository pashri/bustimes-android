package org.pashri.bustimes.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The GeoJSON `FeatureCollection` returned by `/stops.json`. */
@Serializable
data class StopCollection(
    val features: List<StopFeature> = emptyList(),
)

@Serializable
data class StopFeature(
    val geometry: PointGeometry,
    val properties: StopProperties,
) {
    val longitude: Double get() = geometry.coordinates[0]
    val latitude: Double get() = geometry.coordinates[1]

    /** ATCO code parsed from the `/stops/<atco>` url, which is the only place it appears. */
    val atcoCode: String? get() = properties.url?.removePrefix("/stops/")?.takeIf { it.isNotBlank() }
}

@Serializable
data class PointGeometry(
    val coordinates: List<Double>,
)

@Serializable
data class StopProperties(
    val name: String = "",
    val indicator: String? = null,
    val icon: String? = null,
    /** Compass direction the stop flag faces, not necessarily the direction of travel. */
    val bearing: Double? = null,
    val url: String? = null,
    val services: List<String> = emptyList(),
    @SerialName("stop_type") val stopType: String? = null,
    @SerialName("bus_stop_type") val busStopType: String? = null,
)
