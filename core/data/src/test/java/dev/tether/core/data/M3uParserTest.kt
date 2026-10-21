package dev.tether.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {
    @Test
    fun parsesExtendedEntries() {
        val entries = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:245,The Artist - A Song
            content://media/external/audio/media/1
            #EXTINF:61,Another Song
            file:///storage/emulated/0/Music/two.mp3
            """.trimIndent(),
        )
        assertEquals(2, entries.size)
        assertEquals("The Artist", entries[0].artist)
        assertEquals("A Song", entries[0].title)
        assertEquals(245_000L, entries[0].durationMs)
        assertEquals("Another Song", entries[1].title)
        assertNull(entries[1].artist)
    }

    @Test
    fun ignoresCommentsAndBlankLines() {
        val entries = M3uParser.parse("#EXTM3U\n\n# comment\n/sdcard/Music/a.flac")
        assertEquals(1, entries.size)
        assertEquals("/sdcard/Music/a.flac", entries[0].uri)
    }
}
