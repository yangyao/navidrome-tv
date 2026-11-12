package com.navidrome.tv

import com.navidrome.tv.playback.PlaybackMode
import com.navidrome.tv.playback.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackModeTest {
    @Test fun modeReflectsPlayerStateIncludingSingleRepeat() {
        assertEquals(PlaybackMode.ORDERED, PlaybackMode.from(PlaybackState()))
        assertEquals(PlaybackMode.SHUFFLE, PlaybackMode.from(PlaybackState(shuffle = true)))
        assertEquals(PlaybackMode.REPEAT_ONE, PlaybackMode.from(PlaybackState(repeatOne = true)))
        assertEquals(PlaybackMode.REPEAT_ALL, PlaybackMode.from(PlaybackState(repeatAll = true)))
    }
}
