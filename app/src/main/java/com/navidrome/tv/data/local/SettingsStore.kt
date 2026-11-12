package com.navidrome.tv.data.local

import android.content.Context

data class ConnectionSettings(
    val serverUrl: String,
    val username: String,
    val encryptedPassword: String,
) {
    fun passwordPlain(): String = CredentialCipher.decrypt(encryptedPassword)
}

/** 佇列快照：保存查詢來源、順序、目前歌曲 ID；不保存帶認證的串流 URL。 */
data class QueueSnapshot(
    val sourceKey: String,
    val sourceName: String,
    val songIds: List<String>,
    val currentIndex: Int,
    val positionMs: Long,
)

enum class AudioQuality { ORIGINAL, COMPATIBLE }

/** 設定儲存：SharedPreferences + Keystore 加密密碼（首版單帳號，足夠且無額外依賴）。 */
class SettingsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("navidrome_tv", Context.MODE_PRIVATE)

    fun connection(): ConnectionSettings? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val user = prefs.getString(KEY_USER, null) ?: return null
        val enc = prefs.getString(KEY_PASS, null) ?: return null
        return ConnectionSettings(url, user, enc)
    }

    /** 收到新設定且驗證成功後才替換舊憑證。 */
    fun saveConnection(serverUrl: String, username: String, password: String) {
        prefs.edit()
            .putString(KEY_URL, serverUrl.trimEnd('/'))
            .putString(KEY_USER, username)
            .putString(KEY_PASS, CredentialCipher.encrypt(password))
            .apply()
    }

    fun logout() {
        prefs.edit().remove(KEY_URL).remove(KEY_USER).remove(KEY_PASS).apply()
    }

    var audioQuality: AudioQuality
        get() = if (prefs.getBoolean(KEY_COMPAT, false)) AudioQuality.COMPATIBLE else AudioQuality.ORIGINAL
        set(value) = prefs.edit().putBoolean(KEY_COMPAT, value == AudioQuality.COMPATIBLE).apply()

    var queueSnapshot: QueueSnapshot?
        get() {
            val raw = prefs.getString(KEY_QUEUE, null) ?: return null
            return runCatching {
                val parts = raw.split("|")
                QueueSnapshot(
                    sourceKey = parts[0],
                    sourceName = parts[1],
                    songIds = parts[2].split(",").filter { it.isNotBlank() },
                    currentIndex = parts[3].toInt(),
                    positionMs = parts.getOrNull(4)?.toLongOrNull() ?: 0L,
                )
            }.getOrNull()
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_QUEUE).apply()
            } else {
                val raw = "${value.sourceKey}|${value.sourceName}|${value.songIds.joinToString(",")}|${value.currentIndex}|${value.positionMs}"
                prefs.edit().putString(KEY_QUEUE, raw).apply()
            }
        }

    /** 上次開啟時的來源名稱（佇列來源名可能包含自訂歌單名）。 */
    private companion object {
        const val KEY_URL = "server_url"
        const val KEY_USER = "username"
        const val KEY_PASS = "password_enc"
        const val KEY_COMPAT = "audio_compat"
        const val KEY_QUEUE = "queue_snapshot"
    }
}
