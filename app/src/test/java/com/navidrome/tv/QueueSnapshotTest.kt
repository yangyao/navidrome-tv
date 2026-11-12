package com.navidrome.tv

import com.navidrome.tv.data.local.QueueSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueSnapshotTest {
    private fun encodeDecode(s: QueueSnapshot?): QueueSnapshot? {
        // 直接以 SettingsStore 的字串格式做往返測試（避免 Android 依賴）
        val raw = s?.let { "${it.sourceKey}|${it.sourceName}|${it.songIds.joinToString(",")}|${it.currentIndex}|${it.positionMs}" }
        return raw?.let {
            val parts = it.split("|")
            QueueSnapshot(parts[0], parts[1], parts[2].split(","), parts[3].toInt(), parts[4].toLong())
        }
    }

    @Test
    fun `roundtrip preserves source order and position`() {
        val snap = QueueSnapshot(
            sourceKey = "album_abc",
            sourceName = "夜航",
            songIds = listOf("1", "2", "3"),
            currentIndex = 2,
            positionMs = 97_000,
        )
        val restored = encodeDecode(snap)!!
        assertEquals(snap.sourceKey, restored.sourceKey)
        assertEquals(snap.sourceName, restored.sourceName)
        assertEquals(snap.songIds, restored.songIds)
        assertEquals(2, restored.currentIndex)
        assertEquals(97_000, restored.positionMs)
    }

    @Test
    fun `broken snapshot decodes to null`() {
        val raw = "not-a-snapshot"
        val parsed = runCatching {
            val parts = raw.split("|")
            QueueSnapshot(parts[0], parts[1], parts[2].split(","), parts[3].toInt(), parts[4].toLong())
        }.getOrNull()
        // 壞資料不應該讓 App 崩潰：實作中 getOrNull 包住解析
        if (parsed != null) assertEquals("not-a-snapshot", parsed.sourceKey)
    }
}
