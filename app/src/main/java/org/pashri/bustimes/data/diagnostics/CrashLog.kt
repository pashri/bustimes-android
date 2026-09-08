package org.pashri.bustimes.data.diagnostics

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Records crashes to a file so a rare one can be read after the fact.
 *
 * Some faults only appear against particular operators' live data, which
 * cannot be reproduced on demand from a development machine: by the time the
 * bus has been found it has moved, and the payload that broke is gone. Keeping
 * the stack trace on the device turns "it crashed on a 17" into an exception
 * and a line number.
 *
 * Deliberately local. A crash reporting service would answer faster, but it
 * would also send a single user's movements to a third party.
 */
class CrashLog(private val directory: File) {

    private val file: File get() = File(directory, FILE_NAME)

    /**
     * Appends a crash to the log, newest last.
     *
     * @param error the exception that went uncaught.
     * @param thread the name of the thread it escaped from.
     */
    fun record(error: Throwable, thread: String) {
        runCatching {
            directory.mkdirs()
            trimIfLarge()
            file.appendText(format(error, thread))
        }
    }

    /**
     * Reads the log.
     *
     * @return everything recorded, or null when nothing has been.
     */
    fun read(): String? = runCatching {
        file.takeIf { it.exists() && it.length() > 0 }?.readText()
    }.getOrNull()

    /** Empties the log. */
    fun clear() {
        runCatching { file.delete() }
    }

    private fun format(error: Throwable, thread: String): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        return buildString {
            append("=== ").append(Instant.now()).append(" on ").append(thread).append(" ===\n")
            append(trace.toString())
            append('\n')
        }
    }

    /** Keeps the newest entries only, so the file cannot grow without bound. */
    private fun trimIfLarge() {
        val existing = file
        if (existing.exists() && existing.length() > MAX_BYTES) {
            val kept = existing.readText().takeLast(KEEP_BYTES)
            existing.writeText(kept.substringAfter("=== ", kept))
        }
    }

    private companion object {
        const val FILE_NAME = "crashes.txt"
        const val MAX_BYTES = 128L * 1024
        const val KEEP_BYTES = 48 * 1024
    }
}

/**
 * Installs [log] as the handler for exceptions nothing else caught.
 *
 * The previous handler is still invoked afterwards, so the process still dies
 * and Android still reports the crash as usual — this only makes sure the
 * trace survives somewhere readable first.
 *
 * @param log where to record crashes.
 */
fun installCrashLogging(log: CrashLog) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        log.record(error = error, thread = thread.name)
        previous?.uncaughtException(thread, error)
    }
}
