package com.navidrome.tv.playback

enum class PlaybackMode(val label: String) {
    ORDERED("顺序播放"), SHUFFLE("随机播放"), REPEAT_ONE("单曲循环"), REPEAT_ALL("列表循环");
    companion object {
        fun from(state: PlaybackState): PlaybackMode = when {
            state.repeatOne -> REPEAT_ONE
            state.repeatAll -> REPEAT_ALL
            state.shuffle -> SHUFFLE
            else -> ORDERED
        }
    }
}
