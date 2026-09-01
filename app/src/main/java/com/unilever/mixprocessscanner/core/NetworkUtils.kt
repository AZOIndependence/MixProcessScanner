package com.unilever.mixprocessscanner.core

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    fun getIpAddress(): String {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            interfaces
                .flatMap { Collections.list(it.inetAddresses) }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
                ?: "0.0.0.0"
        } catch (ex: Exception) {
            CommLogManager.addError("NetworkUtils", "Failed to get IP: ${ex.message}")
            "0.0.0.0"
        }
    }

    fun getMacAddress(): String {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            // Prefer common active interfaces first
            val preferred = listOf("wlan0", "eth0", "rmnet0")
            val sorted = interfaces.sortedBy { ni ->
                val idx = preferred.indexOf(ni.name)
                if (idx >= 0) idx else Int.MAX_VALUE
            }

            val mac = sorted
                .asSequence()
                .mapNotNull { it.hardwareAddress }
                .firstOrNull { it.isNotEmpty() }
                ?.joinToString(":") { b -> "%02X".format(b) }

            mac ?: "02:00:00:00:00:00"
        } catch (ex: Exception) {
            CommLogManager.addError("NetworkUtils", "Failed to get MAC: ${ex.message}")
            "02:00:00:00:00:00"
        }
    }
}