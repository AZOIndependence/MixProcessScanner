package com.unilever.mixprocessscanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
fun MainMenuScreen(navController: NavController) {
    val navigateSingleTop: (String) -> Unit = remember(navController) {
        { route ->
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    ScreenContainer {
        MainTitle("Mix Process Scanner")
        SubTitle("Main Menu")
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AppSizedButton(
                text = "Login/Logout",
                onClick = { navigateSingleTop(AppRoutes.LOGIN_LOGOUT) },
                containerColor = BlueButton,
                textColor = Color.White
            )
            AppSizedButton(
                text = "Device Info",
                onClick = { navigateSingleTop(AppRoutes.DEVICE_INFO) },
                containerColor = BlueButton,
                textColor = Color.White
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AppSizedButton(
                text = "Container",
                onClick = { navigateSingleTop(AppRoutes.CONTAINER) },
                containerColor = BlueButton,
                textColor = Color.White
            )
            AppSizedButton(
                text = "Picking",
                onClick = { navigateSingleTop(AppRoutes.PICKING) },
                containerColor = BlueButton,
                textColor = Color.White
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AppSizedButton(
                text = "Refilling",
                onClick = { navigateSingleTop(AppRoutes.REFILLING) },
                containerColor = BlueButton,
                textColor = Color.White
            )
            AppSizedButton(
                text = "Scanning",
                onClick = { navigateSingleTop(AppRoutes.SCANNING) },
                containerColor = BlueButton,
                textColor = Color.White
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AppSizedButton(
                text = "View Log",
                onClick = { navigateSingleTop(AppRoutes.VIEW_LOG) },
                containerColor = BlueButton,
                textColor = Color.White
            )
        }
    }
}