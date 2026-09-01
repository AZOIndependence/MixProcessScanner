package com.unilever.mixprocessscanner.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GlobalState {
    // Mutable globals requested by spec, exposed as StateFlow for Compose reactivity.
    val currentUser = MutableStateFlow("")
    val currentPassword = MutableStateFlow("")
    val scannerInstructions = MutableStateFlow("")
    val loggedIn = MutableStateFlow(false)
    val lastScanData = MutableStateFlow("")

    // Immutable-on-read values, initialized at startup.
    private val _deviceIpAddress = MutableStateFlow("")
    val deviceIpAddress: StateFlow<String> = _deviceIpAddress.asStateFlow()

    private val _deviceMacId = MutableStateFlow("")
    val deviceMacId: StateFlow<String> = _deviceMacId.asStateFlow()

    // Keep-alive timeout handling
    private val _lastKeepAliveEpochMs = MutableStateFlow(0L)
    val lastKeepAliveEpochMs: StateFlow<Long> = _lastKeepAliveEpochMs.asStateFlow()

    private val _keepAliveTimeoutSec = MutableStateFlow(60)
    val keepAliveTimeoutSec: StateFlow<Int> = _keepAliveTimeoutSec.asStateFlow()

    fun setDeviceNetworkInfo(ip: String, mac: String) {
        _deviceIpAddress.value = ip
        _deviceMacId.value = mac
    }

    /**
     * Updates timeout and heartbeat timestamp.
     * @param timeoutSec desired timeout; clamped to [5, 3600]
     * @param nowEpochMsProvider optional clock provider (for server-aligned timestamps)
     */
    fun updateKeepAlive(timeoutSec: Int, nowEpochMsProvider: (() -> Long)? = null) {
        _keepAliveTimeoutSec.value = timeoutSec.coerceIn(5, 3600)
        _lastKeepAliveEpochMs.value = nowEpochMsProvider?.invoke() ?: System.currentTimeMillis()
    }

    fun resetLoginState() {
        loggedIn.value = false
        currentUser.value = ""
        currentPassword.value = ""
        scannerInstructions.value = ""
        lastScanData.value = ""
    }
}