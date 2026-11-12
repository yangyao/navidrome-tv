package com.navidrome.tv.pairing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 手機送出的設定內容。帳密只存在 POST body，不出現在 URL 或日誌。 */
data class SetupSubmission(val serverUrl: String, val username: String, val password: String)

sealed interface PairingResult {
    data object Success : PairingResult
    data class Failure(val kind: Kind) : PairingResult {
        enum class Kind { REACHABILITY, CREDENTIALS, TLS }
    }
}

/** 一次配對會話：高熵憑證 + 短效配對碼，五分鐘有效；成功後立即失效。 */
class PairingSession {
    val token: String = newToken()
    val pairingCode: String = (100000..999999).random().toString()
    val expiresAt: Long = System.currentTimeMillis() + SESSION_TTL_MS
    private val usedCodes = java.util.concurrent.atomic.AtomicInteger(0)

    fun isValid(now: Long = System.currentTimeMillis()): Boolean = now < expiresAt

    /** 短效配對碼限制嘗試次數，不能作為無限授權。 */
    fun tryPairingCode(code: String): Boolean {
        if (!isValid() || usedCodes.incrementAndGet() > MAX_CODE_ATTEMPTS) return false
        return code == pairingCode
    }

    companion object {
        const val SESSION_TTL_MS: Long = 5 * 60 * 1000
        const val MAX_CODE_ATTEMPTS = 5
        fun newToken(): String {
            val bytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * 輕量區網 HTTP 服務：只有兩個端點。
 *  GET /setup            手機設定頁（內建 HTML）
 *  POST /api/config      提交設定（Authorization: Bearer <token> 或 X-Pairing-Code）
 *
 * 只允許本會話憑證；不開放 CORS；不提供檔案或代理能力。
 * 實作以 ServerSocket 完成，避免為幾個端點引入完整伺服器框架。
 */
class PairingServer(
    private val onSubmission: (SetupSubmission) -> PairingResult,
    private val discoveredServers: () -> List<String> = { emptyList() },
) {
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    sealed interface State {
        data object Idle : State
        data class Running(val port: Int, val session: PairingSession) : State
        data object Stopped : State
    }

    @Volatile private var serverSocket: java.net.ServerSocket? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    val current: State get() = _state.value

    fun start(): State.Running? {
        stop()
        return try {
            val socket = java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("0.0.0.0"))
            socket.soTimeout = 1000
            val session = PairingSession()
            val newState = State.Running(socket.localPort, session)
            android.util.Log.i("PairingServer", "listening on port ${socket.localPort}, token=${session.token}, code=${session.pairingCode}")
            serverSocket = socket
            running = true
            _state.value = newState
            thread = Thread {
                acceptLoop(socket, session)
            }.also { it.isDaemon = true; it.start() }
            newState
        } catch (e: Exception) {
            _state.value = State.Stopped
            null
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        thread?.join(500)
        thread = null
        if (_state.value is State.Running) _state.value = State.Stopped
    }

    private fun acceptLoop(socket: java.net.ServerSocket, session: PairingSession) {
        while (running) {
            val client = try {
                socket.accept()
            } catch (e: Exception) {
                if (running) continue else break
            }
            try {
                handle(client, session)
            } catch (_: Exception) {
                // 單一連線錯誤不影響服務
            } finally {
                runCatching { client.close() }
            }
        }
    }

    private fun handle(client: java.net.Socket, session: PairingSession) {
        client.soTimeout = 5000
        val reader = client.getInputStream().bufferedReader(Charsets.UTF_8)
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val path = parts[1].substringBefore('#').substringBefore('?')

        val headers = mutableMapOf<String, String>()
        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) {
                val name = line.substring(0, idx).trim().lowercase()
                val value = line.substring(idx + 1).trim()
                headers[name] = value
                if (name == "content-length") contentLength = value.toIntOrNull() ?: 0
            }
        }

        // 限制請求大小
        if (contentLength > 8192) {
            respond(client, 413, "{\"error\":\"payload too large\"}")
            return
        }

        // 只接受正確 Host 的請求（避免 DNS rebinding）；本機與純 IP Host 皆可
        val host = headers["host"] ?: return
        val isLocalHost = host.startsWith("localhost") || host.startsWith("127.0.0.1")
        val hostIsIp = host.substringBefore(':').split('.').all { it.toIntOrNull() in 0..255 }
        if (!isLocalHost && !hostIsIp) {
            respond(client, 403, "{\"error\":\"forbidden host\"}")
            return
        }

        when {
            method == "GET" && path == "/setup" -> {
                respond(client, 200, SetupPage.html, "text/html; charset=utf-8")
            }
            method == "GET" && path == "/api/session" -> {
                // 手機頁開啟時檢查會話是否仍有效
                val ok = session.isValid()
                respond(client, if (ok) 200 else 410, if (ok) "{\"valid\":true}" else "{\"valid\":false}", "application/json")
            }
            method == "GET" && path == "/api/discover" -> {
                // 自動發現的伺服器候選；僅回傳位址，不含任何憑證
                val ok = session.isValid()
                if (!ok) {
                    respond(client, 410, "{\"servers\":[]}", "application/json")
                } else {
                    val list = discoveredServers().joinToString(",") { s ->
                        "\"" + s.replace("\"", "\\\"") + "\""
                    }
                    respond(client, 200, "{\"servers\":[$list]}", "application/json")
                }
            }
            method == "POST" && path == "/api/config" -> {
                handleConfig(client, session, headers, reader, contentLength)
            }
            else -> respond(client, 404, "{\"error\":\"not found\"}")
        }
    }

    private fun handleConfig(
        client: java.net.Socket,
        session: PairingSession,
        headers: Map<String, String>,
        reader: java.io.BufferedReader,
        contentLength: Int,
    ) {
        if (!session.isValid()) {
            respond(client, 410, "{\"error\":\"session expired\"}", "application/json")
            return
        }
        val auth = headers["authorization"] ?: ""
        val bearer = auth.removePrefix("Bearer ").trim()
        val codeHeader = headers["x-pairing-code"]?.trim()
        val authorized = bearer == session.token || (codeHeader != null && session.tryPairingCode(codeHeader))
        if (!authorized) {
            respond(client, 401, "{\"error\":\"invalid pairing credential\"}", "application/json")
            return
        }
        val chars = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val n = reader.read(chars, read, contentLength - read)
            if (n < 0) break
            read += n
        }
        val body = String(chars, 0, read)
        val submission = parseJsonBody(body) ?: run {
            respond(client, 400, "{\"error\":\"bad request\"}", "application/json")
            return
        }
        val result = onSubmission(submission)
        if (result is PairingResult.Success) {
            // 成功後立即失效；先完成回應再由呼叫端停止服務，避免手機收不到成功訊息
            respond(client, 200, "{\"ok\":true}", "application/json")
        } else {
            val kind = (result as PairingResult.Failure).kind.name.lowercase()
            respond(client, 422, "{\"ok\":false,\"error\":\"$kind\"}", "application/json")
        }
    }

    private fun parseJsonBody(body: String): SetupSubmission? {
        val regex = Regex("\"(serverUrl|username|password)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val map = regex.findAll(body).associate {
            val key = it.groupValues[1]
            val value = it.groupValues[2]
                .replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
            key to value
        }
        val url = map["serverUrl"] ?: return null
        val user = map["username"] ?: return null
        val pass = map["password"] ?: return null
        if (url.isBlank() || user.isBlank()) return null
        return SetupSubmission(url.trim(), user.trim(), pass)
    }

    private fun respond(socket: java.net.Socket, code: Int, body: String, contentType: String = "application/json") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
            404 -> "Not Found"; 410 -> "Gone"; 413 -> "Payload Too Large"; 422 -> "Unprocessable Entity"
            else -> "Error"
        }
        val head = buildString {
            append("HTTP/1.1 $code $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: close\r\n\r\n")
        }
        val out = socket.getOutputStream()
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }
}
