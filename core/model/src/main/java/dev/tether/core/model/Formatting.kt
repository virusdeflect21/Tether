package dev.tether.core.model

import java.util.Locale

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatDurationLong(ms: Long): String {
    if (ms <= 0) return "0 min"
    val minutes = ms / 60_000
    return when {
        minutes >= 60 -> {
            val hours = minutes / 60
            val rest = minutes % 60
            if (rest == 0L) "$hours hr" else "$hours hr $rest min"
        }
        else -> "$minutes min"
    }
}

fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val formatter = java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    return formatter.format(java.util.Date(timestamp))
}

fun normalizeForSearch(value: String): String =
    value.lowercase(Locale.ROOT)
        .replace(Regex("[\\p{Punct}&&[^&]]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

fun matchesQuery(track: Track, query: String): Boolean {
    val needle = normalizeForSearch(query)
    if (needle.isBlank()) return true
    val haystack = normalizeForSearch(
        listOf(track.title, track.artist, track.album, track.albumArtist, track.genre.orEmpty(), track.folder.orEmpty())
            .joinToString(" "),
    )
    return needle.split(" ").all { it in haystack }
}

fun groupAlbums(tracks: List<Track>): List<AlbumSummary> = tracks
    .groupBy { it.albumKey }
    .map { (key, items) ->
        val first = items.first()
        AlbumSummary(
            key = key,
            title = first.displayAlbum,
            artist = first.albumArtist.ifBlank { first.displayArtist },
            artworkUri = items.firstNotNullOfOrNull { it.artworkUri },
            trackCount = items.size,
            durationMs = items.sumOf { it.durationMs },
            sourceId = first.sourceId,
            year = items.firstNotNullOfOrNull { it.year },
        )
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

fun groupArtists(tracks: List<Track>): List<ArtistSummary> = tracks
    .groupBy { it.artistKey }
    .map { (key, items) ->
        ArtistSummary(
            key = key,
            name = key,
            artworkUri = items.firstNotNullOfOrNull { it.artworkUri },
            trackCount = items.size,
            albumCount = items.map { it.albumKey }.distinct().size,
            sourceId = items.first().sourceId,
        )
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

fun cleanProviderLabel(text: String): String = text
    .replace(Regex("\\[.*?]"), "")
    .replace(Regex("\\(official (music )?video\\)", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s+"), " ")
    .trim()
