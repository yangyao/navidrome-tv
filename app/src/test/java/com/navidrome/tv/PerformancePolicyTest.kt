package com.navidrome.tv

import com.navidrome.tv.playback.QueueWindow
import com.navidrome.tv.util.BoundedCache
import com.navidrome.tv.util.ScreenCache
import com.navidrome.tv.util.mergeDistinct
import org.junit.Assert.*
import org.junit.Test

class PerformancePolicyTest {
    @Test fun windowsContainEveryTrackAndNeverExceedCap() {
        for (total in listOf(1, 100, 101, 300, 301, 1000)) {
            for (index in 0 until total) {
                val window = QueueWindow.around(index, total)
                assertTrue(window.contains(index))
                assertTrue(window.size <= 300)
                assertTrue(window.endExclusive <= total)
                if (index > 0) assertTrue(window.contains(index - 1))
                if (index < total - 1) assertTrue(window.contains(index + 1))
            }
        }
    }
    @Test fun remoteFetchIsIndependentOfLocalWindowAndEndsOnlyWhenExhausted() {
        assertFalse(QueueWindow.needsRemote(0, 100, false))
        assertTrue(QueueWindow.needsRemote(69, 100, false))
        assertTrue(QueueWindow.needsRemote(99, 100, false))
        assertFalse(QueueWindow.needsRemote(99, 100, true))
    }
    @Test fun leastRecentlyUsedEntriesAreEvictedAndReadsDoNotExtendTtl() {
        var now = 0L
        val cache = BoundedCache<String, Int>(2, 100) { now }
        cache.put("a", 1); cache.put("b", 2)
        assertEquals(1, cache["a"])
        cache.put("c", 3)
        assertNull(cache["b"])
        now = 99
        assertEquals(1, cache["a"])
        now = 100
        assertNull(cache["a"])
    }
    @Test fun screenEvictionKeepsLoadedFlagAndDataTogether() {
        val cache = ScreenCache(2)
        val original = cache.getOrPut("artist:a:data") { Any() }
        cache.getOrPut("artist:a:loaded") { true }
        cache.getOrPut("artist:b:data") { Any() }
        assertSame(original, cache.getOrPut("artist:a:data") { Any() })
        cache.getOrPut("album:c:data") { Any() }
        assertSame(original, cache.getOrPut("artist:a:data") { Any() })
        cache.getOrPut("album:d:data") { Any() }
        cache.getOrPut("album:e:data") { Any() }
        assertFalse(cache.getOrPut("artist:a:loaded") { false })
        assertNotSame(original, cache.getOrPut("artist:a:data") { Any() })
    }
    @Test fun mergingPreservesQueueOrderAndDeduplicatesWithinIncomingBatch() {
        assertEquals(listOf("a", "a", "b", "c", "d"),
            mergeDistinct(listOf("a", "a", "b"), listOf("b", "c", "c", "d")) { it })
    }
}
