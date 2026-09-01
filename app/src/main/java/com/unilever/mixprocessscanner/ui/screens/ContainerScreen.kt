package com.unilever.mixprocessscanner.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.unilever.mixprocessscanner.core.AppRoutes
import com.unilever.mixprocessscanner.ui.components.AppSizedButton
import com.unilever.mixprocessscanner.ui.components.MainTitle
import com.unilever.mixprocessscanner.ui.components.ScreenContainer
import com.unilever.mixprocessscanner.ui.components.SubTitle
import com.unilever.mixprocessscanner.ui.theme.BlueButton

@Composable
fun ContainerScreen(navController: NavController) {
    val navigateSingleTop: (String) -> Unit = remember(navController) {
        { route ->
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    ScreenContainer {
        MainTitle("Mix Process Scanner")
        SubTitle("Container")

        Spacer(Modifier.height(24.dp))

        AppSizedButton(
            text = "Info",
            onClick = { navigateSingleTop(AppRoutes.CONTAINER_INFO) },
            containerColor = BlueButton,
            textColor = Color.White
        )

        Spacer(Modifier.height(12.dp))

        AppSizedButton(
            text = "Clean/Dirty/Block/Release",
            onClick = { navigateSingleTop(AppRoutes.CONTAINER_EDIT) },
            containerColor = BlueButton,
            textColor = Color.White
        )
    }
}