package io.github.sulfuro25.salati.core.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloaded audio is several megabytes of opaque bytes from the network that later gets
 * handed to the platform media decoder and played at alarm volume. These tests pin the
 * checks that stand between the two.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class AdhanAudioTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val mp3Bytes = "ID3".toByteArray() + ByteArray(2048) { it.toByte() }

    private fun option(
        id: String = "makkah",
        bytes: Long = 0L,
        sha256: String? = null,
        url: String = "https://salati.sulfuro.xyz/audio/makkah.mp3"
    ) = AdhanOption(id = id, name = "Makkah", url = url, sizeBytes = bytes, sha256 = sha256)

    private fun connection(
        status: Int = HttpURLConnection.HTTP_OK,
        contentType: String? = "audio/mpeg",
        body: ByteArray = mp3Bytes,
        declaredLength: Long? = null,
        throwOnConnect: Boolean = false
    ): (String) -> HttpURLConnection = {
        object : HttpURLConnection(URL("https://salati.sulfuro.xyz/audio/makkah.mp3")) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false
            override fun getResponseCode(): Int {
                if (throwOnConnect) throw IOException("offline")
                return status
            }
            override fun getContentType(): String? = contentType
            override fun getContentLengthLong(): Long = declaredLength ?: body.size.toLong()
            override fun getInputStream(): InputStream = ByteArrayInputStream(body)
        }
    }

    @Before
    fun clearStore() {
        AdhanAudioStore.directory(context).deleteRecursively()
    }

    @Test
    fun storesARecordingUnderItsIdAndReportsItAsDownloaded() = runBlocking {
        val result = AdhanAudioStore.download(context, option(), connection())

        assertTrue(result is AdhanDownloadResult.Success)
        assertTrue(AdhanAudioStore.isDownloaded(context, "makkah"))
        assertEquals(setOf("makkah"), AdhanAudioStore.downloadedIds(context))
        assertEquals(mp3Bytes.size.toLong(), AdhanAudioStore.fileFor(context, "makkah").length())
    }

    @Test
    fun refusesAResponseThatIsNotAudio() = runBlocking {
        // An intercepting portal answering with a login page must not become an "adhan".
        val result = AdhanAudioStore.download(
            context,
            option(),
            connection(contentType = "text/html", body = "<html>hello</html>".toByteArray())
        )

        assertTrue(result is AdhanDownloadResult.Rejected)
        assertFalse(AdhanAudioStore.isDownloaded(context, "makkah"))
    }

    @Test
    fun refusesAudioThatIsNotActuallyAnMp3() = runBlocking {
        val result = AdhanAudioStore.download(
            context,
            option(),
            connection(body = ByteArray(64) { 0x7A })
        )

        assertTrue(result is AdhanDownloadResult.Rejected)
        assertFalse(AdhanAudioStore.isDownloaded(context, "makkah"))
    }

    @Test
    fun enforcesTheSizeCapEvenWhenTheServerUnderstatesTheLength() = runBlocking {
        val oversized = "ID3".toByteArray() + ByteArray((AdhanAudioStore.MAX_BYTES + 1024).toInt())

        val result = AdhanAudioStore.download(
            context,
            option(),
            // Claims to be small, then streams far more than the cap.
            connection(body = oversized, declaredLength = 1024L)
        )

        assertTrue(result is AdhanDownloadResult.Rejected)
        assertFalse(AdhanAudioStore.isDownloaded(context, "makkah"))
        assertTrue(
            "nothing partial may be left behind",
            AdhanAudioStore.downloadedIds(context).isEmpty()
        )
    }

    @Test
    fun rejectsAMismatchedSizeOrChecksum() = runBlocking {
        val wrongSize = AdhanAudioStore.download(context, option(bytes = 999_999), connection())
        assertTrue(wrongSize is AdhanDownloadResult.Rejected)

        val wrongDigest = AdhanAudioStore.download(
            context,
            option(sha256 = "0".repeat(64)),
            connection()
        )
        assertTrue(wrongDigest is AdhanDownloadResult.Rejected)
        assertFalse(AdhanAudioStore.isDownloaded(context, "makkah"))
    }

    @Test
    fun acceptsAMatchingChecksum() = runBlocking {
        val digest = MessageDigest.getInstance("SHA-256").digest(mp3Bytes)
            .joinToString("") { "%02x".format(it) }

        val result = AdhanAudioStore.download(context, option(sha256 = digest), connection())

        assertTrue(result is AdhanDownloadResult.Success)
        assertTrue(AdhanAudioStore.isDownloaded(context, "makkah"))
    }

    @Test
    fun reportsBeingOfflineSeparatelyFromBeingRefused() = runBlocking {
        val offline = AdhanAudioStore.download(context, option(), connection(throwOnConnect = true))
        assertEquals(AdhanDownloadResult.Unreachable, offline)

        // A 404 is not worth retrying, so it must not look like a network blip.
        val missing = AdhanAudioStore.download(context, option(), connection(status = 404))
        assertTrue(missing is AdhanDownloadResult.Rejected)
    }

    @Test
    fun deletingARecordingRemovesItFromDisk() = runBlocking {
        AdhanAudioStore.download(context, option(), connection())
        assertTrue(AdhanAudioStore.isDownloaded(context, "makkah"))

        assertTrue(AdhanAudioStore.delete(context, "makkah"))

        assertFalse(AdhanAudioStore.isDownloaded(context, "makkah"))
        assertTrue(AdhanAudioStore.downloadedIds(context).isEmpty())
        // Deleting something already gone is success, not failure.
        assertTrue(AdhanAudioStore.delete(context, "makkah"))
    }

    @Test
    fun anIdThatWouldEscapeTheAdhanDirectoryIsNeverDownloaded() = runBlocking {
        val traversal = AdhanOption(
            id = "../../databases/evil",
            name = "Evil",
            url = "https://salati.sulfuro.xyz/audio/evil.mp3"
        )

        assertFalse(traversal.isDownloadable)
        val result = AdhanAudioStore.download(context, traversal, connection())
        assertTrue(result is AdhanDownloadResult.Rejected)
    }

    @Test
    fun catalogueKeepsUsableEntriesAndDropsTheRest() {
        val json = """
            {
              "version": 1,
              "adhans": [
                {"id":"makkah","name":"Makkah","url":"https://salati.sulfuro.xyz/a/makkah.mp3","bytes":3418938,"license":"CC BY-SA 4.0"},
                {"id":"madinah","name":"Madinah","url":"http://insecure.example/a.mp3"},
                {"id":"","name":"No id","url":"https://salati.sulfuro.xyz/a/x.mp3"},
                {"id":"makkah","name":"Duplicate","url":"https://salati.sulfuro.xyz/a/dupe.mp3"}
              ]
            }
        """.trimIndent()

        val result = AdhanCatalog.parse(json)

        assertTrue(result is AdhanCatalogResult.Available)
        val options = (result as AdhanCatalogResult.Available).options
        // Plain HTTP, a blank id, the duplicate, and unlicensed rows are dropped.
        assertEquals(listOf("makkah"), options.map { it.id })
        assertEquals("Makkah", options.single().name)
        assertEquals(3418938L, options.single().sizeBytes)
    }

    @Test
    fun catalogueSurvivesUnknownFieldsButRejectsRubbish() {
        val forwardCompatible = """
            {"version":2,"adhans":[
              {"id":"makkah","name":"Makkah","url":"https://salati.sulfuro.xyz/a.mp3","license":"CC BY-SA 4.0","licence":"ignored"}
            ],"notes":"added later"}
        """.trimIndent()
        assertTrue(AdhanCatalog.parse(forwardCompatible) is AdhanCatalogResult.Available)

        assertEquals(AdhanCatalogResult.Malformed, AdhanCatalog.parse("not json at all"))
        assertEquals(AdhanCatalogResult.Malformed, AdhanCatalog.parse(""))
    }

    @Test
    fun anEmptyCatalogueIsAValidAnswerRatherThanAnError() {
        val result = AdhanCatalog.parse("""{"version":1,"adhans":[]}""")

        assertEquals(AdhanCatalogResult.Available(emptyList()), result)
    }

    @Test
    fun retiredRecordingsAreDeletedFromDiskAndClearedFromSettings() {
        val file = AdhanAudioStore.fileFor(context, "makkah_mullah")
        file.parentFile?.mkdirs()
        file.writeBytes(mp3Bytes)
        assertTrue(AdhanAudioStore.isDownloaded(context, "makkah_mullah"))

        AdhanAudioStore.deleteRetired(context)
        assertFalse(AdhanAudioStore.isDownloaded(context, "makkah_mullah"))

        val settings = io.github.sulfuro25.salati.data.settings.CalculationSettings(
            adhanSoundId = "makkah_mullah",
            adhanSoundName = "Ali Mullah",
            fajrAdhanSoundId = "fajr_makkah",
            fajrAdhanSoundName = "Fajr Makkah"
        )
        val cleaned = settings.withoutRetiredAdhanChoices()
        assertNull(cleaned.adhanSoundId)
        assertNull(cleaned.adhanSoundName)
        assertNull(cleaned.fajrAdhanSoundId)
        assertNull(cleaned.fajrAdhanSoundName)
    }

    @Test
    fun hostedCatalogueOnlyOffersHttpsRecordingsWithUsableIds() {
        val file = listOf(
            java.io.File("adhans.json"),
            java.io.File("..", "adhans.json")
        ).firstOrNull { it.isFile } ?: error("adhans.json missing")
        val result = AdhanCatalog.parse(file.readText())
        assertTrue(result is AdhanCatalogResult.Available)
        val options = (result as AdhanCatalogResult.Available).options
        assertTrue(options.isNotEmpty())
        for (option in options) {
            assertTrue(option.hasUsableId)
            assertTrue(option.url.startsWith("https://"))
            assertFalse(AdhanAudioStore.RETIRED_IDS.contains(option.id))
        }
    }

    private fun manifestConnection(
        fetches: IntArray,
        status: Int = HttpURLConnection.HTTP_OK
    ): (String) -> HttpURLConnection = {
        object : HttpURLConnection(URL(AdhanCatalog.MANIFEST_URL)) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false
            override fun getResponseCode(): Int {
                fetches[0]++
                return status
            }
            override fun getInputStream(): InputStream = ByteArrayInputStream(
                """{"version":1,"adhans":[
                     {"id":"makkah","name":"Makkah","url":"https://salati.sulfuro.xyz/a.mp3"}
                   ]}""".toByteArray()
            )
        }
    }

    /**
     * The manifest changes when a recording is added or withdrawn, which is rare; opening
     * the picker is not. Every open used to spend a round trip to be told the same thing.
     */
    @Test
    fun theManifestIsFetchedOnceAndReusedUntilItGoesStale() = runBlocking {
        AdhanCatalogFetcher.clearCache()
        val fetches = intArrayOf(0)
        var clock = 1_000_000L

        val first = AdhanCatalogFetcher.fetch(
            AdhanCatalog.MANIFEST_URL, manifestConnection(fetches)
        ) { clock }
        val reopened = AdhanCatalogFetcher.fetch(
            AdhanCatalog.MANIFEST_URL, manifestConnection(fetches)
        ) { clock + AdhanCatalogFetcher.CACHE_TTL_MILLIS - 1 }

        assertEquals(first, reopened)
        assertEquals(1, fetches[0])

        // Past the window it goes back to the network, so a withdrawn recording does
        // eventually disappear from the list.
        AdhanCatalogFetcher.fetch(
            AdhanCatalog.MANIFEST_URL, manifestConnection(fetches)
        ) { clock + AdhanCatalogFetcher.CACHE_TTL_MILLIS }
        assertEquals(2, fetches[0])
    }

    /** Being offline is not an answer worth keeping for six hours. */
    @Test
    fun anUnreachableManifestIsNeverCached() = runBlocking {
        AdhanCatalogFetcher.clearCache()
        val failed = intArrayOf(0)

        val first = AdhanCatalogFetcher.fetch(
            AdhanCatalog.MANIFEST_URL,
            manifestConnection(failed, status = HttpURLConnection.HTTP_UNAVAILABLE)
        )
        assertEquals(AdhanCatalogResult.Unreachable, first)

        val succeeded = intArrayOf(0)
        val retry = AdhanCatalogFetcher.fetch(
            AdhanCatalog.MANIFEST_URL, manifestConnection(succeeded)
        )
        assertTrue(retry is AdhanCatalogResult.Available)
        assertEquals(1, succeeded[0])

        AdhanCatalogFetcher.clearCache()
    }
}
