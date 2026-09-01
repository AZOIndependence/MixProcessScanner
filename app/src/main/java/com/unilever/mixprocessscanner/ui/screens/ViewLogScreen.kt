package com.unilever.mixprocessscanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.unilever.mixprocessscanner.core.CommLogManager
import com.unilever.mixprocessscanner.ui.components.AppSizedButton
import com.unilever.mixprocessscanner.ui.components.MainTitle
import com.unilever.mixprocessscanner.ui.components.ScreenContainer
import com.unilever.mixprocessscanner.ui.components.SubTitle
import com.unilever.mixprocessscanner.ui.theme.BlueButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ViewLogScreen(_navController: NavController) {
    val logs by CommLogManager.entries.collectAsState()
    var selectedIndex by rememberSaveable { mutableStateOf(-1) }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US) }

    // Keep selected index valid when logs shrink/clear
    LaunchedEffect(logs.size) {
        if (selectedIndex !in logs.indices) selectedIndex = -1
    }

    ScreenContainer {
        MainTitle("Mix Process Scanner")
        SubTitle("Comm Log")
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSizedButton(
                text = "Clear Log",
                onClick = {
                    CommLogManager.clear()
                    selectedIndex = -1
                },
                containerColor = BlueButton,
                textColor = Color.White,
                enabled = logs.isNotEmpty()
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        // Fixed-height log list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) {
            itemsIndexed(logs) { index, entry ->
                val time = sdf.format(Date(entry.timestamp))
                val isSelected = index == selectedIndex

                Text(
                    text = "$time | ${entry.type} | ${entry.originator}",
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) Color(0xFFE8EEF9) else Color.Transparent)
                        .clickable { selectedIndex = index }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // Fixed-height scrollable detail pane
        val detailScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .verticalScroll(detailScroll)
        ) {
            if (selectedIndex in logs.indices) {
                val item = logs[selectedIndex]
                Text("Selected Log Details:", fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text("Timestamp: ${sdf.format(Date(item.timestamp))}", fontSize = 11.sp, lineHeight = 14.sp)
                Text("Type: ${item.type}", fontSize = 11.sp, lineHeight = 14.sp)
                Text("Originator: ${item.originator}", fontSize = 11.sp, lineHeight = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Data: ${item.details}",
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(24.dp))
            } else {
                Text("Select a log entry to view details.", fontSize = 11.sp)
            }
        }
    }
}