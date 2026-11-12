package com.navidrome.tv.pairing

import java.net.NetworkInterface

/** 取目前可用的區網 IPv4 地址，供二維碼與手動輸入顯示。 */
object LanAddress {
    fun primaryIp(): String? {
        return NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filter { it is java.net.Inet4Address && !it.isLoopbackAddress }
            .map { it.hostAddress }
            .firstOrNull()
    }

    fun allIps(): List<String> = primaryIp()?.let { listOf(it) } ?: emptyList()
}
