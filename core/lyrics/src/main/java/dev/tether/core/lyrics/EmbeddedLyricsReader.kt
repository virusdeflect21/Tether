package dev.tether.core.lyrics

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Reads embedded lyrics from MP3 ID3v2 USLT frames and FLAC Vorbis comments.
 *
 * Android's MediaMetadataRetriever does not expose lyrics. We only read metadata
 * blocks and never modify the user's files. Unsupported containers return null,
 * which the UI reports as "no embedded lyrics".
 */
object EmbeddedLyricsReader {
    private const val MAX_METADATA_BYTES = 4 * 1024 * 1024

    fun read(file: File): String? {
        if (!file.isFile) return null
        return runCatching {
            when (file.extension.lowercase()) {
                "mp3", "mp2" -> file.inputStream().use(::readMp3)
                "flac" -> file.inputStream().use(::readFlac)
                else -> file.inputStream().use(::readGeneric)
            }?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun read(input: InputStream, mimeType: String?, name: String?): String? = runCatching {
        val lowerName = name.orEmpty().lowercase()
        val lowerMime = mimeType.orEmpty().lowercase()
        when {
            lowerMime.contains("mpeg") || lowerMime.contains("mp3") || lowerName.endsWith(".mp3") ->
                readMp3(input)
            lowerMime.contains("flac") || lowerName.endsWith(".flac") -> readFlac(input)
            else -> readGeneric(input)
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun readGeneric(input: InputStream): String? {
        val bytes = input.readLimited(MAX_METADATA_BYTES)
        return when {
            bytes.size >= 3 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte() ->
                parseId3(bytes) ?: parseVorbisInBytes(bytes)
            bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("fLaC".toByteArray()) -> parseFlac(bytes)
            else -> null
        }
    }

    private fun readMp3(input: InputStream): String? {
        val bytes = input.readLimited(MAX_METADATA_BYTES)
        return parseId3(bytes) ?: parseFlac(bytes)
    }

    private fun readFlac(input: InputStream): String? {
        val bytes = input.readLimited(MAX_METADATA_BYTES)
        return parseFlac(bytes) ?: parseId3(bytes)
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (total < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - total))
            if (count <= 0) break
            out.write(buffer, 0, count)
            total += count
        }
        return out.toByteArray()
    }

    private fun parseId3(bytes: ByteArray): String? {
        if (bytes.size < 10 || bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return null
        }
        val version = bytes[3].toInt() and 0xff
        val flags = bytes[5].toInt() and 0xff
        val tagSize = syncSafe(bytes, 6)
        val end = minOf(bytes.size, 10 + tagSize)
        var position = 10
        if (flags and 0x40 != 0 && version >= 3) {
            if (position + 4 > end) return null
            val extended = if (version == 4) syncSafe(bytes, position) else readInt(bytes, position)
            position += extended
        }
        val idLength = if (version == 2) 3 else 4
        val headerLength = if (version == 2) 6 else 10
        while (position + headerLength <= end) {
            val id = String(bytes, position, idLength, Charsets.ISO_8859_1)
            if (id.all { it == '\u0000' }) break
            val size = if (version == 2) {
                ((bytes[position + 3].toInt() and 0xff) shl 16) or
                    ((bytes[position + 4].toInt() and 0xff) shl 8) or
                    (bytes[position + 5].toInt() and 0xff)
            } else if (version == 4) {
                syncSafe(bytes, position + 4)
            } else {
                readInt(bytes, position + 4)
            }
            val dataStart = position + headerLength
            val dataEnd = minOf(end, dataStart + size)
            if (size < 0 || dataStart >= dataEnd) break
            if (id == "USLT" || (version == 2 && id == "ULT")) {
                decodeUslt(bytes, dataStart, dataEnd)?.let { return it }
            }
            position = dataStart + size
        }
        return null
    }

    private fun decodeUslt(bytes: ByteArray, start: Int, end: Int): String? {
        if (end - start < 4) return null
        val encoding = bytes[start].toInt() and 0xff
        var pos = start + 4 // encoding + 3-byte language
        // Skip the content descriptor (null-terminated in the frame's encoding).
        pos = when (encoding) {
            1, 2 -> {
                var index = pos
                while (index + 1 < end) {
                    if (bytes[index].toInt() == 0 && bytes[index + 1].toInt() == 0) {
                        index += 2
                        break
                    }
                    index += 2
                }
                index
            }
            else -> {
                var index = pos
                while (index < end && bytes[index].toInt() != 0) index++
                if (index < end) index++
                index
            }
        }
        if (pos >= end) return null
        val charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(bytes, pos, end - pos, charset)
            .trim('\u0000', '\uFEFF', '\r', '\n', ' ')
            .takeIf { it.isNotBlank() }
    }

    private fun parseFlac(bytes: ByteArray): String? {
        if (bytes.size < 4 || !bytes.copyOfRange(0, 4).contentEquals("fLaC".toByteArray())) return null
        var position = 4
        while (position + 4 <= bytes.size) {
            val header = bytes[position].toInt() and 0xff
            val last = header and 0x80 != 0
            val type = header and 0x7f
            val length = ((bytes[position + 1].toInt() and 0xff) shl 16) or
                ((bytes[position + 2].toInt() and 0xff) shl 8) or
                (bytes[position + 3].toInt() and 0xff)
            val start = position + 4
            val end = minOf(bytes.size, start + length)
            if (start + length > bytes.size) break
            if (type == 4) {
                parseVorbisComment(bytes, start, end)?.let { return it }
            }
            if (last) break
            position = end
        }
        return null
    }

    private fun parseVorbisInBytes(bytes: ByteArray): String? {
        val marker = "LYRICS=".toByteArray()
        val index = indexOf(bytes, marker)
        if (index < 0) return null
        var end = index
        while (end < bytes.size && bytes[end] != 0.toByte() && bytes[end] != '\n'.code.toByte()) end++
        return String(bytes, index + marker.size, end - index - marker.size, Charsets.UTF_8).trim()
    }

    private fun parseVorbisComment(bytes: ByteArray, start: Int, end: Int): String? {
        var position = start
        if (position + 4 > end) return null
        val vendorLength = readIntLe(bytes, position)
        position += 4 + vendorLength
        if (position + 4 > end) return null
        val count = readIntLe(bytes, position)
        position += 4
        repeat(count) {
            if (position + 4 > end) return null
            val length = readIntLe(bytes, position)
            position += 4
            if (position + length > end) return null
            val comment = String(bytes, position, length, Charsets.UTF_8)
            position += length
            val key = comment.substringBefore('=', "")
            if (key.equals("LYRICS", true) || key.equals("UNSYNCEDLYRICS", true) || key.equals("SYNCEDLYRICS", true)) {
                return comment.substringAfter('=', "").trim().takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun syncSafe(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7f) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7f) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7f) shl 7) or
            (bytes[offset + 3].toInt() and 0x7f)

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun readIntLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
