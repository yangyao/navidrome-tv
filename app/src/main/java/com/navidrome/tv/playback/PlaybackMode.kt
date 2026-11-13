package com.navidrome.tv.playback

import com.navidrome.tv.R

enum class PlaybackMode(val labelRes: Int) {
    ORDERED(R.string.playback_ordered), SHUFFLE(R.string.shuffle_play), REPEAT_ONE(R.string.playback_repeat_one), REPEAT_ALL(R.string.playback_repeat_all);
    companion object {
        fun from(state: PlaybackState): PlaybackMode = when {
            state.repeatOne -> REPEAT_ONE
            state.repeatAll -> REPEAT_ALL
            state.shuffle -> SHUFFLE
            else -> ORDERED
        }
    }
}
