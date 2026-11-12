package com.navidrome.tv.pairing

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 局域网自动发现 Navidrome：
 * 1) mDNS（_navidrome._tcp）
 * 2) /24 网段默认端口 4533 探测，用 /rest/ping 响应识别 Subsonic 兼容服务
 * 结果去重，供手机设置页直接点选预填。
 */
class ServerDiscovery(private val context: Context) {

    private val _servers = MutableStateFlow<List<String>>(emptyList())
    val servers: StateFlow<List<String>> = _servers

    @Volatile private var nsdRunning = false

    fun start(scope: kotlinx.coroutines.CoroutineScope) {
        startNsd()
        scope.launch(Dispatchers.IO) { sweepLan(scope) }
    }

    fun stop() {
        nsdRunning = false
    }

    // ---- mDNS ----

    private fun startNsd() {
        if (nsdRunning) return
        nsdRunning = true
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val types = listOf("_navidrome._tcp", "_subsonic._tcp")
        types.forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onDiscoveryStopped(regType: String) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onStartDiscoveryFailed(regType: String, errorCode: Int) { nsdRunning = false }
                override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (!nsdRunning) return
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress ?: return
                            val port = info.port
                            addCandidate("http://$host${if (port == 80) "" else ":$port"}")
                        }
                    })
                }
            }
            try {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (_: Exception) {
            }
        }
    }

    // ---- 端口扫描 ----

    private suspend fun sweepLan(scope: kotlinx.coroutines.CoroutineScope) = withContext(Dispatchers.IO) {
        val ip = LanAddress.primaryIp() ?: return@withContext
        val prefix = ip.substringBeforeLast('.')
        val jobs = (1..254).map { n ->
            val host = "$prefix.$n"
            scope.launch(Dispatchers.IO) {
                if (portOpen(host, 4533)) {
                    probe("$host:4533")
                }
            }
        }
        jobs.joinAll()
    }

    private fun portOpen(host: String, port: Int): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 250)
            true
        }
    } catch (_: Exception) {
        false
    }

    /** 用无凭据 ping 识别 Subsonic 兼容服务：响应包含 subsonic-response。 */
    private fun probe(hostPort: String) {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(2))
            .readTimeout(java.time.Duration.ofSeconds(2))
            .build()
        listOf("http", "https").forEach { scheme ->
            try {
                val req = okhttp3.Request.Builder()
                    .url("$scheme://$hostPort/rest/ping?v=1.16.1&c=navidrome-tv&f=json")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: return@use
                    if (body.contains("subsonic-response")) {
                        addCandidate("$scheme://$hostPort")
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun addCandidate(url: String) {
        synchronized(_servers) {
            val current = _servers.value
            if (current.none { it.equals(url, ignoreCase = true) }) {
                _servers.value = current + url
            }
        }
    }
}
