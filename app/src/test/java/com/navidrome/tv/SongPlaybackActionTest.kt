package com.navidrome.tv

import com.navidrome.tv.model.Song
import com.navidrome.tv.playback.PlaybackState
import com.navidrome.tv.playback.SongPlaybackAction
import org.junit.Assert.assertEquals
import org.junit.Test

class SongPlaybackActionTest {
    private val song = Song("current", "Song", "Artist", null, "Album", null, 60, 1, null, "wav", 128)
    @Test fun actionFollowsTheSelectedSongAndLivePlaybackState() {
        assertEquals(SongPlaybackAction.PLAY, SongPlaybackAction.forSong(song.id, PlaybackState()))
        val playing = PlaybackState(currentSong = song, playing = true)
        assertEquals(SongPlaybackAction.PAUSE, SongPlaybackAction.forSong(song.id, playing))
        assertEquals(SongPlaybackAction.RESUME, SongPlaybackAction.forSong(song.id, playing.copy(playing = false)))
        assertEquals(SongPlaybackAction.PLAY, SongPlaybackAction.forSong("other", playing))
        assertEquals(SongPlaybackAction.PLAY, SongPlaybackAction.forSong("other", playing.copy(playing = false)))
    }
}
