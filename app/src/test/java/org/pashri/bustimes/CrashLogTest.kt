package org.pashri.bustimes

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.pashri.bustimes.data.diagnostics.CrashLog

class CrashLogTest {

    private lateinit var directory: File
    private lateinit var log: CrashLog

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("crashlog").toFile()
        log = CrashLog(directory)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `nothing is recorded to begin with`() {
        assertNull(log.read())
    }

    @Test
    fun `a recorded crash can be read back with its type and message`() {
        log.record(IllegalStateException("something specific went wrong"), thread = "main")

        val text = log.read()

        assertNotNull(text)
        assertTrue(text!!.contains("IllegalStateException"))
        assertTrue(text.contains("something specific went wrong"))
        assertTrue("the thread matters for diagnosing a coroutine", text.contains("main"))
    }

    @Test
    fun `a cause is recorded too`() {
        val cause = NumberFormatException("For input string: -42.0")
        log.record(RuntimeException("decode failed", cause), thread = "DefaultDispatcher-worker-1")

        val text = log.read()!!

        assertTrue(text.contains("NumberFormatException"))
        assertTrue(text.contains("-42.0"))
    }

    @Test
    fun `crashes accumulate rather than overwrite`() {
        log.record(IllegalStateException("first"), thread = "main")
        log.record(IllegalStateException("second"), thread = "main")

        val text = log.read()!!

        assertTrue(text.contains("first"))
        assertTrue(text.contains("second"))
    }

    @Test
    fun `clearing empties the log`() {
        log.record(IllegalStateException("gone"), thread = "main")
        log.clear()

        assertNull(log.read())
    }

    @Test
    fun `the log cannot grow without bound`() {
        repeat(400) { log.record(IllegalStateException("crash number $it"), thread = "main") }

        val text = log.read()!!

        assertTrue("kept under the cap, was ${text.length}", text.length < 200_000)
        assertTrue("the newest crash survives trimming", text.contains("crash number 399"))
    }

    @Test
    fun `recording into an unwritable directory does not throw`() {
        val missing = CrashLog(File("/proc/definitely-not-writable/nested"))

        // The handler runs while the process is already dying; it must not add
        // a second exception on the way out.
        missing.record(IllegalStateException("x"), thread = "main")

        assertNull(missing.read())
    }
}
