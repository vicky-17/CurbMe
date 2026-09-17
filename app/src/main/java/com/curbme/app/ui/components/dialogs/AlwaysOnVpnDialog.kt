package com.curbme.app.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.curbme.app.ui.theme.CurbMeTheme

@Composable
fun AlwaysOnVpnDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        AlwaysOnVpnDialogContent(
            onOpenSettings = onOpenSettings,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun AlwaysOnVpnDialogContent(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    CurbMeAlertCard(
        title = "🛡️\nMake Filter Permanent",
        message = "Enable \"Always-On VPN\" so the filter stays active even after a restart and can't be bypassed.",
        primaryLabel = "Open VPN Settings",
        onPrimary = onOpenSettings,
        secondaryLabel = "Maybe Later",
        onSecondary = onDismiss,
        accentColor = CurbMeTheme.colors.accentBlue,
        content = {
            Column {
                listOf(
                    "1️⃣" to "Tap 'Open VPN Settings' below",
                    "2️⃣" to "Find 'CurbMe Shield'",
                    "3️⃣" to "Tap the ⚙️ gear icon next to it",
                    "4️⃣" to "Enable 'Always-on VPN'",
                    "5️⃣" to "Optional: Enable 'Block without VPN'"
                ).forEach { (emoji, text) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emoji, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text, fontSize = 13.sp, color = CurbMeTheme.colors.textSubtle)
                    }
                }
            }
        }
    )
}

@Preview(name = "Always-On VPN Dialog", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun AlwaysOnVpnDialogPreview() {
    CurbMeTheme {
        Box(
            modifier = Modifier
                .background(CurbMeTheme.colors.bgDeep)
                .padding(24.dp)
        ) {
            AlwaysOnVpnDialogContent(onOpenSettings = {}, onDismiss = {})
        }
    }
}
