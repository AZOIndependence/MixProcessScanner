package com.unilever.mixprocessscanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.unilever.mixprocessscanner.ui.components.AppSizedButton
import com.unilever.mixprocessscanner.ui.components.MainTitle
import com.unilever.mixprocessscanner.ui.components.ScreenContainer
import com.unilever.mixprocessscanner.ui.components.SubTitle
import com.unilever.mixprocessscanner.ui.theme.BlueButton
import com.unilever.mixprocessscanner.viewmodel.AppViewModel
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun ContainerInfoScreen(_navController: NavController) {
    val response by AppViewModel.containerInfoResponse.collectAsState()
    var containerInput by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val trimmedInput = containerInput.trim()

    fun submit() {
        if (trimmedInput.isNotBlank()) {
            AppViewModel.getContainerInfo(trimmedInput)
            focusManager.clearFocus()
        }
    }

    ScreenContainer {
        MainTitle("Mix Process Scanner")
        SubTitle("Container Info")

        Spacer(Modifier.height(12.dp))
        Text("Container")
        HorizontalDivider()

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = containerInput,
            onValueChange = { containerInput = it },
            label = { Text("Enter container ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { submit() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Will send: ${if (trimmedInput.isBlank()) "<empty>" else trimmedInput}",
            color = Color(0xFF6B7280)
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppSizedButton(
                text = "Get Container Info",
                onClick = { submit() },
                containerColor = BlueButton,
                textColor = Color.White,
                enabled = trimmedInput.isNotBlank()
            )

            AppSizedButton(
                text = "Clear",
                onClick = { containerInput = "" },
                containerColor = Color(0xFF6B7280),
                textColor = Color.White
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(response)
    }
}