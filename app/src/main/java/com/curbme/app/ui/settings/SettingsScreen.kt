package com.curbme.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.curbme.app.core.utils.DataWiper
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.ui.components.cards.ActionCard
import com.curbme.app.ui.components.common.SectionLabel
import com.curbme.app.ui.theme.CurbMeTheme
import java.lang.RuntimeException
import kotlin.system.exitProcess

// Reusable local palette variables to sync with your main dashboard skin theme[cite: 1]
private val TextPrimary = Color(0xFFF1F5F9) // Main bright text readable layer[cite: 1]
private val TextSecond  = Color(0xFF64748B) // Subtitle text layer[cite: 1]

@Composable
fun SettingsScreen(
    onNavigateToPermissions: () -> Unit,
    onChangePinClick: () -> Unit
){
    val context = LocalContext.current
    var showWipeConfirm by remember { mutableStateOf(value = false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF04040c), // BgDeep
                        Color(0xFF080B1A), // Deep subtle tint
                        Color(0xFF04040c)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 0.dp, vertical = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            SectionLabel("System Diagnostics", modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(modifier = Modifier.height(8.dp))

            ActionCard(
                title = "App Permissions status",
                description = "Check or fix Accessibility, Device Admin, and Always-On VPN configurations.",
                icon = Icons.Rounded.Shield,
                onClick = onNavigateToPermissions
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel("Security", modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(modifier = Modifier.height(8.dp))

            ActionCard(
                title = "Change PIN",
                description = "Update your parent access PIN",
                icon = Icons.Rounded.Lock,
                onClick = onChangePinClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel("Developer & Support", modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(modifier = Modifier.height(8.dp))

            ActionCard(
                title = "Test Crash Report",
                description = "Triggers a controlled crash to test Firebase Crashlytics.",
                icon = Icons.Rounded.BugReport,
                onClick = {
                    throw RuntimeException("Manual Test Crash from Settings")
                }
            )

            Spacer(modifier = Modifier.height(12.dp))


        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Wipe All Data?") },
            text = { Text("This will clear your settings and database to fix crashes. The app will close.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        DataWiper.wipeAllDataAndExit(context)
                    }
                ) {
                    Text("Wipe & Close", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color.White.copy(alpha = 0.07f),
            titleContentColor = TextPrimary,
            textContentColor = TextSecond,
            modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
        )
    }
}

// --- ANDROID STUDIO SPLIT-VIEW PREVIEW ---
@Preview(name = "Settings Operational Interface", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
fun SettingsScreenPreview() {
    CurbMeTheme {
        // We pass an empty lambda block '{}' to fulfill the callback requirement for preview layout stability[cite: 1]
        SettingsScreen(
            onNavigateToPermissions = {},
            onChangePinClick = {}
        )
    }
}