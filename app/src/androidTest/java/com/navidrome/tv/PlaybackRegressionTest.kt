package com.navidrome.tv

import android.content.Intent
import coil.imageLoader
import coil.decode.DataSource
import coil.request.SuccessResult
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.navidrome.tv.model.Album
import com.navidrome.tv.model.QueueSource
import com.navidrome.tv.model.Song
import com.navidrome.tv.playback.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Runs the real ExoPlayer + repository against an in-process Subsonic fixture. */
@RunWith(AndroidJUnit4::class)
class PlaybackRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as TvApp
    private lateinit var server: Fixture
    private lateinit var saved: Map<String, *>
    @Before fun setup() {
        saved = app.getSharedPreferences("navidrome_tv", 0).all.toMap()
        server = Fixture()
        app.settings.saveConnection("http://127.0.0.1:${server.port}", "fixture", "fixture-only")
        app.repository.clearCaches()
    }
    @After fun teardown() {
        main { PlaybackService.get()?.stopPlayback(); app.stopService(Intent(app, PlaybackService::class.java)) }
        waitFor { PlaybackService.get() == null }
        val editor = app.getSharedPreferences("navidrome_tv", 0).edit().clear()
        saved.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
        } }
        editor.commit()
        app.repository.clearCaches()
        server.close()
    }
    private fun song(index: Int) = Song("$index", "Song $index", "Fixture", null,
        "Fixture album", "fixture", 60, index, null, "wav", 128)
    private fun batch(count: Int) = (0 until count).map(::song)
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(timeout: Long = 10_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (!predicate()) {
            if (System.currentTimeMillis() >= deadline) fail("Timed out waiting for playback condition")
            Thread.sleep(25)
        }
    }
    private fun at(id: String) = waitFor { PlaybackService.playbackState.value.currentSong?.id == id }
    private fun native(block: (Player) -> Unit) = main {
        val service = PlaybackService.get()!!
        val field = PlaybackService::class.java.getDeclaredField("player").apply { isAccessible = true }
        block(field.get(service) as Player)
    }
    private fun assertBounded() = native { assertTrue("Native playlist exceeded cap", it.mediaItemCount <= 300) }

    @Test fun backgroundThreadCommandsAndThousandTrackWindowWork() {
        // Test thread is not the main thread: this is the original shuffleAll crash path.
        PlaybackAccess.playFrom(app, batch(1000), 0, QueueSource.AlbumSongs("fixture", "Fixture"))
        at("0"); waitFor { PlaybackService.playbackState.value.playing }
        assertBounded()
        PlaybackAccess.jumpTo(app, 299); at("299")
        waitFor {
            var extended = false
            native { extended = it.getMediaItemAt(it.mediaItemCount - 1).mediaId.toInt() > 300 }
            extended
        }
        assertBounded()
        waitFor { PlaybackService.playbackState.value.playing && PlaybackService.progress.value.durationMs > 0 }
        native { it.seekTo(it.duration - 20) }
        at("300") // Natural transition across the original 300-item boundary.
        PlaybackAccess.jumpTo(app, 999); at("999"); assertBounded()
        PlaybackAccess.setPlaybackMode(app, PlaybackMode.REPEAT_ALL)
        waitFor { PlaybackService.playbackState.value.playing && PlaybackService.progress.value.durationMs > 0 }
        native { it.seekTo(it.duration - 20) }
        at("0") // Natural end of the logical queue, rather than looping just the player window.
        PlaybackAccess.previous(app); at("999")
        // Removing an item before the window preserves the current song and logical index.
        PlaybackAccess.removeAt(app, 0)
        waitFor { PlaybackService.playbackState.value.currentIndex == 998 }
        assertEquals("999", PlaybackService.playbackState.value.currentSong?.id)
        PlaybackAccess.playNext(app, song(1001))
        waitFor { PlaybackService.playbackState.value.queue.size == 1000 }
        PlaybackAccess.next(app); at("1001"); assertBounded()
        // Burst edits are serialized rather than cancelled by one another.
        PlaybackAccess.addToQueue(app, song(1002))
        PlaybackAccess.addToQueue(app, song(1003))
        waitFor { PlaybackService.playbackState.value.queue.size == 1002 }
        assertEquals(listOf("1002", "1003"), PlaybackService.playbackState.value.queue.takeLast(2).map { it.id })
    }

    @Test fun allSongsFetchesFollowingOffsetsAndRandomFetchesAnotherBatch() {
        PlaybackAccess.playFrom(app, batch(100), 0, QueueSource.AllSongs())
        at("0")
        for (last in listOf(99, 199, 299)) {
            PlaybackAccess.jumpTo(app, last); at("$last")
            waitFor { PlaybackService.playbackState.value.queue.size >= last + 101 }
            assertBounded()
        }
        assertEquals(listOf(100, 200, 300), server.offsets.toList())
        PlaybackAccess.next(app); at("300")
        PlaybackAccess.playFrom(app, batch(50), 0, QueueSource.Random())
        at("0")
        PlaybackAccess.jumpTo(app, 49); at("49")
        waitFor { PlaybackService.playbackState.value.queue.size == 100 }
        PlaybackAccess.next(app); at("50")
    }

    @Test fun staleFetchCannotAppendToReplacementQueueAndProgressDoesNotReemitLibraryState() {
        server.searchDelay = 350
        PlaybackAccess.playFrom(app, batch(100), 0, QueueSource.AllSongs())
        at("0")
        PlaybackAccess.jumpTo(app, 99); at("99")
        waitFor { server.offsets.isNotEmpty() }
        PlaybackAccess.playFrom(app, listOf(song(9999)), 0, QueueSource.Manual(listOf(song(9999))))
        at("9999")
        waitFor { PlaybackService.playbackState.value.playing }
        val state = PlaybackService.playbackState.value
        val position = PlaybackService.progress.value.positionMs
        Thread.sleep(1000)
        assertSame(state, PlaybackService.playbackState.value)
        assertTrue(PlaybackService.progress.value.positionMs > position)
        assertEquals(1, PlaybackService.playbackState.value.queue.size)
    }

    @Test fun repositoryCombinesAlbumRequestsAndLimitsParallelism() = runBlocking {
        val album = app.repository.albumDetails("same").getOrThrow()
        assertEquals(1, album.second.size)
        app.repository.albumSongs("same").getOrThrow()
        assertEquals(1, server.albumCalls["same"]?.get())
        val albums = (1..8).map { Album("$it", "Album $it", "Fixture", null, null, null, 1, 60) }
        assertEquals(8, app.repository.songsForAlbums(albums).getOrThrow().size)
        assertTrue(server.maxAlbums.get() in 2..4)
        assertEquals(app.client.coverArtUrl("same", 100), app.client.coverArtUrl("same", 100))
    }

    @Test fun artworkUsesMemoryThenDiskWithoutRedownloading() = runBlocking {
        val first = app.repository.artworkRequest(app, "art", 80)
        val loader = app.imageLoader
        val network = loader.execute(first) as SuccessResult
        assertEquals(DataSource.NETWORK, network.dataSource)
        val memory = loader.execute(app.repository.artworkRequest(app, "art", 80)) as SuccessResult
        assertEquals(DataSource.MEMORY_CACHE, memory.dataSource)
        loader.memoryCache!!.clear()
        val disk = loader.execute(app.repository.artworkRequest(app, "art", 80)) as SuccessResult
        assertEquals(DataSource.DISK, disk.dataSource)
        assertEquals(1, server.coverCalls.get())
        val oldKey = first.diskCacheKey
        app.settings.saveConnection("http://127.0.0.1:${server.port}", "other-fixture", "other-fixture")
        assertNotEquals(oldKey, app.repository.artworkRequest(app, "art", 80).diskCacheKey)
    }

    @Test fun destroyedServicesCancelAllOwnedCoroutines() {
        repeat(3) {
            PlaybackAccess.playFrom(app, listOf(song(0)), 0, QueueSource.Manual(listOf(song(0))))
            at("0"); waitFor { PlaybackService.playbackState.value.playing }
            val service = PlaybackService.get()!!
            val owned = PlaybackService::class.java.getDeclaredField("scope").apply { isAccessible = true }
                .get(service) as CoroutineScope
            main { service.stopPlayback(); app.stopService(Intent(app, PlaybackService::class.java)) }
            waitFor { PlaybackService.get() == null }
            assertFalse(owned.isActive)
        }
    }

    private class Fixture : AutoCloseable {
        private val socket = ServerSocket(0)
        val port get() = socket.localPort
        private val executor = Executors.newCachedThreadPool()
        val coverCalls = AtomicInteger()
        val offsets = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val albumCalls = ConcurrentHashMap<String, AtomicInteger>()
        private val activeAlbums = AtomicInteger()
        val maxAlbums = AtomicInteger()
        @Volatile var searchDelay = 0L
        private val wav = ByteBuffer.allocate(960044).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(960036); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(960000)
        }.array()
        init {
            executor.execute {
                while (!socket.isClosed) try {
                    val client = socket.accept()
                    executor.execute { runCatching { serve(client) }; client.close() }
                } catch (_: java.io.IOException) { }
            }
        }
        private fun tracks(start: Int, count: Int) = (start until start + count).joinToString(",") {
            """{"id":"$it","title":"Song $it","artist":"Fixture","duration":60,"suffix":"wav"}"""
        }
        private fun serve(client: Socket) {
            val reader = client.getInputStream().bufferedReader()
            val request = reader.readLine() ?: return
            while (!reader.readLine().isNullOrEmpty()) { }
            val uri = URI(request.split(" ")[1])
            val query = uri.rawQuery.orEmpty().split('&').associate {
                val parts = it.split('=', limit = 2); parts[0] to parts.getOrElse(1) { "" }
            }
            if (uri.path.endsWith("/getCoverArt")) {
                coverCalls.incrementAndGet()
                val output = java.io.ByteArrayOutputStream()
                android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(android.graphics.Color.GREEN) }
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                respond(client, output.toByteArray(), "image/png"); return
            }
            if (uri.path.endsWith("/stream")) { respond(client, wav, "audio/wav"); return }
            val payload = when {
                uri.path.endsWith("/search3") -> {
                    val offset = query["songOffset"]!!.toInt(); offsets += offset
                    Thread.sleep(searchDelay)
                    "\"searchResult3\":{\"song\":[${tracks(offset, 100)}]}"
                }
                uri.path.endsWith("/getRandomSongs") -> "\"randomSongs\":{\"song\":[${tracks(50, 50)}]}"
                uri.path.endsWith("/getAlbum") -> {
                    val id = query["id"]!!
                    albumCalls.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
                    val active = activeAlbums.incrementAndGet()
                    maxAlbums.updateAndGet { maxOf(it, active) }
                    try { Thread.sleep(100) } finally { activeAlbums.decrementAndGet() }
                    "\"album\":{\"id\":\"$id\",\"name\":\"Album\",\"song\":[{\"id\":\"$id\",\"title\":\"Song\"}]}"
                }
                else -> "\"version\":\"1.16.1\""
            }
            respond(client, "{\"subsonic-response\":{\"status\":\"ok\",$payload}}".toByteArray(), "application/json")
        }
        private fun respond(client: Socket, data: ByteArray, type: String) {
            client.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(data); flush()
            }
        }
        override fun close() { socket.close(); executor.shutdownNow() }
    }
}
