package com.unilever.mixprocessscanner.viewmodel

import android.app.AlarmManager
import android.content.Context
import android.os.BatteryManager
import com.unilever.mixprocessscanner.core.CommLogManager
import com.unilever.mixprocessscanner.core.GlobalState
import com.unilever.mixprocessscanner.core.LogType
import com.unilever.mixprocessscanner.core.NetworkUtils
import com.unilever.mixprocessscanner.core.PickingOrder
import com.unilever.mixprocessscanner.core.PreferencesDataStore
import com.unilever.mixprocessscanner.core.PreferencesSnapshot
import com.unilever.mixprocessscanner.core.TimeProvider
import com.unilever.mixprocessscanner.network.ApiService
import com.unilever.mixprocessscanner.network.KtorClientProvider
import com.unilever.mixprocessscanner.network.LegacyCompatibilityService
import com.unilever.mixprocessscanner.network.LegacySoapClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AppViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = ApiService()
    private val timeProvider = TimeProvider

    private var appContext: Context? = null
    private fun prefsStoreOrNull(): PreferencesDataStore? =
        appContext?.let { PreferencesDataStore(it.applicationContext) }

    private val legacyService by lazy {
        LegacyCompatibilityService(
            client = KtorClientProvider.client,
            timeProvider = timeProvider,
            serverOffsetMsProvider = { _preferences.value.serverTimeOffsetMs },
            serverIpProvider = { _preferences.value.serverIpAddress.ifBlank { "10.162.33.168" } },
            batteryLevelProvider = {
                val ctx = appContext
                if (ctx == null) 0 else runCatching {
                    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
                }.getOrDefault(0)
            },
            localIpProvider = { GlobalState.deviceIpAddress.value.ifBlank { "0.0.0.0" } },
            selectedReaderProvider = { _selectedScannerKey.value },
            externalScope = scope
        )
    }

    private val _runtimeServerName = MutableStateFlow("")
    val runtimeServerName: StateFlow<String> = _runtimeServerName.asStateFlow()

    private val _selectedScannerDisplayValue = MutableStateFlow("")
    val selectedScannerDisplayValue: StateFlow<String> = _selectedScannerDisplayValue.asStateFlow()

    private val _selectedScannerKey = MutableStateFlow("")
    val selectedScannerKey: StateFlow<String> = _selectedScannerKey.asStateFlow()

    private val _preferences = MutableStateFlow(PreferencesSnapshot())
    val preferences: StateFlow<PreferencesSnapshot> = _preferences.asStateFlow()

    private val _pickingOrders = MutableStateFlow<List<PickingOrder>>(emptyList())
    val pickingOrders: StateFlow<List<PickingOrder>> = _pickingOrders.asStateFlow()

    data class ScannerEntry(val key: String, val value: String)

    private val _scannerEntries = MutableStateFlow<List<ScannerEntry>>(emptyList())
    val scannerEntries: StateFlow<List<ScannerEntry>> = _scannerEntries.asStateFlow()

    private val scannerKeyByValue = linkedMapOf<String, String>()

    private val _containerList1 = MutableStateFlow<List<String>>(emptyList())
    val containerList1: StateFlow<List<String>> = _containerList1.asStateFlow()

    private val _containerList2 = MutableStateFlow<List<String>>(emptyList())
    val containerList2: StateFlow<List<String>> = _containerList2.asStateFlow()

    private val _containerInfoResponse = MutableStateFlow("")
    val containerInfoResponse: StateFlow<String> = _containerInfoResponse.asStateFlow()

    private val readerInfoInFlightKeys = mutableSetOf<String>()
    private val readerInfoLock = Any()

    private val _isScannerListLoading = MutableStateFlow(false)
    val isScannerListLoading: StateFlow<Boolean> = _isScannerListLoading.asStateFlow()

    private var readerPollingJob: Job? = null
    private const val READER_POLL_MS = 700L

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        // Seed network info at app startup
        refreshDeviceNetworkInfo()

        scope.launch {
            prefsStoreOrNull()?.preferencesFlow?.collect { snap ->
                _preferences.value = snap

                if (snap.serverIpAddress.isBlank()) {
                    CommLogManager.addInfo(
                        "AppViewModel",
                        "server_ip_address blank; defaulting to 10.162.33.168"
                    )
                    prefsStoreOrNull()?.updateServerIpAddress("10.162.33.168")
                }
            } ?: CommLogManager.addError("AppViewModel", "PreferencesDataStore not initialized")
        }
    }

    /**
     * Refreshes device network identity values (IP + MAC) from current system state.
     * Safe to call repeatedly (e.g., each DeviceInfoScreen launch).
     */
    fun refreshDeviceNetworkInfo() {
        scope.launch {
            val ip = runCatching {
                NetworkUtils.getIpAddress()
            }.getOrDefault("")

            val mac = runCatching {
                NetworkUtils.getMacAddress()
            }.getOrDefault("")

            GlobalState.setDeviceNetworkInfo(ip = ip, mac = mac)

            CommLogManager.add(
                LogType.INFO,
                "DeviceInfo",
                "Refreshed network info: ip=${ip.ifBlank { "N/A" }}, mac=${mac.ifBlank { "N/A" }}"
            )
        }
    }

    fun runPrefsMigrationIfNeeded() {
        scope.launch {
            prefsStoreOrNull()?.runMigrationV1IfNeeded()
                ?: CommLogManager.addError("AppViewModel", "PreferencesDataStore not initialized")
        }
    }

    suspend fun updateServerIp(value: String) {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            CommLogManager.addError("AppViewModel", "Rejected blank server_ip_address update")
            return
        }
        prefsStoreOrNull()?.updateServerIpAddress(normalized)
            ?: CommLogManager.addError("AppViewModel", "PreferencesDataStore not initialized")
    }

    suspend fun updateDeviceId(value: String) {
        prefsStoreOrNull()?.updateDeviceId(value)
            ?: CommLogManager.addError("AppViewModel", "PreferencesDataStore not initialized")
    }

    suspend fun updateSoftwareVersion(value: String) {
        prefsStoreOrNull()?.updateSoftwareVersion(value)
            ?: CommLogManager.addError("AppViewModel", "PreferencesDataStore not initialized")
    }

    suspend fun updateShowScanPopup(value: Boolean) {
        prefsStoreOrNull()?.updateShowScanPopup(value)
            ?: CommLogManager.addError("AppViewModel", "PreferencesDataStore not initialized")
    }

    suspend fun updateServerTime(iso: String, offsetMs: Long) {
        prefsStoreOrNull()?.updateServerTime(iso, offsetMs)
            ?: CommLogManager.addError("AppViewModel", "PreferencesDataStore not initialized")
    }

    private suspend fun legacyCredentials(): LegacyCompatibilityService.Credentials {
        val snap = prefsStoreOrNull()?.preferencesFlow?.first() ?: _preferences.value
        val deviceId = snap.deviceId.ifBlank { "820D28C6" }
        val token = GlobalState.currentPassword.value

        return LegacyCompatibilityService.Credentials(
            token = token,
            deviceId = deviceId
        )
    }

    fun startLegacyHeartbeat() {
        scope.launch {
            legacyService.startHeartbeat { legacyCredentials() }
        }
    }

    fun stopLegacyHeartbeat() {
        scope.launch {
            legacyService.stopHeartbeat()
        }
    }

    fun startReaderPolling() {
        if (readerPollingJob?.isActive == true) return
        readerPollingJob = scope.launch {
            CommLogManager.add(LogType.INFO, "ReaderPolling", "Started")
            while (currentCoroutineContext().isActive) {
                val key = _selectedScannerKey.value.trim()
                if (key.isNotBlank()) {
                    runCatching { legacyService.fetchReaderInfo(key) }
                        .onFailure { CommLogManager.addError("ReaderPolling", "GetReaderInfo failed: ${it.message}") }
                }
                delay(READER_POLL_MS)
            }
        }
    }

    fun stopReaderPolling() {
        readerPollingJob?.cancel()
        readerPollingJob = null
        CommLogManager.add(LogType.INFO, "ReaderPolling", "Stopped")
    }

    fun onDataWedgeBarcodeReceived(rawScan: String) {
        val scan = rawScan.trim()
        if (scan.isBlank()) return

        val currentKey = _selectedScannerKey.value.trim()
        if (currentKey.isBlank()) {
            CommLogManager.add(LogType.INFO, "DataWedge", "Ignored scan; selectedScannerKey is blank")
            return
        }

        if (scan == currentKey) {
            CommLogManager.add(LogType.INFO, "DataWedge", "Scan matches selected key ($scan); no action")
            return
        }

        val matchedEntry = _scannerEntries.value.firstOrNull { it.key == scan }
        if (matchedEntry != null) {
            CommLogManager.add(
                LogType.INFO,
                "DataWedge",
                "Scan matched scanner key ${matchedEntry.key}; switching dropdown to ${matchedEntry.value}"
            )
            setSelectedScannerByDisplayValue(matchedEntry.value)
            return
        }

        scope.launch {
            runCatching {
                legacyService.writeSimulatedBarcode(
                    readerName = currentKey,
                    barcodeData = scan
                )
            }.onSuccess {
                CommLogManager.add(
                    LogType.INFO,
                    "DataWedge",
                    "SimulatedBarcode sent for reader=$currentKey data=$scan"
                )
            }.onFailure { e ->
                CommLogManager.addError("DataWedge", "SimulatedBarcode failed: ${e.message}")
            }
        }
    }

    fun loadScannerListFromLegacyPost(forceRefresh: Boolean = false) {
        scope.launch {
            if (_isScannerListLoading.value) {
                CommLogManager.add(LogType.INFO, "ScannerList", "Load already in progress; skipping")
                return@launch
            }

            _isScannerListLoading.value = true
            try {
                if (!forceRefresh && _scannerEntries.value.isNotEmpty()) {
                    CommLogManager.add(LogType.INFO, "ScannerList", "Skipped reload; already populated")
                    return@launch
                }

                val creds = legacyCredentials()
                val hostName = legacyService.fetchBarcodeHostName()
                _runtimeServerName.value = hostName

                val entries = legacyService.fetchScannerNames(creds)
                    .map { ScannerEntry(key = it.key, value = it.value) }

                _scannerEntries.value = entries
                scannerKeyByValue.clear()
                entries.forEach { scannerKeyByValue[it.value] = it.key }

                val currentDisplay = _selectedScannerDisplayValue.value

                if (entries.isNotEmpty() && currentDisplay.isBlank()) {
                    setSelectedScannerByDisplayValue(entries.first().value)
                } else {
                    _selectedScannerKey.value = scannerKeyByValue[currentDisplay].orEmpty()
                }

                CommLogManager.add(
                    LogType.INFO,
                    "ScannerList",
                    "Loaded ${entries.size} scanners from legacy POST GetScannerNames"
                )
            } catch (e: Exception) {
                CommLogManager.addError("ScannerList", "Failed to load from legacy POST: ${e.message}")
                if (_scannerEntries.value.isEmpty()) {
                    scannerKeyByValue.clear()
                }
            } finally {
                _isScannerListLoading.value = false
            }
        }
    }

    fun setSelectedScannerByDisplayValue(displayValue: String) {
        val newKey = scannerKeyByValue[displayValue].orEmpty()
        val oldKey = _selectedScannerKey.value

        _selectedScannerDisplayValue.value = displayValue
        _selectedScannerKey.value = newKey

        if (newKey.isNotBlank() && newKey != oldKey) {
            scope.launch {
                val shouldRun = synchronized(readerInfoLock) {
                    if (readerInfoInFlightKeys.contains(newKey)) false
                    else {
                        readerInfoInFlightKeys.add(newKey)
                        true
                    }
                }
                if (!shouldRun) return@launch

                runCatching { legacyService.fetchReaderInfo(newKey) }
                    .onFailure { e ->
                        CommLogManager.add(
                            LogType.ERROR,
                            "ReaderInfo",
                            "Notify failed for key=$newKey: ${e.message}"
                        )
                    }
                    .also {
                        synchronized(readerInfoLock) {
                            readerInfoInFlightKeys.remove(newKey)
                        }
                    }
            }
        }
    }

    fun scannerKeyForDisplayValue(displayValue: String): String? = scannerKeyByValue[displayValue]

    fun runLegacyBootSequence(context: Context) {
        scope.launch {
            try {
                val prefs = _preferences.value

                val batteryLevel = runCatching {
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceAtLeast(0)
                }.getOrDefault(0)

                val result = LegacySoapClient.runBootSequence(
                    prefs = prefs,
                    deviceIp = GlobalState.deviceIpAddress.value,
                    batteryLevel = batteryLevel
                )

                val iso = result.serverTimeIso.orEmpty()
                val serverEpoch = if (iso.isNotBlank()) LegacySoapClient.parseServerTimeToEpochMs(iso) else null
                if (serverEpoch != null) {
                    val offset = serverEpoch - timeProvider.nowEpochMs(_preferences.value.serverTimeOffsetMs)
                    updateServerTime(iso, offset)
                    CommLogManager.addInfo("BootSequence", "Server time synced (offsetMs=$offset)")
                } else {
                    CommLogManager.addError("BootSequence", "Failed to parse server time")
                }

                attemptSetSystemTime(context, result.serverTimeIso)
            } catch (ex: Exception) {
                CommLogManager.addError("BootSequence", "runLegacyBootSequence failed: ${ex.message}")
            }
        }
    }

    private fun attemptSetSystemTime(context: Context, iso: String?) {
        if (iso.isNullOrBlank()) return
        val epoch = LegacySoapClient.parseServerTimeToEpochMs(iso) ?: return

        runCatching {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            @Suppress("MissingPermission")
            alarm.setTime(epoch)
            CommLogManager.addInfo("BootSequence", "Attempted set system time to $iso")
        }.onFailure {
            CommLogManager.addError(
                "BootSequence",
                "System time set not permitted on this device profile. Stored offset only."
            )
        }
    }

    fun logout() {
        scope.launch {
            stopReaderPolling()
            stopLegacyHeartbeat()

            val url = buildUrl("/logout")
            val payload = mapOf(
                "username" to GlobalState.currentUser.value,
                "reason" to "user_logout"
            )

            runCatching { api.genericPost(url, payload) }
                .onFailure { CommLogManager.addError("Logout", "Backend logout failed: ${it.message}") }

            GlobalState.resetLoginState()
        }
    }

    fun submitLogin() {
        scope.launch {
            val url = buildUrl("/login")
            val payload = mapOf(
                "username" to GlobalState.currentUser.value,
                "password" to GlobalState.currentPassword.value
            )
            val response = api.genericPost(url, payload)
            if (response.success) {
                GlobalState.loggedIn.value = true

                runCatching { legacyService.runSingleCycleNow { legacyCredentials() } }
                    .onFailure { CommLogManager.addError("Login", "Initial legacy cycle failed: ${it.message}") }

                startLegacyHeartbeat()
                loadScannerListFromLegacyPost()
            }
        }
    }

    fun updatePickingOrderList() {
        scope.launch {
            val url = buildUrl("/picking/orders")
            val response = api.genericGet(url)
            _pickingOrders.value = listOf(
                PickingOrder("ORD-1001", "MO-5001"),
                PickingOrder("ORD-1002", "MO-5002"),
                PickingOrder("ORD-1003", "MO-5003")
            )
            CommLogManager.add(LogType.INFO, "UpdatePickingOrderList", response.message)
        }
    }

    fun startRefilling(weight: String, bags: String, unit: String, material: String, silo: String) {
        scope.launch {
            val url = buildUrl("/refilling/start")
            val payload = mapOf(
                "weight" to weight,
                "bags" to bags,
                "unit" to unit,
                "materialBarcode" to material,
                "siloBarcode" to silo
            )
            api.genericPost(url, payload)
        }
    }

    fun stopRefilling(weight: String, bags: String, unit: String, material: String, silo: String) {
        scope.launch {
            val url = buildUrl("/refilling/stop")
            val payload = mapOf(
                "weight" to weight,
                "bags" to bags,
                "unit" to unit,
                "materialBarcode" to material,
                "siloBarcode" to silo
            )
            api.genericPost(url, payload)
        }
    }

    fun sendScannerData(scannerDisplay: String, data: String) {
        scope.launch {
            val scannerKey = scannerKeyForDisplayValue(scannerDisplay).orEmpty()
            val url = buildUrl("/scanner/send")
            val payload = mapOf(
                "scanner" to scannerDisplay,
                "scannerKey" to scannerKey,
                "data" to data
            )
            api.genericPost(url, payload)
        }
    }

    fun sendUpCommand(scannerDisplay: String) {
        scope.launch {
            val url = buildUrl("/scanner/up")
            val payload = mapOf("scanner" to scannerDisplay, "command" to "UP")
            api.genericPost(url, payload)
        }
    }

    fun sendDownCommand(scannerDisplay: String) {
        scope.launch {
            val url = buildUrl("/scanner/down")
            val payload = mapOf("scanner" to scannerDisplay, "command" to "DOWN")
            api.genericPost(url, payload)
        }
    }

    fun sendOkCommand(scannerDisplay: String) {
        scope.launch {
            val url = buildUrl("/scanner/ok")
            val payload = mapOf("scanner" to scannerDisplay, "command" to "OK")
            api.genericPost(url, payload)
        }
    }

    fun getReaderInfo(selectedScannerDisplay: String) {
        if (selectedScannerDisplay.isBlank()) return

        val key = scannerKeyForDisplayValue(selectedScannerDisplay)
        if (key == null) {
            if (GlobalState.scannerInstructions.value.isBlank()) {
                GlobalState.scannerInstructions.value = "No scanner key mapping for '$selectedScannerDisplay'"
            }
            return
        }

        scope.launch {
            runCatching {
                legacyService.fetchReaderInfo(key)
                if (GlobalState.scannerInstructions.value.isBlank()) {
                    GlobalState.scannerInstructions.value =
                        "Reader ready for scanner: $selectedScannerDisplay"
                }
            }.onFailure {
                CommLogManager.addError("ReaderInfo", "GetReaderInfo failed for $key: ${it.message}")
            }
        }
    }

    fun loadContainerListFromSql() {
        scope.launch {
            _containerList1.value = listOf("CT-001", "CT-002", "CT-003", "CT-004")
        }
    }

    fun getContainerInfo(selectedContainer: String) {
        val normalized = selectedContainer.trim()
        if (normalized.isBlank()) {
            _containerInfoResponse.value = "Please enter a container first."
            return
        }

        scope.launch {
            val encodedContainer = URLEncoder.encode(normalized, StandardCharsets.UTF_8.toString())
            val url = buildUrl("/container/info?container=$encodedContainer")
            val response = api.genericGet(url)
            _containerInfoResponse.value = response.payload ?: response.message
        }
    }

    fun loadContainerEditListAndState(
        initial: MutableMap<String, Boolean>,
        current: MutableMap<String, Boolean>
    ) {
        scope.launch {
            _containerList2.value = listOf("CT-100", "CT-101", "CT-102")

            initial["Clean"] = true
            initial["Dirty"] = false
            initial["Block"] = false
            initial["Release"] = true

            current["Clean"] = initial["Clean"] ?: false
            current["Dirty"] = initial["Dirty"] ?: false
            current["Block"] = initial["Block"] ?: false
            current["Release"] = initial["Release"] ?: false
        }
    }

    fun updateContainerStatus(
        selectedContainer: String,
        states: Map<String, Boolean>,
        onResponse: (String) -> Unit
    ) {
        if (selectedContainer.isBlank()) {
            onResponse("Please select a container first.")
            return
        }

        scope.launch {
            val url = buildUrl("/container/update")
            val payload = mapOf(
                "container" to selectedContainer,
                "clean" to (states["Clean"] ?: false),
                "dirty" to (states["Dirty"] ?: false),
                "block" to (states["Block"] ?: false),
                "release" to (states["Release"] ?: false)
            )
            val response = api.genericPost(url, payload)
            onResponse(response.payload ?: response.message)
        }
    }

    private fun currentServerIp(): String =
        _preferences.value.serverIpAddress.ifBlank { "10.162.33.168" }

    private fun buildUrl(path: String): String {
        val base = currentServerIp()
        return "http://$base$path"
    }
}