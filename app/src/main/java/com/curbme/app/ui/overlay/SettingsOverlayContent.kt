package com.curbme.app.ui.overlay

import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class SettingsOverlayStage {
    HIDE,
    STRIP,
    HALF,
    FULL
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun SettingsBlockOverlay(
    stage: SettingsOverlayStage,
    onGoHome: () -> Unit = {}
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val targetHeight: Dp = when (stage) {
        SettingsOverlayStage.HIDE -> 0.dp
        SettingsOverlayStage.STRIP -> 10.dp
        SettingsOverlayStage.HALF  -> 70.dp //screenHeight * 0.8f
        SettingsOverlayStage.FULL  -> screenHeight
    }

    Box(
        modifier = Modifier
            .background(Color.Red)
            .animateContentSize(
                animationSpec = tween(durationMillis = 7000), // <-- INCREASE THIS
                alignment = Alignment.BottomCenter
            )
            .height(targetHeight)
            .fillMaxWidth()

    )
    {
        // Inner content removed for testing
    }
}











// ── INTERACTIVE PREVIEW ────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFEEEEEE)
@Composable
fun SettingsBlockOverlayAnimationPreview() {
    // 1. Create a state variable to hold the current stage
    var currentStage by remember { mutableStateOf(SettingsOverlayStage.HALF) }

    Box(modifier = Modifier.fillMaxSize()) {

        // 2. Add some mock background content and buttons to trigger state changes
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Mock Settings App Behind Overlay", color = Color.Gray)

            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { currentStage = SettingsOverlayStage.HIDE }) { Text("HIDE") }
                Button(onClick = { currentStage = SettingsOverlayStage.STRIP }) { Text("STRIP") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { currentStage = SettingsOverlayStage.HALF }) { Text("HALF") }
                Button(onClick = { currentStage = SettingsOverlayStage.FULL }) { Text("FULL") }
            }
        }

        // 3. Place your overlay at the bottom of the Box
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            SettingsBlockOverlay(stage = currentStage)
        }
    }
}