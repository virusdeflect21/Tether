package dev.tether.core.model

data class LyricsLine(
    val timeMs: Long?,
    val text: String,
)

/**
 * Lyrics as stored in the library. [synced] is only true when every displayed line
 * came from real timestamps in the source; plain lyrics are never animated as if
 * they were timed.
 */
data class LyricsDocument(
    val trackId: String,
    val lines: List<LyricsLine>,
    val raw: String,
    val synced: Boolean,
    val sourceLabel: String,
    val providerId: String,
    val attribution: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val externalUrl: String? = null,
    val local: Boolean = false,
) {
    val plainText: String
        get() = lines.joinToString("\n") { it.text }
}

enum class LyricsLoadState {
    IDLE,
    LOADING,
    AVAILABLE,
    NOT_FOUND,
    OFFLINE,
    ERROR,
}

data class LyricsViewState(
    val state: LyricsLoadState = LyricsLoadState.IDLE,
    val document: LyricsDocument? = null,
    val message: String? = null,
    val canRetry: Boolean = true,
)
