package com.curbme.app.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.ui.theme.CurbMeTheme

@Composable
fun ToggleCard(
    emoji: String,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    isLocked: Boolean = false,
    onLockClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .border(1.dp, CurbMeTheme.colors.glassBorder, CurbMeTheme.shapes.cardLarge),
        colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.glassBg),
        shape = CurbMeTheme.shapes.cardLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji Icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isEnabled) CurbMeTheme.colors.accentCyan.copy(0.12f) else CurbMeTheme.colors.textMuted.copy(0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 18.sp)
            }

            Spacer(Modifier.width(12.dp))

            // Text Column
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CurbMeTheme.colors.textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = CurbMeTheme.colors.textSecondary, lineHeight = 15.sp)
            }

            if (onLockClick != null && isEnabled) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape)
                        .background(if (isLocked) CurbMeTheme.colors.accentAmber.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable(enabled = !isLocked) { onLockClick() }
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Lock Settings",
                        tint = if (isLocked) CurbMeTheme.colors.accentAmber else CurbMeTheme.colors.textSubtle,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Toggle Switch
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                enabled = !isLocked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CurbMeTheme.colors.accentCyan,
                    uncheckedThumbColor = CurbMeTheme.colors.textSecondary,
                    uncheckedTrackColor = CurbMeTheme.colors.textMuted,
                    disabledCheckedTrackColor = CurbMeTheme.colors.accentCyan.copy(alpha = 0.4f),
                    disabledCheckedThumbColor = Color.White.copy(alpha = 0.6f)
                )
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1322)
@Composable
fun ToggleCardPreview() {
    Column {
        ToggleCard(
            emoji = "♻️",
            title = "Enabled Toggle",
            subtitle = "This is what an active toggle looks like.",
            isEnabled = true,
            onToggle = {},
            isLocked = false
        )
        ToggleCard(
            emoji = "🔒",
            title = "Disabled Toggle",
            subtitle = "This is what an inactive toggle looks like.",
            isEnabled = false,
            onToggle = {}
        )
    }
}
