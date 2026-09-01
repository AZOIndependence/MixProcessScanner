package com.unilever.mixprocessscanner.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.unilever.mixprocessscanner.core.GlobalState
import com.unilever.mixprocessscanner.ui.components.AppSizedButton
import com.unilever.mixprocessscanner.ui.components.MainTitle
import com.unilever.mixprocessscanner.ui.components.ScreenContainer
import com.unilever.mixprocessscanner.ui.components.SubTitle
import com.unilever.mixprocessscanner.ui.theme.OrangeButton
import com.unilever.mixprocessscanner.viewmodel.AppViewModel

@Composable
fun LoginScreen(navController: NavController) {
    val username by GlobalState.currentUser.collectAsState()
    val password by GlobalState.currentPassword.collectAsState()
    val loggedIn by GlobalState.loggedIn.collectAsState()

    val requester = remember { FocusRequester() }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { requester.requestFocus() }

    // Navigate back after successful login
    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            isSubmitting = false
            navController.popBackStack()
        }
    }

    ScreenContainer {
        MainTitle("Mix Process Scanner")
        SubTitle("Login")
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { GlobalState.currentUser.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(requester),
            label = { Text("Username") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            enabled = !isSubmitting
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { GlobalState.currentPassword.value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !isSubmitting
        )

        Spacer(Modifier.height(20.dp))

        AppSizedButton(
            text = if (isSubmitting) "Submitting..." else "Submit",
            onClick = {
                if (isSubmitting) return@AppSizedButton
                if (username.isBlank() || password.isBlank()) return@AppSizedButton
                isSubmitting = true
                AppViewModel.submitLogin()
            },
            containerColor = OrangeButton,
            textColor = Color.Black,
            enabled = !isSubmitting && username.isNotBlank() && password.isNotBlank()
        )
    }
}