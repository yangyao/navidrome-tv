package com.navidrome.tv.playback

enum class SongPlaybackAction(val label: String) {
    PLAY("播放"), PAUSE("暂停"), RESUME("继续播放");

    companion object {
        fun forSong(songId: String, state: PlaybackState): SongPlaybackAction = when {
            state.currentSong?.id != songId -> PLAY
            state.playing -> PAUSE
            else -> RESUME
        }
    }
}
