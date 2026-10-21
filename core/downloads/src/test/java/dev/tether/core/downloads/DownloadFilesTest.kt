package dev.tether.core.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadFilesTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun derivesExtensionFromUrlThenContentType() {
        assertEquals("flac", DownloadFiles.extensionFor("https://example.org/song.flac?token=1", null))
        assertEquals("ogg", DownloadFiles.extensionFor("https://example.org/stream", "audio/ogg; codecs=opus"))
        assertEquals("m4a", DownloadFiles.extensionFor("https://example.org/stream", "audio/mp4"))
        assertEquals("mp3", DownloadFiles.extensionFor("https://example.org/stream", null))
    }

    @Test
    fun safeFileNameKeepsReadableTextAndStableSuffix() {
        val first = DownloadFiles.fileName(
            trackId = "ccmixter:42",
            title = "A/B: test?",
            artist = "Artist",
            extension = "mp3",
        )
        val second = DownloadFiles.fileName(
            trackId = "ccmixter:42",
            title = "A/B: test?",
            artist = "Artist",
            extension = "mp3",
        )
        assertEquals(first, second)
        assertTrue(first.startsWith("Artist - A B test"))
        assertTrue(first.endsWith(".mp3"))
        assertFalse(first.contains("?") || first.contains(":") || first.contains("/"))
    }

    @Test
    fun acceptsARealMp3Header() {
        val file = temporary.newFile("track.mp3")
        file.writeBytes(ByteArray(1024).also {
            it[0] = 'I'.code.toByte()
            it[1] = 'D'.code.toByte()
            it[2] = '3'.code.toByte()
        })
        assertTrue(DownloadFiles.validate(file, 1024, "mp3").valid)
    }

    @Test
    fun rejectsHtmlInsteadOfAudio() {
        val file = temporary.newFile("fake.mp3")
        val html = "<!doctype html><html><body>Sign in to download</body></html>".toByteArray()
        file.writeBytes(html + ByteArray(1024 - html.size))
        val result = DownloadFiles.validate(file, 1024, "mp3")
        assertFalse(result.valid)
        assertTrue(result.reason.orEmpty().contains("not a valid MP3"))
    }

    @Test
    fun rejectsTruncatedTransfer() {
        val file = temporary.newFile("cut.flac")
        file.writeBytes("fLaC".toByteArray() + ByteArray(900))
        val result = DownloadFiles.validate(file, 5000, "flac")
        assertFalse(result.valid)
        assertTrue(result.reason.orEmpty().contains("Only"))
    }

    @Test
    fun acceptsFlacContainer() {
        val file = temporary.newFile("song.flac")
        file.writeBytes("fLaC".toByteArray() + ByteArray(2048))
        assertTrue(DownloadFiles.validate(file, 2052, "flac").valid)
    }

    @Test
    fun attributionKeepsLicenseAndSource() {
        val text = DownloadFiles.attributionText(
            title = "Feather",
            artist = "Tether",
            sourceLabel = "ccMixter",
            license = "https://creativecommons.org/licenses/by/4.0/",
            sourceUri = "https://example.org/feather.mp3",
            downloadedAt = 1_700_000_000_000,
        )
        assertTrue(text.contains("creativecommons.org"))
        assertTrue(text.contains("ccMixter"))
        assertTrue(text.contains("https://example.org/feather.mp3"))
        assertTrue(text.contains("Keep this file"))
    }
}
