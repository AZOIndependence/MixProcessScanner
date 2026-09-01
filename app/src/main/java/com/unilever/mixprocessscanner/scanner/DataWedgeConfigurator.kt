package com.unilever.mixprocessscanner.scanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.unilever.mixprocessscanner.core.CommLogManager
import com.unilever.mixprocessscanner.core.LogType

object DataWedgeConfigurator {

    private const val DW_ACTION = "com.symbol.datawedge.api.ACTION"
    private const val DW_SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG"
    private const val DW_GET_ACTIVE_PROFILE = "com.symbol.datawedge.api.GET_ACTIVE_PROFILE"

    private const val RESULT_ACTION = "com.symbol.datawedge.api.RESULT_ACTION"
    private const val PROFILE_NAME = "MixProcessScannerProfile"

    const val SCAN_ACTION = "com.unilever.mixprocessscanner.SCAN"

    fun configureProfile(context: Context) {
        runCatching {
            val pkg = context.packageName

            val profileConfig = Bundle().apply {
                putString("PROFILE_NAME", PROFILE_NAME)
                putString("PROFILE_ENABLED", "true")
                putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST")

                // 1) App association in main profile section
                val appConfig = Bundle().apply {
                    putString("PACKAGE_NAME", pkg)
                    putStringArray("ACTIVITY_LIST", arrayOf("*"))
                }
                putParcelableArray("APP_LIST", arrayOf(appConfig))

                // 2) Barcode input plugin
                val barcodePlugin = Bundle().apply {
                    putString("PLUGIN_NAME", "BARCODE")
                    putString("RESET_CONFIG", "false")
                    putBundle("PARAM_LIST", Bundle().apply {
                        putString("scanner_selection", "auto")
                        putString("decoder_code128", "true")
                        putString("decoder_code39", "true")
                        putString("decoder_qrcode", "true")
                        putString("decoder_ean8", "true")
                        putString("decoder_ean13", "true")
                    })
                }

                // 3) Intent output plugin
                val intentPlugin = Bundle().apply {
                    putString("PLUGIN_NAME", "INTENT")
                    putString("RESET_CONFIG", "true")
                    putBundle("PARAM_LIST", Bundle().apply {
                        putString("intent_output_enabled", "true")
                        putString("intent_action", SCAN_ACTION)
                        putString("intent_category", "android.intent.category.DEFAULT")
                        putString("intent_delivery", "2") // 2 = Broadcast

                        // Component Information style keys (supported depending on DW version/build)
                        putString("intent_component_info", pkg)
                        putString("intent_use_package_name", "true")
                        putString("intent_package_name", pkg)
                        putString("intent_signature_check", "true")
                    })
                }

                // 4) Disable keystroke output (focus-independent)
                val keystrokePlugin = Bundle().apply {
                    putString("PLUGIN_NAME", "KEYSTROKE")
                    putString("RESET_CONFIG", "true")
                    putBundle("PARAM_LIST", Bundle().apply {
                        putString("keystroke_output_enabled", "false")
                    })
                }

                putParcelableArray("PLUGIN_CONFIG", arrayOf(barcodePlugin, intentPlugin, keystrokePlugin))
            }

            // SET_CONFIG request
            context.sendBroadcast(Intent(DW_ACTION).apply {
                putExtra(DW_SET_CONFIG, profileConfig)
                putExtra("SEND_RESULT", "true")
                putExtra("COMMAND_IDENTIFIER", "MIX_SET_CONFIG")
                putExtra("RESULT_ACTION", RESULT_ACTION)
            })

            // GET_ACTIVE_PROFILE request
            context.sendBroadcast(Intent(DW_ACTION).apply {
                putExtra(DW_GET_ACTIVE_PROFILE, "")
                putExtra("SEND_RESULT", "true")
                putExtra("COMMAND_IDENTIFIER", "MIX_GET_ACTIVE_PROFILE")
                putExtra("RESULT_ACTION", RESULT_ACTION)
            })

            CommLogManager.add(
                LogType.INFO,
                "DataWedgeConfigurator",
                "SET_CONFIG + GET_ACTIVE_PROFILE sent for profile=$PROFILE_NAME pkg=$pkg"
            )

            // Functional sanity: verify scan action route in-app (does not verify hardware decode)
            sendInAppSanityBroadcast(context)
        }.onFailure { ex ->
            CommLogManager.addError("DataWedgeConfigurator", "configureProfile failed: ${ex.message}")
        }
    }

    private fun sendInAppSanityBroadcast(context: Context) {
        context.sendBroadcast(Intent(SCAN_ACTION).apply {
            setPackage(context.packageName)
            putExtra("com.symbol.datawedge.data_string", "DW_SANITY_TEST")
            putExtra("com.symbol.datawedge.label_type", "TEST")
        })
        CommLogManager.add(
            LogType.INFO,
            "DataWedgeConfigurator",
            "Sent in-app sanity broadcast on action=$SCAN_ACTION"
        )
    }
}