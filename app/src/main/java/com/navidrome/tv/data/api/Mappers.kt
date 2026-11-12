package com.navidrome.tv.data.api

import com.navidrome.tv.model.Album
import com.navidrome.tv.model.Artist
import com.navidrome.tv.model.Playlist
import com.navidrome.tv.model.Song

fun SongDto.toSong() = Song(
    id = id,
    title = title,
    artist = artist ?: "未知歌手",
    artistId = artistId,
    album = album ?: "未知专辑",
    albumId = albumId,
    durationSec = duration ?: 0,
    track = track,
    coverArt = coverArt,
    suffix = suffix,
    bitRate = bitRate,
)

fun AlbumDto.toAlbum() = Album(
    id = id,
    name = name,
    artist = artist ?: "未知歌手",
    artistId = artistId,
    coverArt = coverArt,
    year = year,
    songCount = songCount ?: song.size,
    durationSec = duration ?: 0,
)

fun ArtistId3.toArtist() = Artist(id = id, name = name, coverArt = coverArt, albumCount = albumCount ?: 0)

fun PlaylistDto.toPlaylist() = Playlist(
    id = id,
    name = name,
    coverArt = coverArt,
    songCount = songCount ?: 0,
    durationSec = duration ?: 0,
)
