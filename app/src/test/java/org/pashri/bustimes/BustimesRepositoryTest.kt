package org.pashri.bustimes

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.pashri.bustimes.data.net.BoundingBox
import org.pashri.bustimes.data.repo.BustimesRepository

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
        repository = BustimesRepository(client)
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

    private companion object {
        val BOX = BoundingBox(north = 52.3, east = 0.2, south = 52.1, west = 0.0)
    }
}
