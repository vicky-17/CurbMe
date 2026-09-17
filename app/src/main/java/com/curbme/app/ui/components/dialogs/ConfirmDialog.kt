package com.curbme.app.ui.components.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import com.curbme.app.ui.theme.CurbMeTheme

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        CurbMeAlertCard(
            title = title,
            message = message,
            primaryLabel = "Confirm",
            onPrimary = onConfirm,
            secondaryLabel = "Cancel",
            onSecondary = onDismiss,
            accentColor = CurbMeTheme.colors.accentBlue
        )
    }
}
