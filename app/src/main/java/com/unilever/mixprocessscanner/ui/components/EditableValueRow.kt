package com.unilever.mixprocessscanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.unilever.mixprocessscanner.ui.theme.BlueButton
import kotlinx.coroutines.launch

@Composable
fun EditableValueRow(
    label: String,
    value: String,
    onSave: suspend (String) -> Unit,
    showButtonColor: Color = BlueButton
) {
    var showDialog by remember { mutableStateOf(false) }
    var editValue by remember(value) { mutableStateOf(value) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label)
            Text(text = value)
        }

        AppSizedButton(
            text = "Edit",
            onClick = { showDialog = true },
            containerColor = showButtonColor,
            textColor = Color.White
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Edit $label") },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    singleLine = true,
                    label = { Text(label) }
                )
            },
            confirmButton = {
                AppSizedButton(
                    text = "Save",
                    onClick = {
                        scope.launch { onSave(editValue) }
                        showDialog = false
                    },
                    containerColor = BlueButton,
                    textColor = Color.White
                )
            },
            dismissButton = {
                AppSizedButton(
                    text = "Cancel",
                    onClick = { showDialog = false },
                    containerColor = BlueButton,
                    textColor = Color.White
                )
            }
        )
    }
}