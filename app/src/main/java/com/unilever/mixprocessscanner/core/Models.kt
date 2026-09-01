package com.unilever.mixprocessscanner.core

import kotlinx.serialization.Serializable

@Serializable
data class GenericResponse(
    val success: Boolean,
    val message: String,
    val payload: String? = null
)

enum class LogType {
    HTTP_GET_IN,
    HTTP_POST_IN,
    HTTP_GET_OUT,
    HTTP_POST_OUT,
    WS_IN,
    WS_OUT,
    ERROR,
    INFO
}

data class CommLogEntry(
    val timestamp: Long,
    val type: LogType,
    val originator: String,
    val details: String
)

data class PickingOrder(
    val order: String,
    val masterOrder: String
)

data class PreferencesSnapshot(
    val serverIpAddress: String = "",
    val deviceId: String = "",
    val softwareVersion: String = "",
    val showScanPopup: Boolean = true,
    val serverTimeIso: String = "",
    val serverTimeOffsetMs: Long = 0L
)