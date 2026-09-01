package com.unilever.mixprocessscanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.unilever.mixprocessscanner.core.AppRoutes
import com.unilever.mixprocessscanner.core.GlobalState
import com.unilever.mixprocessscanner.ui.components.AppSizedButton
import com.unilever.mixprocessscanner.ui.components.MainTitle
import com.unilever.mixprocessscanner.ui.components.ScreenContainer
import com.unilever.mixprocessscanner.ui.components.SubTitle
import com.unilever.mixprocessscanner.ui.theme.OrangeButton
import com.unilever.mixprocessscanner.viewmodel.AppViewModel

@Composable
fun LoginLogoutScreen(navController: NavController) {
    val currentUser by GlobalState.currentUser.collectAsState()
    val loggedIn by GlobalState.loggedIn.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }

    ScreenContainer {
        MainTitle("Mix Process Scanner")
        SubTitle("Login/Logout")

        Spacer(Modifier.height(20.dp))
        Text("Current User:")
        Text(currentUser.ifBlank { "N/A" })

        Spacer(Modifier.height(8.dp))
        Text("Status: ${if (loggedIn) "Logged In" else "Logged Out"}")

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AppSizedButton(
                text = "Login",
                onClick = { navController.navigate(AppRoutes.LOGIN) },
                containerColor = OrangeButton,
                textColor = Color.Black
            )

            AppSizedButton(
                text = "Logout",
                onClick = { showConfirm = true },
                containerColor = OrangeButton,
                textColor = Color.Black,
                enabled = loggedIn
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Confirm Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                AppSizedButton(
                    text = "Confirm",
                    onClick = {
                        showConfirm = false
                        AppViewModel.logout()
                    },
                    containerColor = OrangeButton,
                    textColor = Color.Black
                )
            },
            dismissButton = {
                AppSizedButton(
                    text = "Cancel",
                    onClick = { showConfirm = false },
                    containerColor = OrangeButton,
                    textColor = Color.Black
                )
            }
        )
    }
}