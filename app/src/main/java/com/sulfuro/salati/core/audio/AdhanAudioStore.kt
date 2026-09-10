package com.sulfuro.salati.core.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import com.sulfuro.salati.data.settings.CalculationSettings
import java.security.MessageDigest

sealed interface AdhanDownloadResult {
    data class Success(val file: File) : AdhanDownloadResult

    /** The device is offline or the host did not answer. Worth retrying. */
    data object Unreachable : AdhanDownloadResult

    /** The server answered, but not with the recording. Retrying will not help. */
    data class Rejected(val reason: String) : AdhanDownloadResult

    /** Storage was full or unwritable. */
    data class NotStored(val cause: Throwable) : AdhanDownloadResult
}

/**
 * Downloads adhan recordings and keeps them on the device.
 *
 * Downloaded audio gets more scrutiny than a JSON response would: it is several megabytes
 * of opaque bytes that will later be handed to the platform media decoder, so the size is
 * capped, the content type is checked, the first bytes have to actually look like audio,
 * and a digest is verified whenever the manifest supplies one. A file only becomes visible
 * under its real name once all of that passes, so a failed or interrupted download can
 * never leave a half-written file that the alarm path would later try to play.
 */
object AdhanAudioStore {

    private const val TAG = "AdhanAudioStore"
    private const val DIRECTORY = "adhan"
    private const val EXTENSION = ".mp3"

    /** Comfortably above the longest adhan, far below anything that would fill a disk. */
    internal const val MAX_BYTES = 12L * 1024 * 1024

    private const val CONNECT_TIMEOUT_MILLIS = 15_000
    private const val READ_TIMEOUT_MILLIS = 30_000

    fun directory(context: Context): File = File(context.filesDir, DIRECTORY)

    fun fileFor(context: Context, id: String): File = File(directory(context), id + EXTENSION)

    fun isDownloaded(context: Context, id: String): Boolean {
        if (id.isBlank()) return false
        val file = fileFor(context, id)
        return file.isFile && file.length() > 0
    }

    /** Ids of every recording currently held on disk. */
    fun downloadedIds(context: Context): Set<String> {
        val files = directory(context).listFiles() ?: return emptySet()
        return files
            .filter { it.isFile && it.name.endsWith(EXTENSION) && it.length() > 0 }
            .map { it.name.removeSuffix(EXTENSION) }
            .toSet()
    }

    fun delete(context: Context, id: String): Boolean {
        val file = fileFor(context, id)
        return !file.exists() || file.delete()
    }

    /**
     * Recordings that were previously offered without a documented redistribution
     * license. They must not be played or kept on disk after an app update.
     */
    val RETIRED_IDS: Set<String> = setOf(
        "makkah_mullah",
        "makkah_faydah",
        "madinah_short",
        "quba",
        "al_surehi",
        "fajr_makkah",
        "fajr_madinah",
        "fajr_abdul_basit",
        "aaqib_azeez"
    )

    fun deleteRetired(context: Context) {
        for (id in RETIRED_IDS) {
            delete(context, id)
        }
    }

    suspend fun download(
        context: Context,
        option: AdhanOption,
        openConnection: (String) -> HttpURLConnection = ::defaultConnection
    ): AdhanDownloadResult = withContext(Dispatchers.IO) {
        if (!option.isDownloadable) {
            return@withContext AdhanDownloadResult.Rejected("unusable catalogue entry")
        }

        val directory = directory(context)
        if (!directory.isDirectory && !directory.mkdirs()) {
            return@withContext AdhanDownloadResult.NotStored(
                IOException("Could not create the adhan directory")
            )
        }

        // Downloaded under a scratch name so an interrupted transfer cannot be mistaken
        // for a complete recording by the alarm path.
        val partial = File(directory, option.id + EXTENSION + ".part")
        val connection = try {
            openConnection(option.url)
        } catch (cause: IOException) {
            Log.w(TAG, "Could not reach ${option.url}", cause)
            return@withContext AdhanDownloadResult.Unreachable
        }

        try {
            val status = try {
                connection.responseCode
            } catch (cause: IOException) {
                Log.w(TAG, "No response for ${option.url}", cause)
                return@withContext AdhanDownloadResult.Unreachable
            }
            if (status != HttpURLConnection.HTTP_OK) {
                return@withContext AdhanDownloadResult.Rejected("HTTP $status")
            }

            val contentType = connection.contentType.orEmpty()
            if (!contentType.startsWith("audio/", ignoreCase = true)) {
                return@withContext AdhanDownloadResult.Rejected("not audio: $contentType")
            }

            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_BYTES) {
                return@withContext AdhanDownloadResult.Rejected("too large: $declaredLength bytes")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            try {
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            // Enforced while streaming, so a server that lies about or
                            // omits its length cannot run the disk out of space.
                            if (total > MAX_BYTES) {
                                return@withContext AdhanDownloadResult.Rejected("exceeded size cap")
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } catch (cause: IOException) {
                Log.w(TAG, "Transfer of ${option.id} failed", cause)
                return@withContext AdhanDownloadResult.Unreachable
            }

            if (total == 0L) {
                return@withContext AdhanDownloadResult.Rejected("empty response")
            }
            if (option.sizeBytes > 0 && total != option.sizeBytes) {
                return@withContext AdhanDownloadResult.Rejected(
                    "expected ${option.sizeBytes} bytes, received $total"
                )
            }
            if (!looksLikeMp3(partial)) {
                return@withContext AdhanDownloadResult.Rejected("not an MP3 recording")
            }
            val expectedDigest = option.sha256
            if (!expectedDigest.isNullOrBlank()) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedDigest, ignoreCase = true)) {
                    return@withContext AdhanDownloadResult.Rejected("checksum mismatch")
                }
            }

            val destination = fileFor(context, option.id)
            destination.delete()
            if (!partial.renameTo(destination)) {
                return@withContext AdhanDownloadResult.NotStored(
                    IOException("Could not move the downloaded recording into place")
                )
            }
            AdhanDownloadResult.Success(destination)
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    /** ID3-tagged files start with "ID3"; a bare MPEG stream starts with a frame sync. */
    internal fun looksLikeMp3(file: File): Boolean {
        return runCatching {
            file.inputStream().use { stream ->
                val header = ByteArray(3)
                if (stream.read(header) != 3) return@use false
                val isId3 = header[0] == 'I'.code.toByte() &&
                    header[1] == 'D'.code.toByte() &&
                    header[2] == '3'.code.toByte()
                val isFrameSync = header[0] == 0xFF.toByte() &&
                    (header[1].toInt() and 0xE0) == 0xE0
                isId3 || isFrameSync
            }
        }.getOrDefault(false)
    }

    private fun defaultConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
    }
}

fun CalculationSettings.withoutRetiredAdhanChoices(): CalculationSettings {
    var next = this
    if (adhanSoundId in AdhanAudioStore.RETIRED_IDS) {
        next = next.copy(adhanSoundId = null, adhanSoundName = null)
    }
    if (fajrAdhanSoundId in AdhanAudioStore.RETIRED_IDS) {
        next = next.copy(fajrAdhanSoundId = null, fajrAdhanSoundName = null)
    }
    return next
}
