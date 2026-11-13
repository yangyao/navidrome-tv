package com.navidrome.tv.playback

import com.navidrome.tv.R

enum class SongPlaybackAction(val labelRes: Int) {
    PLAY(R.string.action_play), PAUSE(R.string.action_pause), RESUME(R.string.action_resume);

    companion object {
        fun forSong(songId: String, state: PlaybackState): SongPlaybackAction = when {
            state.currentSong?.id != songId -> PLAY
            state.playing -> PAUSE
            else -> RESUME
        }
    }
}
