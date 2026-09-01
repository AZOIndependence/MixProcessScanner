package com.unilever.mixprocessscanner.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.unilever.mixprocessscanner.core.CommLogManager
import com.unilever.mixprocessscanner.core.LogType
import com.unilever.mixprocessscanner.core.ScanBus
import com.unilever.mixprocessscanner.core.ScanEvent
import com.unilever.mixprocessscanner.viewmodel.AppViewModel

class ScanReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScanReceiver"
        private const val DEBUG_TAG = "SCAN_DEBUG"

        // Keep this aligned with your DataWedge profile intent action
        private const val APP_SCAN_ACTION = "com.unilever.mixprocessscanner.SCAN"

        // Optional known vendor actions (if you use them)
        private val ALLOWED_ACTIONS = setOf(
            APP_SCAN_ACTION,
            "com.symbol.datawedge.api.RESULT_ACTION"
        )

        // keep log payload bounded
        private const val MAX_DUMP_LEN = 1200
    }

    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            val action = intent.action.orEmpty()

            if (action.isBlank() || action !in ALLOWED_ACTIONS) {
                CommLogManager.add(
                    LogType.INFO,
                    TAG,
                    "Ignored broadcast action=$action (not allowlisted)"
                )
                return
            }

            // Optional debug dump (bounded)
            val extrasDump = bundleToDebugString(intent.extras)
            Log.d(DEBUG_TAG, "Receiver fired. action=$action extras=$extrasDump")

            // Common DataWedge / OEM variants
            val data = firstNonBlank(
                intent.getStringExtra("com.symbol.datawedge.data_string"),
                intent.getStringExtra("com.motorolasolutions.emdk.datawedge.data_string"),
                intent.getStringExtra("data_string"),
                intent.getStringExtra("barcode_data"),
                intent.getStringExtra("data")
            )

            val symbology = firstNonBlank(
                intent.getStringExtra("com.symbol.datawedge.label_type"),
                intent.getStringExtra("com.motorolasolutions.emdk.datawedge.label_type"),
                intent.getStringExtra("label_type"),
                intent.getStringExtra("barcode_type")
            ) ?: "UNKNOWN"

            if (data.isNullOrBlank()) {
                CommLogManager.add(
                    LogType.INFO,
                    TAG,
                    "Intent received but no barcode payload. action=$action extras=$extrasDump"
                )
                return
            }

            val trimmed = data.trim()
            if (trimmed.isBlank()) {
                CommLogManager.add(LogType.INFO, TAG, "Barcode payload blank after trim; ignored")
                return
            }

            // Emit app-wide event (UI observers)
            ScanBus.emit(
                ScanEvent(
                    data = trimmed,
                    symbology = symbology,
                    timestampMs = System.currentTimeMillis()
                )
            )

            // Route scan according to selectedScannerKey logic:
            // 1) match selected key => no-op
            // 2) match another scanner key => switch selection + GetReaderInfo
            // 3) no key match => Write ##SimulatedBarcode using selected key
            AppViewModel.onDataWedgeBarcodeReceived(trimmed)

            CommLogManager.add(
                LogType.INFO,
                TAG,
                "scan=$trimmed | type=$symbology | action=$action"
            )
            Log.d(DEBUG_TAG, "Scan parsed. data=$trimmed type=$symbology")
        }.onFailure { ex ->
            CommLogManager.addError(TAG, "onReceive failed: ${ex.message}")
            Log.e(DEBUG_TAG, "ScanReceiver exception", ex)
        }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }

    private fun bundleToDebugString(bundle: Bundle?): String {
        if (bundle == null) return "{}"

        val keys = bundle.keySet().toList().sorted()
        val parts = ArrayList<String>(keys.size)

        for (key in keys) {
            val value: Any? = when {
                bundle.containsKey(key) && bundle.getString(key) != null -> bundle.getString(key)
                bundle.containsKey(key) && bundle.getCharSequence(key) != null -> bundle.getCharSequence(key)
                bundle.containsKey(key) -> {
                    val intSentinel = Int.MIN_VALUE
                    val longSentinel = Long.MIN_VALUE
                    val floatSentinel = Float.NaN
                    val doubleSentinel = Double.NaN

                    when {
                        bundle.getInt(key, intSentinel) != intSentinel -> bundle.getInt(key)
                        bundle.getLong(key, longSentinel) != longSentinel -> bundle.getLong(key)
                        !bundle.getFloat(key, floatSentinel).isNaN() -> bundle.getFloat(key)
                        !bundle.getDouble(key, doubleSentinel).isNaN() -> bundle.getDouble(key)
                        else -> "<non-string-extra>"
                    }
                }
                else -> null
            }

            parts.add("$key=$value")
        }

        val raw = parts.joinToString(prefix = "{", postfix = "}")
        return if (raw.length > MAX_DUMP_LEN) raw.take(MAX_DUMP_LEN) + "...(truncated)" else raw
    }
}