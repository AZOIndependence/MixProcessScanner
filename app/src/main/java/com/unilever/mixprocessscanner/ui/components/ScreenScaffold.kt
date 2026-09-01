package com.unilever.mixprocessscanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ScreenBackgroundGray = Color(0xFFC2C2C2) // medium-light gray

@Composable
fun ScreenContainer(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackgroundGray)
            .systemBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun MainTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.Black,
        textAlign = TextAlign.Center
    )
}

@Composable
fun SubTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        textAlign = TextAlign.Center
    )
}