package org.pashri.bustimes

/**
 * Loads a recorded response from `src/test/resources`.
 *
 * Fixtures are real bodies captured from bustimes.org. They exist so that a
 * template or export change upstream breaks a test rather than the app: both
 * the departures board and the timetable are scraped rather than consumed from
 * a stable API, and those are the project's only fragile seams.
 */
fun fixture(name: String): String =
    requireNotNull(Thread.currentThread().contextClassLoader?.getResourceAsStream(name)) {
        "missing fixture: $name"
    }.bufferedReader().use { it.readText() }
