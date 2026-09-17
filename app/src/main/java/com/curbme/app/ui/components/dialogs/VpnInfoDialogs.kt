package com.curbme.app.ui.components.dialogs

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.curbme.app.ui.theme.CurbMeTheme

@Composable
fun VpnKeepAliveDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        CurbMeAlertCard(
            title = "Keep VPN alive",
            message = "Some device types kill the VPN under certain circumstances. Here are some popular situations that can kill the VPN.",
            primaryLabel = "Turn it on",
            onPrimary = onConfirm,
            secondaryLabel = "Go back",
            onSecondary = onDismiss,
            accentColor = CurbMeTheme.colors.accentCyan,
            content = {
                Column {
                    listOf("Low battery", "Low CPU/memory", "Ultra-fast charging").forEach { item ->
                        Text("- $item", fontSize = 13.sp, color = CurbMeTheme.colors.textSubtle)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "By turning this on, CurbMe will check if the VPN is on when you turn on your screen. It will proceed to turn on the VPN if it is off.",
                        fontSize = 13.sp, color = CurbMeTheme.colors.textSubtle, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "NOTE: If you have pin protect turned on, you will be asked for the pin before you can turn off this feature.",
                        fontSize = 12.sp, color = CurbMeTheme.colors.textSecondary, lineHeight = 17.sp
                    )
                }
            }
        )
    }
}

@Composable
fun PreventVpnOverrideDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        CurbMeAlertCard(
            title = "Prevent VPN override",
            message = "Turning on this feature will make it impossible to use other VPNs.",
            primaryLabel = "Turn it on",
            onPrimary = onConfirm,
            secondaryLabel = "Go back",
            onSecondary = onDismiss,
            accentColor = CurbMeTheme.colors.accentCyan,
            content = {
                Text(
                    "NOTE: If you have pin protect turned on, you will be asked for the pin before you can turn off this feature.",
                    fontSize = 12.sp, color = CurbMeTheme.colors.textSecondary, lineHeight = 17.sp
                )
            }
        )
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
fun VpnDialogsPreview() {
    CurbMeTheme {
        Scaffold {
            VpnKeepAliveDialog(onConfirm = {}, onDismiss = {})
        }
    }
}
