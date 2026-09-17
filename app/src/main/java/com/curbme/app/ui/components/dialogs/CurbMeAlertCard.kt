package com.curbme.app.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.ui.theme.CurbMeTheme

/**
 * Shared Alert / Dialog Shell for CurbMe design system.
 */
@Composable
fun CurbMeAlertCard(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    accentColor: Color = CurbMeTheme.colors.accentBlue,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Card(
        shape = CurbMeTheme.shapes.cardLarge,
        colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.bgElevated),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CurbMeTheme.colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = CurbMeTheme.colors.textSubtle,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )

            if (content != null) {
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = CurbMeTheme.shapes.small
            ) {
                Text(primaryLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            if (secondaryLabel != null && onSecondary != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onSecondary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(secondaryLabel, color = CurbMeTheme.colors.textSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}
