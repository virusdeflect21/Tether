package dev.tether.core.lyrics

import dev.tether.core.model.LyricsLine

data class ParsedLyrics(
    val lines: List<LyricsLine>,
    val synced: Boolean,
    val plainText: String,
) {
    val hasContent: Boolean get() = lines.any { it.text.isNotBlank() }
}

/**
 * A deliberately conservative LRC parser.
 *
 * It only reports [ParsedLyrics.synced] when every displayed lyric line has a real
 * timestamp. Mixed files and plain text are shown as plain text instead of being
 * animated using guessed timing.
 */
object LrcParser {
    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val offsetTag = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    private val metadataTag = Regex("^\\[[a-zA-Z#]+:[^]]*]$")
    private val enhancedWordTag = Regex("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>")
    private val inlineTimestamp = Regex("^\\d{1,2}:\\d{1,2}(?:[.:]\\d{1,3})?\\s+")

    fun parse(raw: String, forcePlain: Boolean = false): ParsedLyrics {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isBlank()) return ParsedLyrics(emptyList(), false, "")
        val offset = offsetTag.find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val timed = mutableListOf<LyricsLine>()
        val display = mutableListOf<String>()
        var sawTimestamp = false
        var sawUntimedText = false

        normalized.lineSequence().forEach { sourceLine ->
            val line = sourceLine.trim()
            if (line.isEmpty()) return@forEach
            if (offsetTag.matches(line) || metadataTag.matches(line)) return@forEach

            val matches = timeTag.findAll(line).toList()
            if (matches.isNotEmpty()) {
                sawTimestamp = true
                val text = line.substring(matches.last().range.last + 1)
                    .replace(enhancedWordTag, "")
                    .trim()
                if (text.isNotEmpty()) {
                    display += text
                    matches.forEach { match ->
                        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                        val fractionRaw = match.groupValues[3]
                        val fraction = when (fractionRaw.length) {
                            0 -> 0L
                            1 -> fractionRaw.toLongOrNull().orZero() * 100
                            2 -> fractionRaw.toLongOrNull().orZero() * 10
                            else -> fractionRaw.take(3).toLongOrNull().orZero()
                        }
                        val time = (minutes * 60_000 + seconds * 1000 + fraction - offset).coerceAtLeast(0)
                        timed += LyricsLine(time, text)
                    }
                }
            } else {
                val text = line.replace(enhancedWordTag, "").replace(inlineTimestamp, "").trim()
                if (text.isNotEmpty()) {
                    sawUntimedText = true
                    display += text
                }
            }
        }

        val canSync = !forcePlain && sawTimestamp && !sawUntimedText && timed.size >= 2
        if (canSync) {
            val sorted = timed.sortedBy { it.timeMs ?: 0L }
            return ParsedLyrics(sorted, true, sorted.joinToString("\n") { it.text })
        }

        val lines = display.map { LyricsLine(null, it) }
        return ParsedLyrics(lines, false, lines.joinToString("\n") { it.text })
    }

    fun currentLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            val time = lines[mid].timeMs
            if (time == null) return -1
            if (time <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    private fun Long?.orZero(): Long = this ?: 0L
}
