package com.navidrome.tv.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicEnvelope(@SerialName("subsonic-response") val body: SubBody)

@Serializable
data class SubBody(
    val status: String = "ok",
    val version: String? = null,
    val error: SubError? = null,
    val searchResult3: SearchResult3? = null,
    val randomSongs: SongList? = null,
    val albumList2: AlbumList2? = null,
    val album: AlbumDto? = null,
    val artists: ArtistsId3? = null,
    val artist: ArtistId3? = null,
    val artistInfo2: ArtistInfo2? = null,
    val playlists: PlaylistsDto? = null,
    val playlist: PlaylistDto? = null,
    val starred2: Starred2? = null,
)

@Serializable
data class SubError(val code: Int, val message: String)

@Serializable
data class SearchResult3(
    val song: List<SongDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val artist: List<ArtistId3> = emptyList(),
)

@Serializable
data class SongList(val song: List<SongDto> = emptyList())

@Serializable
data class AlbumList2(val album: List<AlbumDto> = emptyList())

@Serializable
data class SongDto(
    val id: String,
    val title: String,
    val artist: String? = null,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val duration: Int? = null,
    val track: Int? = null,
    val coverArt: String? = null,
    val suffix: String? = null,
    val bitRate: Int? = null,
)

@Serializable
data class AlbumDto(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val year: Int? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class ArtistsId3(
    @SerialName("index") val indexes: List<ArtistIndex> = emptyList(),
    val ignoredArticles: String? = null,
)

@Serializable
data class ArtistIndex(val name: String, val artist: List<ArtistId3> = emptyList())

@Serializable
data class ArtistId3(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val albumCount: Int? = null,
    val album: List<AlbumDto> = emptyList(),
)

@Serializable
data class ArtistInfo2(
    val biography: String? = null,
    val largeImageUrl: String? = null,
)

@Serializable
data class PlaylistsDto(val playlist: List<PlaylistDto> = emptyList())

@Serializable
data class PlaylistDto(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val entry: List<SongDto> = emptyList(),
)

@Serializable
data class Starred2(
    val song: List<SongDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val artist: List<ArtistId3> = emptyList(),
)
