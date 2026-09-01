package com.unilever.mixprocessscanner

import android.app.Application
import com.unilever.mixprocessscanner.core.GlobalState
import com.unilever.mixprocessscanner.core.NetworkUtils

class MixProcessScannerApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize immutable device-level values at app startup.
        GlobalState.setDeviceNetworkInfo(
            ip = NetworkUtils.getIpAddress(),
            mac = NetworkUtils.getMacAddress()
        )
    }
}