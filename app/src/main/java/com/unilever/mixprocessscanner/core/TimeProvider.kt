package com.unilever.mixprocessscanner.core

import kotlin.math.abs

object TimeProvider {

    // Safety clamp for offset usage (24h)
    private const val MAX_ABS_OFFSET_MS = 86_400_000L

    /**
     * Returns server-aligned "now" in epoch ms using stored offset.
     * If offset is unreasonable, falls back to device time.
     */
    fun nowEpochMs(serverOffsetMs: Long): Long {
        val deviceNow = System.currentTimeMillis()
        val safeOffset = sanitizeOffset(serverOffsetMs)

        return runCatching {
            Math.addExact(deviceNow, safeOffset)
        }.getOrElse {
            deviceNow
        }
    }

    /**
     * Helper for formatting/debug.
     */
    fun nowEpochMsFromPrefs(prefs: PreferencesSnapshot): Long {
        return nowEpochMs(prefs.serverTimeOffsetMs)
    }

    /**
     * Computes offset from known server epoch and device epoch.
     * Result is sanitized to avoid extreme drift values.
     */
    fun computeOffsetMs(serverEpochMs: Long, deviceEpochMs: Long = System.currentTimeMillis()): Long {
        val raw = runCatching {
            Math.subtractExact(serverEpochMs, deviceEpochMs)
        }.getOrElse { 0L }

        return sanitizeOffset(raw)
    }

    fun sanitizeOffset(offsetMs: Long): Long {
        return if (abs(offsetMs) > MAX_ABS_OFFSET_MS) 0L else offsetMs
    }
}