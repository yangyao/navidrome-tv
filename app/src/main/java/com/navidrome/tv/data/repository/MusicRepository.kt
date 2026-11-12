package com.navidrome.tv.data.repository

import com.navidrome.tv.data.api.SubsonicClient
import com.navidrome.tv.model.Album
import com.navidrome.tv.model.Artist
import com.navidrome.tv.model.Playlist
import com.navidrome.tv.model.Song
import com.navidrome.tv.util.BoundedCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** 統一的音樂庫資料閘道；畫面與播放服務都從這裡取資料。 */
class MusicRepository(private val client: SubsonicClient) {
    private val albumCache = BoundedCache<String, Pair<Album, List<Song>>>(32, 5 * 60_000L)
    private val albumLocks = Array(16) { Mutex() }
    private val artistLimit = Semaphore(4)

    fun clearCaches() { albumCache.clear(); client.clearSession() }
    fun cacheScope(): String = client.cacheScope()

    suspend fun artworkRequest(context: android.content.Context, id: String, pixels: Int): coil.request.ImageRequest =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val key = "${client.cacheScope()}:cover:$id:$pixels"
            coil.request.ImageRequest.Builder(context.applicationContext)
                .data(client.coverArtUrl(id, pixels)).memoryCacheKey(key).diskCacheKey(key).size(pixels).build()
        }


    suspend fun albumDetails(id: String): Result<Pair<Album, List<Song>>> = runCatchingErrors {
        val key = "${client.cacheScope()}:$id"
        albumLocks[(key.hashCode() and Int.MAX_VALUE) % albumLocks.size].withLock {
            albumCache[key] ?: client.albumDetails(id).also { albumCache.put(key, it) }
        }
    }

    /** Parallel requests are globally limited, while results keep album/track order. */
    suspend fun songsForAlbums(albums: List<Album>): Result<List<Song>> = runCatchingErrors {
        coroutineScope {
            albums.map { album -> async {
                artistLimit.withPermit { albumSongs(album.id).getOrThrow() }
            } }.awaitAll().flatten().distinctBy { it.id }
        }
    }

    suspend fun artistSongs(id: String): Result<List<Song>> = runCatchingErrors {
        songsForAlbums(artistAlbums(id).getOrThrow()).getOrThrow()
    }


    /** 所有歌曲分頁：首批 100 首，之後依瀏覽與播放預取。 */
    suspend fun songsPage(offset: Int, count: Int = 100): Result<List<Song>> =
        runCatchingErrors { client.songsPage(offset, count) }

    suspend fun randomSongs(count: Int = 50): Result<List<Song>> =
        runCatchingErrors { client.randomSongs(count) }

    suspend fun starred(): Result<Pair<List<Song>, List<Album>>> =
        runCatchingErrors { client.starred() }

    suspend fun albumsNewest(offset: Int, count: Int = 40): Result<List<Album>> =
        runCatchingErrors { client.albums("newest", offset, count) }

    suspend fun albumsAlphabetical(offset: Int, count: Int = 40): Result<List<Album>> =
        runCatchingErrors { client.albums("alphabeticalByName", offset, count) }

    suspend fun album(id: String): Result<Album> = albumDetails(id).map { it.first }

    suspend fun albumSongs(id: String): Result<List<Song>> = albumDetails(id).map { it.second }

    suspend fun artists(): Result<List<Artist>> = runCatchingErrors { client.artists() }

    suspend fun artistAlbums(artistId: String): Result<List<Album>> =
        runCatchingErrors { client.artistAlbums(artistId) }

    suspend fun playlists(): Result<List<Playlist>> = runCatchingErrors { client.playlists() }

    suspend fun playlistSongs(id: String): Result<List<Song>> =
        runCatchingErrors { client.playlistSongs(id) }

    suspend fun scrobble(id: String, submission: Boolean) {
        runCatchingErrors { client.scrobble(id, submission) }
    }

    fun streamUrl(songId: String, compat: Boolean): String =
        client.streamUrl(songId, maxBitRate = if (compat) 192 else null, format = if (compat) "mp3" else null)

    fun coverUrl(coverArt: String?, size: Int? = null): String? =
        coverArt?.let { client.coverArtUrl(it, size) }

    private inline fun <T> runCatchingErrors(block: () -> T): Result<T> =
        try { Result.success(block()) } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
}
