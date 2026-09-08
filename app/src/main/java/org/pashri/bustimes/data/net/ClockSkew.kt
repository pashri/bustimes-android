package org.pashri.bustimes.data.net

import java.util.concurrent.atomic.AtomicLong
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Tracks the offset between this device's clock and bustimes.org's.
 *
 * A phone with a wrong clock would otherwise compute nonsense delays: a bus
 * reported at 10:13 looks 5 minutes early on a device running 5 minutes fast.
 * bustimes.org's own frontend does exactly this (`clockSkew.ts`), reading the
 * `Date` response header and adding the difference to every comparison.
 *
 * Only positive skew is recorded, matching upstream: the server's clock is
 * treated as authoritative when it is ahead, and network latency means a
 * server `Date` slightly behind ours is expected rather than wrong.
 */
object ClockSkew {

    private val offsetMillis = AtomicLong(0)

    /** Records skew from a response's `Date` header. Ignores absent or unparseable values. */
    fun record(response: Response) {
        val serverDate = response.headers.getDate("Date")?.time ?: return
        val local = System.currentTimeMillis()
        if (serverDate > local) {
            offsetMillis.set(serverDate - local)
        }
    }

    /** Current time in epoch millis, corrected towards the server's clock. */
    fun now(): Long = System.currentTimeMillis() + offsetMillis.get()

    /** The recorded offset, exposed for diagnostics and tests. */
    fun offset(): Long = offsetMillis.get()

    internal fun reset() = offsetMillis.set(0)
}

/** Feeds every response's `Date` header into [ClockSkew]. */
class ClockSkewInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        ClockSkew.record(response)
        return response
    }
}
