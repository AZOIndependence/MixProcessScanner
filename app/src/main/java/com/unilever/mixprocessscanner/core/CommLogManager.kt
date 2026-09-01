package com.unilever.mixprocessscanner.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.commLogDataStore by preferencesDataStore(name = "comm_log_store")

object CommLogManager {
    private const val MAX_LOG_ENTRIES = 500
    private const val PERSIST_DEBOUNCE_MS = 300L
    private val LOG_JSON_KEY = stringPreferencesKey("comm_log_entries_json")

    private val _entries = MutableStateFlow<List<CommLogEntry>>(emptyList())
    val entries: StateFlow<List<CommLogEntry>> = _entries.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val entriesMutex = Mutex()
    private val persistMutex = Mutex()
    @Volatile
    private var persistJob: Job? = null

    /**
     * Must be called once at app startup.
     */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        loadPersistedLogs()
    }

    fun add(type: LogType, originator: String, details: String) {
        val timestamp = runCatching {
            // Server-aligned time when preferences are initialized, otherwise device time fallback.
            TimeProvider.nowEpochMs(
                com.unilever.mixprocessscanner.viewmodel.AppViewModel.preferences.value.serverTimeOffsetMs
            )
        }.getOrElse { System.currentTimeMillis() }

        val safeOrigin = originator.ifBlank { "Unknown" }
        val safeDetails = details

        val newEntry = CommLogEntry(
            timestamp = timestamp,
            type = type,
            originator = safeOrigin,
            details = safeDetails
        )

        scope.launch {
            entriesMutex.withLock {
                val updated = (_entries.value + newEntry).takeLast(MAX_LOG_ENTRIES)
                _entries.value = updated
            }
            schedulePersist()
        }
    }

    fun addError(originator: String, message: String) {
        add(LogType.ERROR, originator, message)
    }

    fun addInfo(originator: String, message: String) {
        add(LogType.INFO, originator, message)
    }

    fun clear() {
        scope.launch {
            entriesMutex.withLock {
                _entries.value = emptyList()
            }
            schedulePersist(immediate = true)
        }
    }

    private fun loadPersistedLogs() {
        val ctx = appContext ?: return
        scope.launch {
            runCatching {
                val prefs = ctx.commLogDataStore.data.first()
                val raw = prefs[LOG_JSON_KEY].orEmpty()

                val restored = if (raw.isBlank()) {
                    emptyList()
                } else {
                    json.decodeFromString<List<PersistedCommLogEntry>>(raw)
                        .map { it.toModel() }
                        .takeLast(MAX_LOG_ENTRIES)
                }

                entriesMutex.withLock {
                    _entries.value = restored
                }
            }.onFailure {
                entriesMutex.withLock {
                    _entries.value = emptyList()
                }
            }
        }
    }

    private fun schedulePersist(immediate: Boolean = false) {
        persistJob?.cancel()
        persistJob = scope.launch {
            if (!immediate) delay(PERSIST_DEBOUNCE_MS)
            persistCurrentSnapshot()
        }
    }

    private suspend fun persistCurrentSnapshot() {
        val ctx = appContext ?: return
        persistMutex.withLock {
            runCatching {
                val snapshot = entriesMutex.withLock { _entries.value }
                val persisted = snapshot.map { PersistedCommLogEntry.fromModel(it) }
                val raw = json.encodeToString(persisted)

                ctx.commLogDataStore.edit { prefs ->
                    prefs[LOG_JSON_KEY] = raw
                }
            }.onFailure {
                // Keep in-memory entries even if persist fails
            }
        }
    }
}

@Serializable
private data class PersistedCommLogEntry(
    val timestamp: Long,
    val type: String,
    val originator: String,
    val details: String
) {
    fun toModel(): CommLogEntry = CommLogEntry(
        timestamp = timestamp,
        type = runCatching { LogType.valueOf(type) }.getOrElse { LogType.INFO },
        originator = originator,
        details = details
    )

    companion object {
        fun fromModel(model: CommLogEntry): PersistedCommLogEntry = PersistedCommLogEntry(
            timestamp = model.timestamp,
            type = model.type.name,
            originator = model.originator,
            details = model.details
        )
    }
}