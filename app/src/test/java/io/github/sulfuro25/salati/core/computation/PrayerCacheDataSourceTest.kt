package io.github.sulfuro25.salati.core.computation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class PrayerCacheDataSourceTest {
    private val july = PrayerMonthRequest(2026, 7, 3, 0, "1", 50.8503, 4.3517)

    @Test
    fun cacheFilenameFormatIsUnchangedAndMonthIdentitiesAreDistinct() {
        assertEquals(
            "prayers_v2_lat50p850_lon4p352_2026_7_m3_s0_h1.json",
            cacheFileName(july)
        )
        val december = PrayerMonthRequest(2026, 12, 3, 0, "1", 50.8503, 4.3517)
        val january = PrayerMonthRequest(2027, 1, 3, 0, "1", 50.8503, 4.3517)
        assertNotEquals(cacheFileName(december), cacheFileName(january))
        assertNotEquals(december, january)
    }

    @Test
    fun existingPlainCacheFileRemainsReadable() {
        val directory = temporaryDirectory()
        val source = AtomicFilePrayerCacheDataSource(directory)
        source.fileFor(july).writeText(validJson(), Charsets.UTF_8)

        assertEquals(PrayerCacheReadResult.Success(validJson()), source.read(july))
    }

    @Test
    fun failedAtomicReplacementPreservesPreviousValidCache() {
        val handle = StatefulAtomicFileHandle(validJson(), failOnFinish = true)
        val source = sourceWith(handle)

        val write = source.write(july, "replacement")

        assertTrue(write is PrayerCacheWriteResult.Failure)
        assertEquals(1, handle.failWriteCalls)
        assertEquals(PrayerCacheReadResult.Success(validJson()), source.read(july))
    }

    @Test
    fun simulatedPartialWriteInvokesFailWriteAndDoesNotExposeTruncatedData() {
        val handle = StatefulAtomicFileHandle(validJson(), failPartwayThroughWrite = true)
        val source = sourceWith(handle)

        val write = source.write(july, "replacement that must remain hidden")

        assertTrue(write is PrayerCacheWriteResult.Failure)
        assertEquals(1, handle.failWriteCalls)
        assertEquals(PrayerCacheReadResult.Success(validJson()), source.read(july))
    }

    @Test
    fun concurrentWritesToSamePathLeaveOneCompletePayload() {
        val directory = temporaryDirectory()
        val source = AtomicFilePrayerCacheDataSource(directory)
        val payloads = (1..20).map { index -> validJson().replace("\"OK\"", "\"OK-$index\"") }
        val executor = Executors.newFixedThreadPool(6)
        try {
            val futures = payloads.map { payload ->
                executor.submit<PrayerCacheWriteResult> { source.write(july, payload) }
            }
            futures.forEach { assertEquals(PrayerCacheWriteResult.Success, it.get(5, TimeUnit.SECONDS)) }

            val final = source.read(july) as PrayerCacheReadResult.Success
            assertTrue(payloads.contains(final.rawJson))
            assertTrue(AladhanPrayerResponseParser.parse(final.rawJson) is PrayerResponseParseResult.Success)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun differentMonthPathsCanOperateIndependently() {
        val directory = temporaryDirectory()
        val januaryStarted = CountDownLatch(1)
        val releaseJanuary = CountDownLatch(1)
        val handles = mutableMapOf<String, StatefulAtomicFileHandle>()
        val factory = AtomicFileHandleFactory { file ->
            synchronized(handles) {
                handles.getOrPut(file.absolutePath) {
                    StatefulAtomicFileHandle(
                        initialContent = null,
                        startSignal = if (file.name.contains("_1_")) januaryStarted else null,
                        startRelease = if (file.name.contains("_1_")) releaseJanuary else null
                    )
                }
            }
        }
        val source = AtomicFilePrayerCacheDataSource(directory, factory)
        val january = PrayerMonthRequest(2027, 1, 3, 0, "1", 50.8503, 4.3517)
        val february = PrayerMonthRequest(2027, 2, 3, 0, "1", 50.8503, 4.3517)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val januaryWrite = executor.submit<PrayerCacheWriteResult> {
                source.write(january, "january")
            }
            assertTrue(januaryStarted.await(2, TimeUnit.SECONDS))

            val februaryWrite = executor.submit<PrayerCacheWriteResult> {
                source.write(february, "february")
            }
            assertEquals(
                PrayerCacheWriteResult.Success,
                februaryWrite.get(2, TimeUnit.SECONDS)
            )

            releaseJanuary.countDown()
            assertEquals(
                PrayerCacheWriteResult.Success,
                januaryWrite.get(2, TimeUnit.SECONDS)
            )
        } finally {
            releaseJanuary.countDown()
            executor.shutdownNow()
        }
    }

    private fun sourceWith(handle: AtomicFileHandle): AtomicFilePrayerCacheDataSource {
        return AtomicFilePrayerCacheDataSource(
            temporaryDirectory(),
            AtomicFileHandleFactory { handle }
        )
    }

    private fun temporaryDirectory(): File = Files.createTempDirectory("salati-cache-test").toFile()

    private class StatefulAtomicFileHandle(
        initialContent: String?,
        private val failOnFinish: Boolean = false,
        private val failPartwayThroughWrite: Boolean = false,
        private val startSignal: CountDownLatch? = null,
        private val startRelease: CountDownLatch? = null
    ) : AtomicFileHandle {
        private var content: String? = initialContent
        private val scratch = Files.createTempFile("salati-atomic", ".tmp").toFile()
        var failWriteCalls = 0

        override fun exists(): Boolean = content != null

        override fun readUtf8(): String = content ?: throw IOException("missing")

        override fun startWrite(): FileOutputStream {
            startSignal?.countDown()
            if (startRelease != null && !startRelease.await(5, TimeUnit.SECONDS)) {
                throw IOException("timed out waiting to continue write")
            }
            return if (failPartwayThroughWrite) {
                object : FileOutputStream(scratch, false) {
                    override fun write(bytes: ByteArray) {
                        super.write(bytes, 0, bytes.size.coerceAtMost(4))
                        throw IOException("simulated interrupted write")
                    }
                }
            } else {
                FileOutputStream(scratch, false)
            }
        }

        override fun finishWrite(stream: FileOutputStream) {
            stream.close()
            if (failOnFinish) throw IOException("simulated finish failure")
            content = scratch.readText(Charsets.UTF_8)
        }

        override fun failWrite(stream: FileOutputStream) {
            failWriteCalls++
            stream.close()
        }

        override fun delete(): Boolean {
            content = null
            return true
        }
    }
}
