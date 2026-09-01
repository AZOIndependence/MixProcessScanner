package com.unilever.mixprocessscanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.unilever.mixprocessscanner.core.GlobalState
import com.unilever.mixprocessscanner.core.TimeProvider
import com.unilever.mixprocessscanner.scanner.DataWedgeConfigurator
import com.unilever.mixprocessscanner.ui.components.AppSizedButton
import com.unilever.mixprocessscanner.ui.components.EditableValueRow
import com.unilever.mixprocessscanner.ui.components.MainTitle
import com.unilever.mixprocessscanner.ui.components.ScreenContainer
import com.unilever.mixprocessscanner.ui.components.SubTitle
import com.unilever.mixprocessscanner.ui.theme.BlueButton
import com.unilever.mixprocessscanner.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun DeviceInfoScreen(navController: NavController) {
    val prefs by AppViewModel.preferences.collectAsState()
    val ip by GlobalState.deviceIpAddress.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Refresh device network values each time this screen enters composition.
    LaunchedEffect(Unit) {
        AppViewModel.refreshDeviceNetworkInfo()
    }

    ScreenContainer {
        MainTitle("Mix Process Scanner")
        SubTitle("Device Info")
        Spacer(Modifier.height(16.dp))

        EditableValueRow(
            label = "Device ID:",
            value = prefs.deviceId,
            onSave = AppViewModel::updateDeviceId
        )

        Spacer(Modifier.height(12.dp))

        EditableValueRow(
            label = "Software Version:",
            value = prefs.softwareVersion,
            onSave = AppViewModel::updateSoftwareVersion
        )

        Spacer(Modifier.height(12.dp))

        EditableValueRow(
            label = "Server IP Address:",
            value = prefs.serverIpAddress,
            onSave = AppViewModel::updateServerIp
        )

        Spacer(Modifier.height(12.dp))
        Text("Device IP Address:")
        Text(ip.ifBlank { "N/A" })

        Spacer(Modifier.height(12.dp))
        Text("Server Time (last): ${prefs.serverTimeIso.ifBlank { "N/A" }}")
        Text("Server Time Offset (ms): ${prefs.serverTimeOffsetMs}")
        Text("App Now (server-aligned ms): ${TimeProvider.nowEpochMs(prefs.serverTimeOffsetMs)}")

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Show scan confirmation popup")
            Switch(
                checked = prefs.showScanPopup,
                onCheckedChange = { enabled ->
                    scope.launch { AppViewModel.updateShowScanPopup(enabled) }
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        AppSizedButton(
            text = "Configure Scanner Profile",
            onClick = { DataWedgeConfigurator.configureProfile(context.applicationContext) },
            containerColor = BlueButton,
            textColor = Color.White
        )
    }
}