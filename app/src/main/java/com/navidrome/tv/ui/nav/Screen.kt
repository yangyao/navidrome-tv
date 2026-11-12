package com.navidrome.tv.ui.nav

/** 畫面堆疊：Back 依序關閉覆蓋層、返回上頁、根頁交還系統。 */
sealed interface Screen {
    data class Songs(val autoResumePrompt: Boolean = true) : Screen
    data object Artists : Screen
    data class ArtistDetail(val artistId: String, val name: String) : Screen
    data object Albums : Screen
    data class AlbumDetail(val albumId: String) : Screen
    data object Playlists : Screen
    data class PlaylistDetail(val playlistId: String, val name: String) : Screen
    data object Favorites : Screen
    data object Settings : Screen
    data object Login : Screen
    data object NowPlaying : Screen
    data object Queue : Screen
}
