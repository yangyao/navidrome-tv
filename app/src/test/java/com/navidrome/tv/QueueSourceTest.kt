package com.navidrome.tv

import com.navidrome.tv.model.QueueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSourceTest {
    @Test fun `artist source survives snapshot restoration without becoming all songs`() {
        val source = QueueSource.ArtistSongs("artist_with_underscore", "歌手")
        val restored = QueueSource.fromKey(source.key, source.displayName)
        assertTrue(restored is QueueSource.ArtistSongs)
        assertEquals(source, restored)
    }
}
