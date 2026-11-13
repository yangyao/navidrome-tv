package com.navidrome.tv.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String?,
    val album: String,
    val albumId: String?,
    val durationSec: Int,
    val track: Int?,
    val coverArt: String?,
    val suffix: String?,
    val bitRate: Int?,
)

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String?,
    val coverArt: String?,
    val year: Int?,
    val songCount: Int,
    val durationSec: Int,
)

data class Artist(
    val id: String,
    val name: String,
    val coverArt: String?,
    val albumCount: Int,
)

data class Playlist(
    val id: String,
    val name: String,
    val coverArt: String?,
    val songCount: Int,
    val durationSec: Int,
)

/** 播放來源。點歌會替換當前來源；「下一首播放／加入仔列」不改變來源。 */
    /**
     * 供快取／快照与「继续播放」使用的来源名。具名来源（歌手／专辑／歌单）使用用户数据，
     * 固定来源使用英文规范名；界面显示时按 sourceKey 解析为本地化文案（见 MainActivity）。
     */
    sealed interface QueueSource {
        val key: String
        val displayName: String

        /** 所有歌曲：依清單順序分頁載入。 */
        data class AllSongs(val loadedIds: List<String> = emptyList()) : QueueSource {
            override val key get() = "all_songs"
            override val displayName get() = "All songs"
        }

    data class AlbumSongs(val albumId: String, val albumName: String) : QueueSource {
        override val key get() = "album_$albumId"
        override val displayName get() = albumName
    }

    data class ArtistSongs(val artistId: String, val artistName: String) : QueueSource {
        override val key get() = "artist_$artistId"
        override val displayName get() = artistName
    }

    data class PlaylistSongs(val playlistId: String, val playlistName: String) : QueueSource {
        override val key get() = "playlist_$playlistId"
        override val displayName get() = playlistName
    }

    data object Starred : QueueSource {
        override val key get() = "starred"
        override val displayName get() = "Favorites"
    }

    /** 全庫隨機播放：分批取得候選，以會話已播 ID 去重。 */
    data class Random(val seed: Long = System.nanoTime()) : QueueSource {
        override val key get() = "random_$seed"
        override val displayName get() = "Shuffle"
    }

    /** 手動插入的歌曲（下一首播放／加入仔列）。 */
    data class Manual(val songs: List<Song>) : QueueSource {
        override val key get() = "manual"
        override val displayName get() = "Added manually"
    }

    companion object {
        fun fromKey(key: String, name: String): QueueSource = when {
            key == "all_songs" -> AllSongs()
            key == "starred" -> Starred
            key.startsWith("artist_") -> ArtistSongs(key.removePrefix("artist_"), name)
            key.startsWith("album_") -> AlbumSongs(key.removePrefix("album_"), name)
            key.startsWith("playlist_") -> PlaylistSongs(key.removePrefix("playlist_"), name)
            key.startsWith("random_") -> Random(key.removePrefix("random_").toLongOrNull() ?: 0L)
            else -> AllSongs()
        }
    }
}
