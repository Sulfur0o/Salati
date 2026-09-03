package io.github.sulfuro25.salati.core.computation

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

sealed interface PrayerCacheReadResult {
    data class Success(val rawJson: String) : PrayerCacheReadResult
    data object Missing : PrayerCacheReadResult
    data class Failure(val cause: Throwable) : PrayerCacheReadResult
}

sealed interface PrayerCacheWriteResult {
    data object Success : PrayerCacheWriteResult
    data class Failure(val cause: Throwable) : PrayerCacheWriteResult
}

sealed interface PrayerCacheInvalidationResult {
    data object Success : PrayerCacheInvalidationResult
    data object Missing : PrayerCacheInvalidationResult
    data class Failure(val cause: Throwable) : PrayerCacheInvalidationResult
}

interface PrayerCacheDataSource {
    fun read(request: PrayerMonthRequest): PrayerCacheReadResult
    fun write(request: PrayerMonthRequest, rawJson: String): PrayerCacheWriteResult
    fun invalidate(request: PrayerMonthRequest): PrayerCacheInvalidationResult
}

internal interface AtomicFileHandle {
    fun exists(): Boolean
    fun readUtf8(): String
    fun startWrite(): FileOutputStream
    fun finishWrite(stream: FileOutputStream)
    fun failWrite(stream: FileOutputStream)
    fun delete(): Boolean
}

internal fun interface AtomicFileHandleFactory {
    fun create(file: File): AtomicFileHandle
}

private class AndroidAtomicFileHandle(file: File) : AtomicFileHandle {
    private val baseFile = file
    private val atomicFile = AtomicFile(file)

    override fun exists(): Boolean = baseFile.exists() || backupFile().exists()

    override fun readUtf8(): String = atomicFile.openRead().bufferedReader(Charsets.UTF_8).use {
        it.readText()
    }

    override fun startWrite(): FileOutputStream = atomicFile.startWrite()

    override fun finishWrite(stream: FileOutputStream) = atomicFile.finishWrite(stream)

    override fun failWrite(stream: FileOutputStream) = atomicFile.failWrite(stream)

    override fun delete(): Boolean {
        atomicFile.delete()
        return listOf(baseFile, backupFile(), newFile()).none(File::exists)
    }

    private fun backupFile(): File = File(baseFile.path + ".bak")

    private fun newFile(): File = File(baseFile.path + ".new")
}

class AtomicFilePrayerCacheDataSource internal constructor(
    private val filesDir: File,
    private val atomicFileFactory: AtomicFileHandleFactory
) : PrayerCacheDataSource {
    constructor(filesDir: File) : this(filesDir, AtomicFileHandleFactory(::AndroidAtomicFileHandle))

    override fun read(request: PrayerMonthRequest): PrayerCacheReadResult {
        return withLockedFile(request) { atomicFile ->
            if (!atomicFile.exists()) {
                PrayerCacheReadResult.Missing
            } else {
                try {
                    PrayerCacheReadResult.Success(atomicFile.readUtf8())
                } catch (cause: Exception) {
                    PrayerCacheReadResult.Failure(cause)
                }
            }
        }
    }

    override fun write(request: PrayerMonthRequest, rawJson: String): PrayerCacheWriteResult {
        return withLockedFile(request) { atomicFile ->
            var stream: FileOutputStream? = null
            try {
                stream = atomicFile.startWrite()
                stream.write(rawJson.toByteArray(Charsets.UTF_8))
                stream.flush()
                atomicFile.finishWrite(stream)
                PrayerCacheWriteResult.Success
            } catch (cause: Exception) {
                stream?.let {
                    try {
                        atomicFile.failWrite(it)
                    } catch (rollbackCause: Exception) {
                        cause.addSuppressed(rollbackCause)
                    }
                }
                PrayerCacheWriteResult.Failure(cause)
            }
        }
    }

    override fun invalidate(request: PrayerMonthRequest): PrayerCacheInvalidationResult {
        return withLockedFile(request) { atomicFile ->
            if (!atomicFile.exists()) {
                PrayerCacheInvalidationResult.Missing
            } else {
                try {
                    if (atomicFile.delete()) {
                        PrayerCacheInvalidationResult.Success
                    } else {
                        PrayerCacheInvalidationResult.Failure(
                            IOException("Cache file still exists after invalidation")
                        )
                    }
                } catch (cause: Exception) {
                    PrayerCacheInvalidationResult.Failure(cause)
                }
            }
        }
    }

    internal fun fileFor(request: PrayerMonthRequest): File {
        return File(filesDir, cacheFileName(request))
    }

    private fun <T> withLockedFile(request: PrayerMonthRequest, block: (AtomicFileHandle) -> T): T {
        val file = fileFor(request)
        val lock = locks.computeIfAbsent(lockKey(file)) { Any() }
        return synchronized(lock) {
            block(atomicFileFactory.create(file))
        }
    }

    private companion object {
        val locks = ConcurrentHashMap<String, Any>()

        fun lockKey(file: File): String = file.absoluteFile.normalize().path
    }
}

internal fun locationCacheKey(latitude: Double, longitude: Double): String {
    fun fixed(value: Double) = String.format(java.util.Locale.ROOT, "%.3f", value)
        .replace('-', 'n')
        .replace('.', 'p')
    return "lat${fixed(latitude)}_lon${fixed(longitude)}"
}

internal fun cacheFileName(request: PrayerMonthRequest): String {
    val locationKey = locationCacheKey(request.latitude, request.longitude)
    return "prayers_v2_${locationKey}_${request.year}_${request.month}" +
        "_m${request.methodId}_s${request.schoolId}_h${request.latitudeAdjustmentId}.json"
}
