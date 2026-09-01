package com.unilever.mixprocessscanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.unilever.mixprocessscanner.ui.components.AppSizedButton
import com.unilever.mixprocessscanner.ui.components.MainTitle
import com.unilever.mixprocessscanner.ui.components.ScreenContainer
import com.unilever.mixprocessscanner.ui.components.SubTitle
import com.unilever.mixprocessscanner.ui.theme.BlueButton
import com.unilever.mixprocessscanner.ui.theme.OrangeButton
import com.unilever.mixprocessscanner.viewmodel.AppViewModel

@Composable
fun RefillingScreen(navController: NavController) {
    var silo by rememberSaveable { mutableStateOf("") }
    var material by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var bags by rememberSaveable { mutableStateOf("") }

    val canSubmit =
        silo.isNotBlank() &&
                material.isNotBlank() &&
                unit.isNotBlank() &&
                weight.isNotBlank() &&
                bags.isNotBlank()

    ScreenContainer {
        MainTitle("Mix Process Scanner")
        SubTitle("Refilling")
        Spacer(Modifier.height(12.dp))

        InputWithClear("Silo Barcode", silo, { silo = it }, { silo = "" })
        InputWithClear("Material Barcode", material, { material = it }, { material = "" })
        InputWithClear("Unit Number", unit, { unit = it }, { unit = "" })

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Weight")
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                AppSizedButton(
                    text = "Start",
                    onClick = {
                        AppViewModel.startRefilling(
                            weight = weight.trim(),
                            bags = bags.trim(),
                            unit = unit.trim(),
                            material = material.trim(),
                            silo = silo.trim()
                        )
                    },
                    containerColor = OrangeButton,
                    textColor = Color.Black,
                    enabled = canSubmit
                )
            }

            Column(Modifier.weight(1f)) {
                Text("Bags")
                OutlinedTextField(
                    value = bags,
                    onValueChange = { bags = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                AppSizedButton(
                    text = "Stop",
                    onClick = {
                        AppViewModel.stopRefilling(
                            weight = weight.trim(),
                            bags = bags.trim(),
                            unit = unit.trim(),
                            material = material.trim(),
                            silo = silo.trim()
                        )
                    },
                    containerColor = OrangeButton,
                    textColor = Color.Black,
                    enabled = canSubmit
                )
            }

            Column(verticalArrangement = Arrangement.Bottom) {
                AppSizedButton(
                    text = "Clear",
                    onClick = { weight = ""; bags = "" },
                    containerColor = BlueButton,
                    textColor = Color.White
                )
            }
        }
    }
}

@Composable
private fun InputWithClear(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        AppSizedButton(
            text = "Clear",
            onClick = onClear,
            containerColor = BlueButton,
            textColor = Color.White,
            modifier = Modifier.widthIn(min = 88.dp)
        )
    }
    Spacer(Modifier.height(8.dp))
}