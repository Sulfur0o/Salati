package io.github.sulfuro25.salati.core.computation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class PrayerCacheLifecycleAuditTest {
    private val request = PrayerMonthRequest(2026, 7, 3, 0, "1", 50.8503, 4.3517)

    @Test
    fun successfulReplacementFinishesExactlyOnceWithoutRollback() {
        val directory = temporaryDirectory()
        try {
            val handle = AuditAtomicFileHandle(directory, initialContent = "previous")
            val source = AtomicFilePrayerCacheDataSource(
                directory,
                AtomicFileHandleFactory { handle }
            )

            assertEquals(PrayerCacheWriteResult.Success, source.write(request, "replacement"))
            assertEquals(1, handle.startCalls)
            assertEquals(1, handle.finishCalls)
            assertEquals(0, handle.failCalls)
            assertEquals(PrayerCacheReadResult.Success("replacement"), source.read(request))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun recoverableLegacyBackupIsRestoredBeforeInvalidation() {
        val directory = temporaryDirectory()
        try {
            val source = AtomicFilePrayerCacheDataSource(directory)
            val baseFile = source.fileFor(request)
            val backupFile = File(baseFile.path + ".bak")
            backupFile.writeText(validJson(), Charsets.UTF_8)

            assertFalse(baseFile.exists())
            assertEquals(PrayerCacheReadResult.Success(validJson()), source.read(request))
            assertTrue(baseFile.exists())
            assertFalse(backupFile.exists())

            assertEquals(PrayerCacheInvalidationResult.Success, source.invalidate(request))
            assertFalse(baseFile.exists())
            assertFalse(backupFile.exists())
            assertFalse(File(baseFile.path + ".new").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun normalizedAliasesForSamePathShareOneProcessWideLock() {
        val directory = temporaryDirectory()
        val enteredFirstWrite = CountDownLatch(1)
        val releaseFirstWrite = CountDownLatch(1)
        val firstHandle = AuditAtomicFileHandle(
            directory,
            startSignal = enteredFirstWrite,
            startRelease = releaseFirstWrite
        )
        val secondHandle = AuditAtomicFileHandle(directory)
        val directSource = AtomicFilePrayerCacheDataSource(
            directory,
            AtomicFileHandleFactory { firstHandle }
        )
        val aliasedSource = AtomicFilePrayerCacheDataSource(
            File(directory, "child/.."),
            AtomicFileHandleFactory { secondHandle }
        )
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<PrayerCacheWriteResult> {
                directSource.write(request, "first")
            }
            assertTrue(enteredFirstWrite.await(2, TimeUnit.SECONDS))
            val second = executor.submit<PrayerCacheWriteResult> {
                aliasedSource.write(request, "second")
            }

            try {
                second.get(200, TimeUnit.MILLISECONDS)
                throw AssertionError("Same normalized cache path used different locks")
            } catch (_: TimeoutException) {
                // Expected: the second write is waiting for the shared path lock.
            }

            releaseFirstWrite.countDown()
            assertEquals(PrayerCacheWriteResult.Success, first.get(2, TimeUnit.SECONDS))
            assertEquals(PrayerCacheWriteResult.Success, second.get(2, TimeUnit.SECONDS))
        } finally {
            releaseFirstWrite.countDown()
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    private fun temporaryDirectory(): File {
        return Files.createTempDirectory("salati-cache-lifecycle").toFile()
    }

    private class AuditAtomicFileHandle(
        directory: File,
        initialContent: String? = null,
        private val startSignal: CountDownLatch? = null,
        private val startRelease: CountDownLatch? = null
    ) : AtomicFileHandle {
        private var content = initialContent
        private val scratch = File(directory, "scratch-${System.nanoTime()}")
        var startCalls = 0
        var finishCalls = 0
        var failCalls = 0

        override fun exists(): Boolean = content != null

        override fun readUtf8(): String = requireNotNull(content)

        override fun startWrite(): FileOutputStream {
            startCalls++
            startSignal?.countDown()
            check(startRelease?.await(5, TimeUnit.SECONDS) != false)
            return FileOutputStream(scratch, false)
        }

        override fun finishWrite(stream: FileOutputStream) {
            finishCalls++
            stream.close()
            content = scratch.readText(Charsets.UTF_8)
            scratch.delete()
        }

        override fun failWrite(stream: FileOutputStream) {
            failCalls++
            stream.close()
            scratch.delete()
        }

        override fun delete(): Boolean {
            content = null
            return true
        }
    }
}
