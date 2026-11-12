package com.navidrome.tv.data.api

import com.navidrome.tv.data.local.ConnectionSettings
import com.navidrome.tv.model.Album
import com.navidrome.tv.model.Song
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume

/** 認證失敗：帳密錯誤或權限失效（Subsonic error code 40）。 */
class AuthException(message: String) : Exception(message)

/** 伺服器不可達、超時或網路錯誤。 */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 伺服器回應其他錯誤（如用戶無權限）。 */
class SubsonicApiException(val code: Int, message: String) : Exception(message)

/** 回應無法解碼或缺少欄位。 */
class DecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

object SubsonicAuth {
    fun salt(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return buildString { repeat(12) { append(chars.random()) } }
    }

    fun token(password: String, salt: String): String =
        md5Hex(password + salt)

    fun md5Hex(input: String): String =
        java.security.MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/**
 * 建立 Subsonic 請求查詢參數。基礎認證 u/t/s/v/c/f：t = md5(password + salt)。
 * URL 子路徑（如 https://host/music/）保留在 baseUrl 中，拼接時不會丟掉。
 */
class SubsonicClient(
    private val settingsProvider: () -> ConnectionSettings?,
) {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    private val http by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .readTimeout(java.time.Duration.ofSeconds(20))
            .build()
    }

    private fun current(): ConnectionSettings =
        settingsProvider() ?: throw AuthException("尚未设定服务器连接")

    fun authParams(password: String): Map<String, String> {
        val salt = SubsonicAuth.salt()
        return mapOf(
            "u" to current().username,
            "t" to SubsonicAuth.token(password, salt),
            "s" to salt,
            "v" to API_VERSION,
            "c" to CLIENT,
            "f" to "json",
        )
    }

    private var cachedAuth: Pair<ConnectionSettings, Map<String, String>>? = null

    /** Only derived authentication is retained; changing settings replaces the session. */
    @Synchronized private fun sessionAuth(settings: ConnectionSettings): Map<String, String> {
        cachedAuth?.takeIf { it.first == settings }?.let { return it.second }
        val salt = SubsonicAuth.salt()
        val params = mapOf("u" to settings.username,
            "t" to SubsonicAuth.token(settings.passwordPlain(), salt), "s" to salt,
            "v" to API_VERSION, "c" to CLIENT, "f" to "json")
        cachedAuth = settings to params
        return params
    }

    @Synchronized fun clearSession() { cachedAuth = null }
    fun cacheScope(): String {
        val settings = current()
        return SubsonicAuth.md5Hex("${settings.serverUrl}|${settings.username}|${settings.encryptedPassword}")
    }

    private suspend fun call(endpoint: String, extra: Map<String, String>): SubBody {
        val settings = current()
        val parsed = settings.serverUrl.trimEnd('/').toHttpUrlOrNull()
            ?: throw NetworkException("服务器地址無效：${settings.serverUrl}")
        val builder = okhttp3.HttpUrl.Builder()
            .scheme(parsed.scheme)
            .host(parsed.host)
            .port(parsed.port)
            .addEncodedPathSegments((parsed.encodedPath.trimEnd('/') + "/rest/$endpoint").trimStart('/'))
        sessionAuth(settings).forEach { (k, v) -> builder.addQueryParameter(k, v) }
        extra.forEach { (k, v) -> if (v.isNotEmpty()) builder.addQueryParameter(k, v) }
        val request = okhttp3.Request.Builder().url(builder.build()).build()
        android.util.Log.i("SubsonicClient", "GET $endpoint")
        val text = try {
            http.newCall(request).awaitText()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SubsonicClient", "call $endpoint failed: ${e.javaClass.simpleName}: ${e.message}", e)

            throw NetworkException("無法连上服务器", e)
        }
        val envelope = try {
            json.decodeFromString(SubsonicEnvelope.serializer(), text)
        } catch (e: Exception) {
            throw DecodeException("回应无法解码: ${text.take(300)}", e)
        }
        val body = envelope.body
        body.error?.let { err ->
            if (err.code == 40) throw AuthException(err.message)
            throw SubsonicApiException(err.code, err.message)
        }
        if (body.status != "ok") throw SubsonicApiException(0, "未知服务器錯误")
        return body
    }

    // ---- 端點 ----

    suspend fun ping(): Unit = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        call("ping", emptyMap())
    }

    /** 所有歌曲：search3 空查詢、artistCount=0、albumCount=0，以 songCount/songOffset 分頁。 */
    suspend fun songsPage(offset: Int, count: Int): List<Song> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            call(
                "search3",
                mapOf(
                    "query" to "",
                    "artistCount" to "0",
                    "albumCount" to "0",
                    "songCount" to count.toString(),
                    "songOffset" to offset.toString(),
                ),
            ).searchResult3?.song?.map { it.toSong() } ?: emptyList()
        }

    suspend fun randomSongs(count: Int): List<Song> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            call("getRandomSongs", mapOf("size" to count.toString()))
                .randomSongs?.song?.map { it.toSong() } ?: emptyList()
        }

    suspend fun starred(): Pair<List<Song>, List<Album>> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val s = call("getStarred2", emptyMap()).starred2
            Pair(s?.song?.map { it.toSong() } ?: emptyList(), s?.album?.map { it.toAlbum() } ?: emptyList())
        }

    suspend fun albums(sorting: String, offset: Int, count: Int): List<Album> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            call(
                "getAlbumList2",
                mapOf("type" to sorting, "size" to count.toString(), "offset" to offset.toString()),
            ).albumList2?.album?.map { it.toAlbum() } ?: emptyList()
        }

    suspend fun albumDetails(id: String): Pair<Album, List<Song>> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val album = call("getAlbum", mapOf("id" to id)).album
                ?: throw DecodeException("找不到专辑 $id")
            album.toAlbum() to (album.song?.map { it.toSong() } ?: emptyList())
        }

    /** getArtists：無分頁端點，一次取得全部歌手。 */
    suspend fun artists(): List<com.navidrome.tv.model.Artist> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            call("getArtists", emptyMap()).artists?.indexes
                ?.flatMap { it.artist }
                ?.map { com.navidrome.tv.model.Artist(it.id, it.name, it.coverArt, it.albumCount ?: 0) }
                ?: emptyList()
        }

    suspend fun artistAlbums(artistId: String): List<Album> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            call("getArtist", mapOf("id" to artistId)).artist?.album?.map { it.toAlbum() } ?: emptyList()
        }

    suspend fun playlists(): List<com.navidrome.tv.model.Playlist> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            call("getPlaylists", emptyMap()).playlists?.playlist
                ?.map { com.navidrome.tv.model.Playlist(it.id, it.name, it.coverArt, it.songCount ?: 0, it.duration ?: 0) }
                ?: emptyList()
        }

    suspend fun playlistSongs(id: String): List<Song> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            call("getPlaylist", mapOf("id" to id)).playlist?.entry?.map { it.toSong() } ?: emptyList()
        }

    suspend fun scrobble(id: String, submission: Boolean) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            call("scrobble", mapOf("id" to id, "submission" to submission.toString()))
        }

    /** 串流 URL：不以持久化方式保存，播放時重新生成。 */
    fun streamUrl(songId: String, maxBitRate: Int? = null, format: String? = null): String {
        val settings = current()
        val parsed = settings.serverUrl.trimEnd('/').toHttpUrlOrNull()
            ?: throw NetworkException("服务器地址無效")
        val b = okhttp3.HttpUrl.Builder()
            .scheme(parsed.scheme).host(parsed.host).port(parsed.port)
            .addEncodedPathSegments((parsed.encodedPath.trimEnd('/') + "/rest/stream").trimStart('/'))
            .addQueryParameter("id", songId)
        sessionAuth(settings).forEach { (k, v) -> b.addQueryParameter(k, v) }
        maxBitRate?.let { b.addQueryParameter("maxBitRate", it.toString()) }
        format?.let { b.addQueryParameter("format", it) }
        return b.build().toString()
    }

    fun coverArtUrl(coverArtId: String, size: Int? = null): String {
        val settings = current()
        val parsed = settings.serverUrl.trimEnd('/').toHttpUrlOrNull() ?: return ""
        val b = okhttp3.HttpUrl.Builder()
            .scheme(parsed.scheme).host(parsed.host).port(parsed.port)
            .addEncodedPathSegments((parsed.encodedPath.trimEnd('/') + "/rest/getCoverArt").trimStart('/'))
            .addQueryParameter("id", coverArtId)
        sessionAuth(settings).forEach { (k, v) -> b.addQueryParameter(k, v) }
        size?.let { b.addQueryParameter("size", it.toString()) }
        return b.build().toString()
    }

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT = "navidrome-tv"
    }
}

private fun String.toHttpUrlOrNull(): okhttp3.HttpUrl? = runCatching {
    okhttp3.HttpUrl.Companion.run { this@toHttpUrlOrNull.toHttpUrl() }
}.getOrNull()

/** Keep the continuation pending through body consumption so cancellation closes the socket. */
private suspend fun okhttp3.Call.awaitText(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            val result = runCatching {
                response.use {
                    if (!continuation.isActive) return
                    if (it.code == 401) throw AuthException("服务器拒絕存取（401）")
                    if (!it.isSuccessful) throw NetworkException("服务器回应 ${it.code}")
                    it.body?.string() ?: throw DecodeException("空回应")
                }
            }
            if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
        }
    })
}
