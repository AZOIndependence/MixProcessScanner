package com.unilever.mixprocessscanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.unilever.mixprocessscanner.ui.components.AppSizedButton
import com.unilever.mixprocessscanner.ui.components.MainTitle
import com.unilever.mixprocessscanner.ui.components.ScreenContainer
import com.unilever.mixprocessscanner.ui.components.SubTitle
import com.unilever.mixprocessscanner.ui.theme.OrangeButton
import com.unilever.mixprocessscanner.viewmodel.AppViewModel

@Composable
fun ContainerEditScreen(_navController: NavController) {
    val containers by AppViewModel.containerList2.collectAsState()
    var selected by remember { mutableStateOf("") }

    val initial = remember {
        mutableStateMapOf(
            "Clean" to false,
            "Dirty" to false,
            "Block" to false,
            "Release" to false
        )
    }
    val current = remember {
        mutableStateMapOf(
            "Clean" to false,
            "Dirty" to false,
            "Block" to false,
            "Release" to false
        )
    }

    var popup by remember { mutableStateOf("") }

    val changed by remember(initial, current) {
        derivedStateOf { current.any { (k, v) -> initial[k] != v } }
    }

    LaunchedEffect(Unit) {
        AppViewModel.loadContainerEditListAndState(initial, current)
    }

    // Keep selection valid if backend list changes/reloads
    LaunchedEffect(containers) {
        if (containers.isEmpty()) {
            selected = ""
        } else if (selected.isBlank() || selected !in containers) {
            selected = containers.first()
        }
    }

    ScreenContainer {
        MainTitle("Mix Process Scanner")
        SubTitle("Container Edit")

        Spacer(Modifier.height(12.dp))
        Text("Container")

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            items(containers) { item ->
                val isSelected = item == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) Color(0xFFE8EEF9) else Color.Transparent)
                        .clickable { selected = item }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row {
            CheckboxRowItem("Clean", initial["Clean"] == current["Clean"], current)
            Spacer(Modifier.width(20.dp))
            CheckboxRowItem("Dirty", initial["Dirty"] == current["Dirty"], current)
        }
        Row {
            CheckboxRowItem("Block", initial["Block"] == current["Block"], current)
            Spacer(Modifier.width(20.dp))
            CheckboxRowItem("Release", initial["Release"] == current["Release"], current)
        }

        Spacer(Modifier.height(12.dp))
        AppSizedButton(
            text = "Update Container Status",
            onClick = {
                AppViewModel.updateContainerStatus(
                    selectedContainer = selected,
                    states = current.toMap()
                ) { response ->
                    popup = response
                }
            },
            containerColor = OrangeButton,
            textColor = Color.Black,
            enabled = changed && selected.isNotBlank()
        )
    }

    if (popup.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { popup = "" },
            title = { Text("Server Response") },
            text = { Text(popup) },
            confirmButton = {
                TextButton(onClick = { popup = "" }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun CheckboxRowItem(
    key: String,
    isSameAsInitial: Boolean,
    current: SnapshotStateMap<String, Boolean>
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = current[key] == true,
            onCheckedChange = { current[key] = it }
        )
        val changed = !isSameAsInitial
        Text(
            text = if (changed) "$key*" else key,
            fontWeight = if (changed) FontWeight.Bold else FontWeight.Normal
        )
    }
}