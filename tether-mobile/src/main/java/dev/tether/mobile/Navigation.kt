package dev.tether.mobile

sealed interface MobileDestination {
    data object Home : MobileDestination
    data object Explore : MobileDestination
    data object Library : MobileDestination
    data object Downloads : MobileDestination
    data object Playlists : MobileDestination
    data object Search : MobileDestination
    data object Settings : MobileDestination
    data object Player : MobileDestination
    data object Queue : MobileDestination
    data object Lyrics : MobileDestination
    data object Diagnostics : MobileDestination
    data object About : MobileDestination
    data object Licenses : MobileDestination
    data class Collection(
        val kind: CollectionKind,
        val key: String,
        val title: String,
        val subtitle: String = "",
        val artworkUri: String? = null,
    ) : MobileDestination
    data class PlaylistDetail(val playlistId: Long) : MobileDestination
}

enum class CollectionKind { ALBUM, ARTIST, GENRE, FOLDER, FAVORITES, RECENT, DOWNLOADED }

fun MobileDestination.isRootTab(): Boolean = when (this) {
    MobileDestination.Home,
    MobileDestination.Explore,
    MobileDestination.Library,
    MobileDestination.Downloads,
    MobileDestination.Playlists -> true
    else -> false
}

fun MobileDestination.title(): String = when (this) {
    MobileDestination.Home -> "Home"
    MobileDestination.Explore -> "Explore"
    MobileDestination.Library -> "Library"
    MobileDestination.Downloads -> "Downloads"
    MobileDestination.Playlists -> "Playlists"
    MobileDestination.Search -> "Search"
    MobileDestination.Settings -> "Settings"
    MobileDestination.Player -> "Now playing"
    MobileDestination.Queue -> "Queue"
    MobileDestination.Lyrics -> "Lyrics"
    MobileDestination.Diagnostics -> "Diagnostics"
    MobileDestination.About -> "About Tether"
    MobileDestination.Licenses -> "Licenses & credits"
    is MobileDestination.Collection -> title
    is MobileDestination.PlaylistDetail -> "Playlist"
}
