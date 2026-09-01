package com.unilever.mixprocessscanner.core

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

private const val DATASTORE_NAME = "com.unilever.mixprocessscanner.user_prefs"
private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

class PreferencesDataStore(private val context: Context) {

    companion object Keys {
        const val DEFAULT_SERVER_IP = "10.162.33.168"
        private const val MAX_ABS_SERVER_OFFSET_MS = 86_400_000L // 24h safety clamp

        val SERVER_IP_ADDRESS = stringPreferencesKey("server_ip_address")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val SOFTWARE_VERSION = stringPreferencesKey("software_version")
        val SHOW_SCAN_POPUP = booleanPreferencesKey("show_scan_popup")

        val SERVER_TIME_ISO = stringPreferencesKey("server_time_iso")
        val SERVER_TIME_OFFSET_MS = longPreferencesKey("server_time_offset_ms")

        val PREFS_MIGRATION_V1_DONE = booleanPreferencesKey("prefs_migration_v1_done")
    }

    val preferencesFlow: Flow<PreferencesSnapshot> = context.dataStore.data.map { prefs ->
        val rawOffset = prefs[SERVER_TIME_OFFSET_MS] ?: 0L
        val safeOffset = if (abs(rawOffset) > MAX_ABS_SERVER_OFFSET_MS) 0L else rawOffset

        PreferencesSnapshot(
            serverIpAddress = prefs[SERVER_IP_ADDRESS].orEmpty(),
            deviceId = prefs[DEVICE_ID].orEmpty(),
            softwareVersion = prefs[SOFTWARE_VERSION].orEmpty(),
            showScanPopup = prefs[SHOW_SCAN_POPUP] ?: true,
            serverTimeIso = prefs[SERVER_TIME_ISO].orEmpty(),
            serverTimeOffsetMs = safeOffset
        )
    }

    suspend fun updateServerIpAddress(value: String) {
        val normalized = value.trim()
        setStringValue(SERVER_IP_ADDRESS, normalized)
    }

    suspend fun updateDeviceId(value: String) {
        setStringValue(DEVICE_ID, value.trim())
    }

    suspend fun updateSoftwareVersion(value: String) {
        setStringValue(SOFTWARE_VERSION, value.trim())
    }

    suspend fun updateShowScanPopup(value: Boolean) = setBooleanValue(SHOW_SCAN_POPUP, value)

    suspend fun updateServerTime(iso: String, offsetMs: Long) {
        val safeOffset = if (abs(offsetMs) > MAX_ABS_SERVER_OFFSET_MS) 0L else offsetMs
        context.dataStore.edit { prefs ->
            prefs[SERVER_TIME_ISO] = iso.trim()
            prefs[SERVER_TIME_OFFSET_MS] = safeOffset
        }
    }

    suspend fun runMigrationV1IfNeeded() {
        context.dataStore.edit { prefs ->
            val done = prefs[PREFS_MIGRATION_V1_DONE] ?: false
            if (done) return@edit

            if (prefs[SERVER_IP_ADDRESS].isNullOrBlank()) {
                prefs[SERVER_IP_ADDRESS] = DEFAULT_SERVER_IP
            } else {
                prefs[SERVER_IP_ADDRESS] = prefs[SERVER_IP_ADDRESS]!!.trim()
            }

            if (prefs[DEVICE_ID] == null) prefs[DEVICE_ID] = ""
            else prefs[DEVICE_ID] = prefs[DEVICE_ID]!!.trim()

            if (prefs[SOFTWARE_VERSION] == null) prefs[SOFTWARE_VERSION] = ""
            else prefs[SOFTWARE_VERSION] = prefs[SOFTWARE_VERSION]!!.trim()

            if (prefs[SHOW_SCAN_POPUP] == null) prefs[SHOW_SCAN_POPUP] = true
            if (prefs[SERVER_TIME_ISO] == null) prefs[SERVER_TIME_ISO] = ""

            val rawOffset = prefs[SERVER_TIME_OFFSET_MS] ?: 0L
            prefs[SERVER_TIME_OFFSET_MS] =
                if (abs(rawOffset) > MAX_ABS_SERVER_OFFSET_MS) 0L else rawOffset

            prefs[PREFS_MIGRATION_V1_DONE] = true
        }
    }

    private suspend fun setStringValue(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    private suspend fun setBooleanValue(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }
}