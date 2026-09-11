package org.pashri.bustimes

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pashri.bustimes.data.model.Vehicle
import org.pashri.bustimes.data.model.VehicleJourneySummary
import org.pashri.bustimes.data.repo.BustimesRepository

class VehicleDecodingTest {

    private val json: Json = BustimesRepository.defaultJson

    @Test
    fun `decodes a fractional delay`() {
        // bustimes computes delay, and some operators' feeds render it as a
        // float. Declaring it as an integer threw while decoding, so selecting
        // a bus from one of those operators crashed the app while a bus from
        // another was fine — which read as a fault with particular routes.
        val vehicles = json.decodeFromString<List<Vehicle>>(
            fixture("vehicles_float_delay.json"),
        )

        assertTrue(vehicles.isNotEmpty())
        val delayed = vehicles.first { it.delay != null }
        assertNotNull(delayed.delay)
    }

    @Test
    fun `a float delay survives as seconds`() {
        val decoded = json.decodeFromString<Vehicle>(
            """{"id":1,"coordinates":[0.1,52.2],"delay":-42.0}""",
        )

        assertEquals(-42.0, decoded.delay!!, 0.001)
    }

    @Test
    fun `an integer delay still decodes`() {
        val decoded = json.decodeFromString<Vehicle>(
            """{"id":1,"coordinates":[0.1,52.2],"delay":90}""",
        )

        assertEquals(90.0, decoded.delay!!, 0.001)
    }

    @Test
    fun `a vehicle with no delay decodes`() {
        val decoded = json.decodeFromString<Vehicle>(
            """{"id":1,"coordinates":[0.1,52.2]}""",
        )

        assertEquals(null, decoded.delay)
        assertEquals(0.1, decoded.longitude, 0.0001)
        assertEquals(52.2, decoded.latitude, 0.0001)
    }

    @Test
    fun `unknown fields are ignored so a new upstream field cannot crash the app`() {
        val decoded = json.decodeFromString<Vehicle>(
            """{"id":1,"coordinates":[0.1,52.2],"something_new":{"a":1}}""",
        )

        assertEquals(1L, decoded.id)
    }

    /** A stand-in for the old model, so the hazard is documented on its own. */
    @Serializable
    private data class IntDelayVehicle(val id: Long, val delay: Int? = null)

    @Test
    fun `an integer-typed delay cannot decode what bustimes sends`() {
        // Kept independent of Vehicle's own declaration so the reason the
        // field must not be an integer survives someone changing it back.
        val body = """{"id":1,"delay":-42.0}"""

        val failure = runCatching { json.decodeFromString<IntDelayVehicle>(body) }

        assertTrue(
            "decoding a float into an Int must fail, or the crash was not what we thought",
            failure.isFailure,
        )
    }

    @Test
    fun `the bbox form carries a timestamp for every vehicle`() {
        // Vehicle's KDoc says the bounding-box form omits delay and progress.
        // It says nothing about datetime, and the staleness shown on the map
        // depends entirely on it being there, so it is asserted against a
        // real recorded response rather than assumed.
        val vehicles = json.decodeFromString<List<Vehicle>>(fixture("vehicles_bbox.json"))

        assertEquals(95, vehicles.size)
        assertTrue(vehicles.all { !it.datetime.isNullOrBlank() })
        assertTrue(vehicles.none { it.delay != null })
        assertTrue(vehicles.none { it.progress != null })
    }

    @Test
    fun `a service slug is read out of its url`() {
        val decoded = json.decodeFromString<Vehicle>(
            """{"id":1,"coordinates":[0.1,52.2],
               "service":{"url":"/services/17-luton-wigmore","line_name":"17"}}""",
        )

        assertEquals("17-luton-wigmore", decoded.service?.slug)
    }

    @Test
    fun `a vehicle journey decodes as a bare object, not a page`() {
        // /api/vehiclejourneys/{id}/ returns the journey itself, unlike the
        // list endpoint's {"results": [...]} shape. Decoding it as a page
        // would silently read nothing and the id filter bug it replaces is
        // exactly the kind of thing that comes back if this regresses.
        val decoded = json.decodeFromString<VehicleJourneySummary>(
            """{"id":934824969,"trip_id":652479788,
               "route_name":"m2","destination":"Poole"}""",
        )

        assertEquals(934824969L, decoded.id)
        assertEquals(652479788L, decoded.tripId)
    }
}
