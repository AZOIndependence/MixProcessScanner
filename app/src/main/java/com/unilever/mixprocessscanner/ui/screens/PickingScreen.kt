package com.unilever.mixprocessscanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.unilever.mixprocessscanner.core.AppRoutes
import com.unilever.mixprocessscanner.ui.components.AppSizedButton
import com.unilever.mixprocessscanner.ui.components.MainTitle
import com.unilever.mixprocessscanner.ui.components.ScreenContainer
import com.unilever.mixprocessscanner.ui.components.SubTitle
import com.unilever.mixprocessscanner.ui.theme.BlueButton
import com.unilever.mixprocessscanner.ui.theme.OrangeButton
import com.unilever.mixprocessscanner.viewmodel.AppViewModel

@Composable
fun PickingScreen(navController: NavController) {
    val orders by AppViewModel.pickingOrders.collectAsState()
    var selectedOrder by rememberSaveable { mutableStateOf("") }

    // Keep selection valid when list refreshes
    LaunchedEffect(orders) {
        if (orders.isEmpty()) {
            selectedOrder = ""
        } else if (selectedOrder.isBlank() || orders.none { it.order == selectedOrder }) {
            selectedOrder = orders.first().order
        }
    }

    ScreenContainer {
        MainTitle("Mix Process Scanner")
        SubTitle("Picking Orders")
        Spacer(Modifier.height(8.dp))

        AppSizedButton(
            text = "Refresh List",
            onClick = { AppViewModel.updatePickingOrderList() },
            containerColor = BlueButton,
            textColor = Color.White
        )

        Spacer(Modifier.height(8.dp))
        Text("Available Orders:")
        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Order", fontWeight = FontWeight.SemiBold)
            Text("MasterOrder", fontWeight = FontWeight.SemiBold)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 360.dp)
        ) {
            items(orders) { item ->
                val isSelected = item.order == selectedOrder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) Color(0xFFE8EEF9) else Color.Transparent)
                        .clickable { selectedOrder = item.order }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.order,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Text(
                        text = item.masterOrder,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Selected Order: $selectedOrder")
        Spacer(Modifier.height(8.dp))

        AppSizedButton(
            text = "Start Picking",
            onClick = {
                val encoded = URLEncoder.encode(selectedOrder, StandardCharsets.UTF_8.toString())
                navController.navigate("${AppRoutes.PICK_ORDER}?order=$encoded")
            },
            containerColor = OrangeButton,
            textColor = Color.Black,
            enabled = selectedOrder.isNotBlank()
        )
    }
}