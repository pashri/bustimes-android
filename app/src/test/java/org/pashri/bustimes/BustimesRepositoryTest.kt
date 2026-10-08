package org.pashri.bustimes

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.pashri.bustimes.data.model.Vehicle
import org.pashri.bustimes.data.net.BoundingBox
import org.pashri.bustimes.data.repo.BustimesRepository
import org.pashri.bustimes.data.repo.HttpBustimesRepository

class BustimesRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: BustimesRepository

    @Before
    fun startServer() {
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Redirects every request onto the mock server, keeping the
                // path and query the repository built for the real host.
                val original = chain.request()
                val redirected = original.newBuilder()
                    .url(server.url(original.url.encodedPath + "?" + original.url.encodedQuery))
                    .build()
                chain.proceed(redirected)
            }
            .build()
        repository = HttpBustimesRepository(client)
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `a transient server error is retried and then succeeds`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setBody("[]"))

        val vehicles = repository.vehiclesInBox(BOX)

        assertEquals(emptyList<Nothing>(), vehicles)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a persistently failing endpoint still fails after retries are exhausted`() {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(502)) }

        assertThrows(IOException::class.java) {
            runBlocking { repository.vehiclesInBox(BOX) }
        }
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `a selected journey's vehicle is the one running its trip, not the first listed`() =
        runBlocking {
            // Recorded live: the endpoint ignored `trip` and returned the whole
            // service, with a stale second bus also claiming the same trip.
            server.enqueue(MockResponse().setBody(fixture("vehicles_service_trip_655102840.json")))

            val vehicle = repository.vehicleForTrip(serviceId = 8021, tripId = 655102840)

            assertEquals(19280L, vehicle?.id)
            assertEquals(306.0, vehicle?.delay)
        }

    @Test
    fun `a trip no vehicle is running is not tracked`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("vehicles_service_trip_655102840.json")))

        assertNull(repository.vehicleForTrip(serviceId = 8021, tripId = 1))
    }

    @Test
    fun `when no claimant is matched by the server the freshest position wins`() {
        // Mixed offsets, so comparing the strings would pick the wrong one.
        val older = claimant(id = 1, datetime = "2026-10-08T20:14:44+01:00")
        val newer = claimant(id = 2, datetime = "2026-10-08T19:29:06Z")

        val picked = HttpBustimesRepository.vehicleRunning(listOf(older, newer), tripId = 5)

        assertEquals(2L, picked?.id)
    }

    private fun claimant(id: Long, datetime: String) =
        Vehicle(id = id, coordinates = listOf(0.1, 52.2), tripId = 5, datetime = datetime)

    private companion object {
        val BOX = BoundingBox(north = 52.3, east = 0.2, south = 52.1, west = 0.0)
    }
}
