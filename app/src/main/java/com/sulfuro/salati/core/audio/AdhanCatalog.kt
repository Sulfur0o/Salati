package com.sulfuro.salati.core.audio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One adhan recording that can be downloaded.
 *
 * @param id stable key stored in settings and used as the on-disk filename, so it must
 *   never change for a given recording.
 * @param sizeBytes the expected download size, shown before the user commits to it and
 *   checked afterwards.
 * @param sha256 lower-case hex digest. Optional in the manifest, but when present the
 *   download is rejected unless it matches.
 * @param isFajr whether this is a Fajr recording. The Fajr adhan carries
 *   "as-salatu khayrun min an-nawm", which belongs at dawn only, so these are offered for
 *   the Fajr slot and kept out of the one that plays at the other four prayers.
 */
@Serializable
data class AdhanOption(
    val id: String,
    val name: String,
    val url: String,
    @SerialName("bytes") val sizeBytes: Long = 0L,
    val reciter: String? = null,
    val sha256: String? = null,
    @SerialName("fajr") val isFajr: Boolean = false,
    val license: String? = null,
    val attribution: String? = null
) {
    /** Guards against a manifest entry that would write outside the adhan directory. */
    val hasUsableId: Boolean
        get() = id.isNotBlank() && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }

    val isDownloadable: Boolean
        get() = hasUsableId && name.isNotBlank() && url.startsWith("https://")
}

@Serializable
data class AdhanManifest(
    val version: Int = 1,
    val adhans: List<AdhanOption> = emptyList()
)

sealed interface AdhanCatalogResult {
    data class Available(val options: List<AdhanOption>) : AdhanCatalogResult
    data object Unreachable : AdhanCatalogResult
    data object Malformed : AdhanCatalogResult
}

/**
 * Reads the list of downloadable adhan recordings.
 *
 * The list is fetched rather than compiled in, for two reasons. Audio is measured in
 * megabytes and most users want at most one, so shipping a library of them inside the APK
 * would make everyone pay for what few of them use. And a hosted list can be corrected -
 * a recording replaced, an attribution fixed, one withdrawn - without an app update.
 */
object AdhanCatalog {

    /**
     * Manifest of available recordings.
     *
     * Deliberately served from Salati's own domain rather than a third-party audio CDN.
     * The recordings are redistributed to users, so their provenance and licensing have
     * to be something this project can actually vouch for.
     */
    const val MANIFEST_URL = "https://salati.sulfuro.xyz/adhans.json"

    /** A manifest is a short list of short strings; anything larger is not one. */
    internal const val MAX_MANIFEST_BYTES = 256 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    internal fun parse(rawJson: String): AdhanCatalogResult {
        val manifest = runCatching { json.decodeFromString(AdhanManifest.serializer(), rawJson) }
            .getOrElse { return AdhanCatalogResult.Malformed }

        // A single bad entry should not cost the user the whole list.
        // Retired recordings are dropped even if a stale hosted manifest still lists them.
        val usable = manifest.adhans
            .filter(AdhanOption::isDownloadable)
            .filter { it.id !in AdhanAudioStore.RETIRED_IDS }
            .distinctBy(AdhanOption::id)

        return AdhanCatalogResult.Available(usable)
    }
}

/** Network access for the manifest, kept separate so tests can drive [AdhanCatalog.parse]. */
object AdhanCatalogFetcher {

    private const val CONNECT_TIMEOUT_MILLIS = 10_000
    private const val READ_TIMEOUT_MILLIS = 15_000

    /**
     * How long a fetched manifest is reused within the process.
     *
     * The list changes when a recording is added, replaced or withdrawn, which is rare;
     * opening the picker is not, and every open was paying for a round trip that returned
     * the same handful of entries. Only successes are held - an unreachable host has to be
     * retried, or the user would be stuck with "offline" for hours after coming back.
     */
    internal const val CACHE_TTL_MILLIS = 6L * 60 * 60 * 1000

    private class CachedManifest(
        val url: String,
        val options: List<AdhanOption>,
        val fetchedAtMillis: Long
    )

    @Volatile
    private var cached: CachedManifest? = null

    /** Drops the held manifest, so the next [fetch] goes to the network. */
    internal fun clearCache() {
        cached = null
    }

    suspend fun fetch(
        url: String = AdhanCatalog.MANIFEST_URL,
        openConnection: (String) -> java.net.HttpURLConnection = ::defaultConnection,
        nowMillis: () -> Long = System::currentTimeMillis
    ): AdhanCatalogResult {
        cached?.let { held ->
            if (held.url == url && nowMillis() - held.fetchedAtMillis < CACHE_TTL_MILLIS) {
                return AdhanCatalogResult.Available(held.options)
            }
        }
        val result = fetchFromNetwork(url, openConnection)
        if (result is AdhanCatalogResult.Available) {
            cached = CachedManifest(url, result.options, nowMillis())
        }
        return result
    }

    private suspend fun fetchFromNetwork(
        url: String,
        openConnection: (String) -> java.net.HttpURLConnection
    ): AdhanCatalogResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val connection = try {
            openConnection(url)
        } catch (cause: java.io.IOException) {
            return@withContext AdhanCatalogResult.Unreachable
        }
        try {
            if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                return@withContext AdhanCatalogResult.Unreachable
            }
            val body = connection.inputStream.use { stream ->
                // Bounded read: a manifest is a short list and the reply is untrusted.
                // Written out rather than using readNBytes, which is Java 9 and not one
                // of the APIs core library desugaring back-fills for minSdk 24.
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                while (buffer.size() < AdhanCatalog.MAX_MANIFEST_BYTES) {
                    val read = stream.read(chunk)
                    if (read <= 0) break
                    buffer.write(chunk, 0, read)
                }
                String(buffer.toByteArray(), Charsets.UTF_8)
            }
            AdhanCatalog.parse(body)
        } catch (cause: java.io.IOException) {
            AdhanCatalogResult.Unreachable
        } finally {
            connection.disconnect()
        }
    }

    private fun defaultConnection(url: String): java.net.HttpURLConnection {
        return (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            requestMethod = "GET"
        }
    }
}
