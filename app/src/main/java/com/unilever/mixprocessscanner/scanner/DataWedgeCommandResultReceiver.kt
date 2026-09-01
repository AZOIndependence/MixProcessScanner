package com.unilever.mixprocessscanner.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.unilever.mixprocessscanner.core.CommLogManager
import com.unilever.mixprocessscanner.core.LogType

class DataWedgeCommandResultReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DWCommandResultReceiver"
        private const val DEBUG_TAG = "DW_DEBUG"
        private const val RESULT_ACTION = "com.symbol.datawedge.api.RESULT_ACTION"
        private const val MAX_DUMP_LEN = 1200
    }

    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            val action = intent.action.orEmpty()
            if (action != RESULT_ACTION) {
                CommLogManager.add(
                    LogType.INFO,
                    TAG,
                    "Ignored broadcast action=$action (expected=$RESULT_ACTION)"
                )
                return
            }

            val extrasDump = dump(intent.extras)

            // Common DataWedge result fields
            val commandId = intent.getStringExtra("COMMAND_IDENTIFIER").orEmpty()
            val result = intent.getStringExtra("RESULT").orEmpty()
            val resultInfo = intent.getBundleExtra("RESULT_INFO")?.let { dump(it) }.orEmpty()
            val activeProfile =
                intent.getStringExtra("com.symbol.datawedge.api.RESULT_GET_ACTIVE_PROFILE").orEmpty()

            val msg =
                "DW_RESULT action=$action cmd=$commandId result=$result activeProfile=$activeProfile resultInfo=$resultInfo extras=$extrasDump"

            Log.d(DEBUG_TAG, msg)
            CommLogManager.add(LogType.INFO, TAG, msg)
        }.onFailure { ex ->
            CommLogManager.addError(TAG, "onReceive failed: ${ex.message}")
            Log.e(DEBUG_TAG, "Receiver exception", ex)
        }
    }

    private fun dump(bundle: Bundle?): String {
        if (bundle == null) return "{}"

        val parts = mutableListOf<String>()
        for (key in bundle.keySet().sorted()) {
            val value: Any? = when {
                bundle.getString(key) != null -> bundle.getString(key)
                bundle.getCharSequence(key) != null -> bundle.getCharSequence(key)
                bundle.getBundle(key) != null -> dump(bundle.getBundle(key))
                else -> {
                    val intSentinel = Int.MIN_VALUE
                    val longSentinel = Long.MIN_VALUE
                    val floatSentinel = Float.NaN
                    val doubleSentinel = Double.NaN

                    when {
                        bundle.getInt(key, intSentinel) != intSentinel -> bundle.getInt(key)
                        bundle.getLong(key, longSentinel) != longSentinel -> bundle.getLong(key)
                        !bundle.getFloat(key, floatSentinel).isNaN() -> bundle.getFloat(key)
                        !bundle.getDouble(key, doubleSentinel).isNaN() -> bundle.getDouble(key)
                        else -> "<non-string>"
                    }
                }
            }
            parts.add("$key=$value")
        }

        val raw = parts.joinToString(prefix = "{", postfix = "}")
        return if (raw.length > MAX_DUMP_LEN) raw.take(MAX_DUMP_LEN) + "...(truncated)" else raw
    }
}