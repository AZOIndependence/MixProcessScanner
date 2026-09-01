package com.unilever.mixprocessscanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.unilever.mixprocessscanner.core.GlobalState
import com.unilever.mixprocessscanner.core.ScanBus
import com.unilever.mixprocessscanner.ui.components.AppSizedButton
import com.unilever.mixprocessscanner.ui.components.MainTitle
import com.unilever.mixprocessscanner.ui.components.ScreenContainer
import com.unilever.mixprocessscanner.ui.components.SubTitle
import com.unilever.mixprocessscanner.ui.theme.BlueButton
import com.unilever.mixprocessscanner.ui.theme.GreenBox
import com.unilever.mixprocessscanner.ui.theme.OrangeButton
import com.unilever.mixprocessscanner.viewmodel.AppViewModel

@Composable
fun ScanningScreen(_navController: NavController) {
    val prefs by AppViewModel.preferences.collectAsState()
    val scannerEntries by AppViewModel.scannerEntries.collectAsState()
    val isScannerListLoading by AppViewModel.isScannerListLoading.collectAsState()
    val instructions by GlobalState.scannerInstructions.collectAsState()
    val runtimeServerName by AppViewModel.runtimeServerName.collectAsState()

    val scannerDisplayValues = scannerEntries.map { it.value }
    val selectedScanner by AppViewModel.selectedScannerDisplayValue.collectAsState()
    val selectedScannerKey by AppViewModel.selectedScannerKey.collectAsState()

    var expanded by rememberSaveable { mutableStateOf(false) }
    var manualInput by rememberSaveable { mutableStateOf("") }
    var showKeyboard by rememberSaveable { mutableStateOf(false) }

    var showScanPopup by remember { mutableStateOf(false) }
    var lastScannedData by remember { mutableStateOf("") }
    var lastScannedType by remember { mutableStateOf("") }

    val requester = remember { FocusRequester() }
    val enableInitialManualFocus = false

    LaunchedEffect(Unit) {
        if (enableInitialManualFocus) requester.requestFocus()
        AppViewModel.loadScannerListFromLegacyPost()
    }

    // Start/stop reader polling with screen lifecycle
    DisposableEffect(Unit) {
        AppViewModel.startReaderPolling()
        onDispose {
            AppViewModel.stopReaderPolling()
        }
    }

    LaunchedEffect(Unit) {
        ScanBus.events.collect { evt ->
            val scanned = evt.data.trim()
            lastScannedData = scanned
            lastScannedType = evt.symbology
            showScanPopup = prefs.showScanPopup
            GlobalState.scannerInstructions.value = "Scanned (${evt.symbology}): $scanned"
        }
    }

    Box(Modifier.fillMaxSize()) {
        ScreenContainer {
            MainTitle("Mix Process Scanner")
            SubTitle("Scanning")
            Spacer(Modifier.height(8.dp))

            val hostLabel = runtimeServerName.ifBlank { "unknown" }
            Text("$hostLabel:$selectedScannerKey")
            Spacer(Modifier.height(4.dp))

            Box {
                OutlinedTextField(
                    value = selectedScanner,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Scanner") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            enabled = !isScannerListLoading,
                            onClick = {
                                if (scannerDisplayValues.isEmpty()) {
                                    AppViewModel.loadScannerListFromLegacyPost(forceRefresh = true)
                                    return@IconButton
                                }
                                expanded = true
                            }
                        ) { Text("▼") }
                    }
                )

                DropdownMenu(
                    expanded = expanded && scannerDisplayValues.isNotEmpty() && !isScannerListLoading,
                    onDismissRequest = { expanded = false }
                ) {
                    scannerDisplayValues.forEach { scannerDisplay ->
                        DropdownMenuItem(
                            text = { Text(scannerDisplay) },
                            onClick = {
                                AppViewModel.setSelectedScannerByDisplayValue(scannerDisplay)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(GreenBox)
                    .padding(12.dp)
            ) {
                Text(instructions, color = Color.Black)
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { AppViewModel.sendUpCommand(selectedScanner) },
                    modifier = Modifier.size(width = 100.dp, height = 40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueButton),
                    enabled = selectedScanner.isNotBlank()
                ) { Text("Up", color = Color.White) }

                Button(
                    onClick = { AppViewModel.sendOkCommand(selectedScanner) },
                    modifier = Modifier.size(width = 100.dp, height = 40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueButton),
                    enabled = selectedScanner.isNotBlank()
                ) { Text("OK", color = Color.White) }

                Button(
                    onClick = { AppViewModel.sendDownCommand(selectedScanner) },
                    modifier = Modifier.size(width = 100.dp, height = 40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueButton),
                    enabled = selectedScanner.isNotBlank()
                ) { Text("Down", color = Color.White) }
            }

            Spacer(Modifier.height(10.dp))
            Text("Manual Input:")
            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                label = { Text("Manual Entry") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (showKeyboard) KeyboardType.Text else KeyboardType.Ascii
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requester)
            )

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSizedButton(
                    text = "Clear",
                    onClick = { manualInput = "" },
                    containerColor = BlueButton,
                    textColor = Color.White
                )

                IconButton(onClick = { showKeyboard = !showKeyboard }) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard")
                }

                AppSizedButton(
                    text = "SEND",
                    onClick = {
                        val input = manualInput.trim()
                        if (input.isBlank()) return@AppSizedButton
                        AppViewModel.onDataWedgeBarcodeReceived(input)
                        manualInput = ""
                    },
                    containerColor = OrangeButton,
                    textColor = Color.Black,
                    enabled = manualInput.isNotBlank() && selectedScannerKey.isNotBlank()
                )
            }
        }

        if (isScannerListLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Card {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Loading scanner list...")
                    }
                }
            }
        }

        if (showScanPopup) {
            AlertDialog(
                onDismissRequest = { showScanPopup = false },
                title = { Text("Scan Received") },
                text = { Text("Type: $lastScannedType\n\nData:\n$lastScannedData") },
                confirmButton = {
                    TextButton(onClick = { showScanPopup = false }) { Text("OK") }
                }
            )
        }
    }
}