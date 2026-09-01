package com.unilever.mixprocessscanner.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.unilever.mixprocessscanner.ui.components.MainTitle
import com.unilever.mixprocessscanner.ui.components.ScreenContainer
import com.unilever.mixprocessscanner.ui.components.SubTitle

@Composable
fun PickOrderScreen(navController: NavController, selectedOrder: String) {
    ScreenContainer {
        MainTitle("Mix Process Scanner")
        SubTitle("Picking Orders")
        Spacer(Modifier.height(10.dp))
        Text("Selected Order: $selectedOrder")
        Spacer(Modifier.height(20.dp))
        Text("More composables to come...")
    }
}