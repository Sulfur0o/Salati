package com.sulfuro.salati.release

import com.sulfuro.salati.core.audio.AdhanAudioStore
import com.sulfuro.salati.core.audio.AdhanCatalog
import com.sulfuro.salati.core.audio.AdhanCatalogResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The published catalogue has to agree with the audio that is published beside it.
 *
 * Two shipped bugs came from it drifting instead. `abdul_basit` and `madinah` were reused
 * for different recordings, which no download could ever repair: the app keeps a file
 * named after its id and verifies the digest only while fetching, so every install that
 * already had one went on playing the old audio for ever. And `aqsa` was dropped from the
 * catalogue without being retired, so it stayed on disk, kept playing, and left the picker
 * with no row selected at all - not even "Device tone" - because the stored id matched
 * nothing on offer.
 *
 * Both are invisible to a person reading the JSON, and both are trivial to check here.
 */
class AdhanManifestIntegrityTest {

    private val repositoryRoot = File("..")
    private val manifestFile = File(repositoryRoot, "adhans.json")
    private val audioRoot = File(repositoryRoot, "audio")

    private val options by lazy {
        val parsed = AdhanCatalog.parse(manifestFile.readText())
        assertTrue("adhans.json must parse", parsed is AdhanCatalogResult.Available)
        (parsed as AdhanCatalogResult.Available).options
    }

    @Test
    fun theManifestIsPresentAndOffersRecordings() {
        assertTrue("adhans.json is missing from the repository root", manifestFile.isFile)
        assertTrue("the catalogue must not be empty", options.isNotEmpty())
    }

    /** A retired id in the manifest would be filtered out and silently vanish. */
    @Test
    fun noOfferedRecordingHasBeenRetired() {
        for (option in options) {
            assertFalse(
                "${option.id} is offered and retired at the same time",
                option.id in AdhanAudioStore.RETIRED_IDS
            )
        }
    }

    @Test
    fun everyIdIsUnique() {
        val duplicates = options.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate ids: $duplicates", duplicates.isEmpty())
    }

    /**
     * The download is stored as `<id>.mp3`, so an entry whose url points at a different
     * basename would put one recording on disk under another one's name.
     */
    @Test
    fun everyUrlMatchesItsIdAndItsFajrFlag() {
        for (option in options) {
            val folder = if (option.isFajr) "fajr" else "regular"
            assertEquals(
                "${option.id} points somewhere its id and fajr flag do not agree with",
                "https://salati.sulfuro.xyz/audio/$folder/${option.id}.mp3",
                option.url
            )
        }
    }

    @Test
    fun everyRecordingExistsAndIsTheSizeTheManifestClaims() {
        for (option in options) {
            val folder = if (option.isFajr) "fajr" else "regular"
            val file = File(audioRoot, "$folder/${option.id}.mp3")
            assertTrue("${option.id}: no file at ${file.path}", file.isFile)
            assertEquals("${option.id}: declared size is wrong", file.length(), option.sizeBytes)
        }
    }

    /**
     * A file with no entry is unreachable from the picker but still playable if some older
     * install has it, and still costs its megabytes in the repository.
     */
    @Test
    fun noAudioFileIsOrphanedByTheManifest() {
        val listed = options.map { (if (it.isFajr) "fajr" else "regular") + "/" + it.id }.toSet()
        val onDisk = buildSet {
            for (folder in listOf("fajr", "regular")) {
                File(audioRoot, folder).listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".mp3") }
                    ?.forEach { add("$folder/" + it.name.removeSuffix(".mp3")) }
            }
        }
        assertEquals("audio files no entry points at", emptySet<String>(), onDisk - listed)
    }

    /** Every entry carries a digest, and it is the digest of the file that is published. */
    @Test
    fun everyRecordingCarriesTheDigestOfWhatIsActuallyHosted() {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for (option in options) {
            val folder = if (option.isFajr) "fajr" else "regular"
            val file = File(audioRoot, "$folder/${option.id}.mp3")
            digest.reset()
            file.inputStream().use { stream ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            assertEquals("${option.id}: sha256 does not match the hosted file", actual, option.sha256)
        }
    }

    /**
     * An entry over the cap cannot be downloaded at all, and the user is only told that it
     * failed. Checked here so it is caught when the catalogue is built rather than by
     * whoever picks that row.
     */
    @Test
    fun noRecordingExceedsWhatTheAppWillDownload() {
        for (option in options) {
            assertTrue(
                "${option.id} is ${option.sizeBytes} bytes, over the " +
                    "${AdhanAudioStore.MAX_BYTES} cap",
                option.sizeBytes <= AdhanAudioStore.MAX_BYTES
            )
        }
    }

    /** Shown in the picker, so wrong values here are wrong on screen. */
    @Test
    fun everyRecordingDeclaresAPlausibleDuration() {
        for (option in options) {
            assertTrue("${option.id} has no duration", option.seconds > 0)
            assertTrue(
                "${option.id} claims ${option.seconds}s, which is not an adhan",
                option.seconds in 5.0..900.0
            )
        }
    }

    /**
     * A bumped version means something existing changed meaning, so an install that
     * predates the change must refuse the list rather than misread it.
     */
    @Test
    fun aNewerManifestVersionIsRefusedRatherThanGuessedAt() {
        val future = """{"version": ${AdhanCatalog.SUPPORTED_VERSION + 1}, "adhans": []}"""

        assertEquals(AdhanCatalogResult.Malformed, AdhanCatalog.parse(future))
    }
}
