package com.unilever.mixprocessscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.unilever.mixprocessscanner.core.CommLogManager
import com.unilever.mixprocessscanner.scanner.DataWedgeConfigurator
import com.unilever.mixprocessscanner.service.KtorServerService
import com.unilever.mixprocessscanner.ui.AppNavHost
import com.unilever.mixprocessscanner.ui.theme.MixProcessScannerTheme
import com.unilever.mixprocessscanner.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                CommLogManager.addInfo(TAG, "POST_NOTIFICATIONS granted")
            } else {
                CommLogManager.addError(
                    TAG,
                    "POST_NOTIFICATIONS denied (FGS notification visibility may be limited)"
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Init logging first so all startup errors are captured
        CommLogManager.init(applicationContext)

        // 2) Init VM/state before background work
        AppViewModel.init(applicationContext)
        AppViewModel.runPrefsMigrationIfNeeded()

        // 3) Request notification permission on Android 13+
        requestPostNotificationsIfNeeded()

        // 4) Start local foreground service
        startKtorForegroundServiceSafely()

        // 5) Configure DataWedge profile
        runCatching {
            DataWedgeConfigurator.configureProfile(applicationContext)
        }.onFailure {
            CommLogManager.addError(TAG, "DataWedge configure failed: ${it.message}")
        }

        // 6) Kick legacy boot sequence
        AppViewModel.runLegacyBootSequence(applicationContext)

        setContent {
            MixProcessScannerTheme {
                AppNavHost()
            }
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!alreadyGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startKtorForegroundServiceSafely() {
        val intent = Intent(this, KtorServerService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }.onFailure {
            CommLogManager.addError(TAG, "Failed to start KtorServerService: ${it.message}")
        }
    }
}