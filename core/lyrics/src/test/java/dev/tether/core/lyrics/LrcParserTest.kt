package dev.tether.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesSyncedLyricsAndSortsLines() {
        val parsed = LrcParser.parse(
            """
            [ar:Tether]
            [00:12.50]Second line
            [00:01.00]First line
            [00:20.00]
            [00:30.123]Third line
            """.trimIndent(),
        )
        assertTrue(parsed.synced)
        assertEquals(3, parsed.lines.size)
        assertEquals("First line", parsed.lines[0].text)
        assertEquals(1000L, parsed.lines[0].timeMs)
        assertEquals(30_123L, parsed.lines[2].timeMs)
    }

    @Test
    fun mixedTimedAndUntimedIsNotFakedAsSynced() {
        val parsed = LrcParser.parse("[00:01.00]Timed\nAn untimed line")
        assertFalse(parsed.synced)
        assertEquals(2, parsed.lines.size)
        assertTrue(parsed.lines.all { it.timeMs == null })
    }

    @Test
    fun plainLyricsStayPlain() {
        val parsed = LrcParser.parse("Hello\nWorld")
        assertFalse(parsed.synced)
        assertEquals(listOf("Hello", "World"), parsed.lines.map { it.text })
    }

    @Test
    fun usesOffset() {
        val parsed = LrcParser.parse("[offset:500]\n[00:02.00]Line\n[00:03.00]Next")
        assertEquals(1500L, parsed.lines[0].timeMs)
    }

    @Test
    fun findsCurrentLine() {
        val parsed = LrcParser.parse("[00:01.00]A\n[00:05.00]B\n[00:09.00]C")
        assertEquals(0, LrcParser.currentLineIndex(parsed.lines, 1_000))
        assertEquals(1, LrcParser.currentLineIndex(parsed.lines, 7_000))
        assertEquals(2, LrcParser.currentLineIndex(parsed.lines, 30_000))
        assertEquals(-1, LrcParser.currentLineIndex(emptyList(), 0))
    }

    @Test
    fun enhancedWordTagsAreRemoved() {
        val parsed = LrcParser.parse("[00:01.00]<00:01.00>Hello <00:02.00>world\n[00:03.00]Again")
        assertEquals("Hello world", parsed.lines[0].text)
    }
}
