package com.unilever.mixprocessscanner.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ScanEvent(
    val data: String,
    val symbology: String,
    val timestampMs: Long = System.currentTimeMillis()
)

object ScanBus {
    private val _events = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun emit(event: ScanEvent) {
        _events.tryEmit(event)
    }
}