package com.navidrome.tv.data.api

import com.navidrome.tv.R
import com.navidrome.tv.model.Album
import com.navidrome.tv.model.Artist
import com.navidrome.tv.model.Playlist
import com.navidrome.tv.model.Song
import com.navidrome.tv.util.L10n

fun SongDto.toSong() = Song(
    id = id,
    title = title,
    artist = artist ?: L10n.str(R.string.unknown_artist),
    artistId = artistId,
    album = album ?: L10n.str(R.string.unknown_album),
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
    artist = artist ?: L10n.str(R.string.unknown_artist),
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
