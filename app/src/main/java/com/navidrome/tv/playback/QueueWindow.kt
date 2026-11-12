package com.navidrome.tv.playback

/** Pure index policy shared by the player and boundary tests. endExclusive is logical. */
data class QueueWindow(val start: Int, val endExclusive: Int) {
    val size get() = endExclusive - start
    fun contains(index: Int) = index in start until endExclusive
    companion object {
        const val MAX_ITEMS = 300
        const val LOOK_BEHIND = 50
        const val PREFETCH_REMAINING = 30
        fun around(index: Int, total: Int): QueueWindow {
            require(total > 0 && index in 0 until total)
            val start = (index - LOOK_BEHIND).coerceAtLeast(0)
            return QueueWindow(start, (start + MAX_ITEMS).coerceAtMost(total))
        }
        fun needsRemote(index: Int, total: Int, exhausted: Boolean): Boolean =
            !exhausted && total - index <= PREFETCH_REMAINING + 1
    }
}
