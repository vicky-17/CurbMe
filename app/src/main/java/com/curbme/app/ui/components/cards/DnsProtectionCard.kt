package com.curbme.app.ui.components.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.ui.sidebar.formatRemainingTime
import com.curbme.app.ui.theme.CurbMeTheme

@Composable
fun DnsProtectionCard(
    isEnabled: Boolean,
    selectedHostname: String,
    isSettingsLocked: Boolean,      // The Android system restriction
    isTimedLockActive: Boolean,     // Digital Monk's internal lock period
    lockUntil: Long,
    isDeviceOwner: Boolean,
    onDnsToggle: (Boolean) -> Unit,
    onHostClick: () -> Unit,
    onSettingsLockToggle: (Boolean) -> Unit,
    onLockClick: () -> Unit
) {
    val footerBg = Color(0xAD002541) // Darker for the status bar look

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, CurbMeTheme.colors.glassBorder, CurbMeTheme.shapes.cardLarge),
        colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.glassBg),
        shape = CurbMeTheme.shapes.cardLarge
    ) {
        Column {
            // --- 1. Master DNS Toggle ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) CurbMeTheme.colors.accentCyan.copy(0.1f) else CurbMeTheme.colors.bgElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🌐", fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Private DNS", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CurbMeTheme.colors.textPrimary)
                    Text("System-wide web filtering", fontSize = 11.sp, color = CurbMeTheme.colors.textSubtle)
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onDnsToggle,
                    enabled = !isTimedLockActive,
                    colors = SwitchDefaults.colors(checkedTrackColor = CurbMeTheme.colors.accentCyan)
                )
            }

            // --- 2. Sub-Settings (Visible only if enabled) ---
            AnimatedVisibility(visible = isEnabled) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(color = CurbMeTheme.colors.divider, thickness = 0.5.dp)
                    Spacer(Modifier.height(12.dp))

                    // Hostname Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isTimedLockActive) { onHostClick() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DNS Provider", modifier = Modifier.weight(1f), fontSize = 13.sp, color = CurbMeTheme.colors.textSubtle)
                        Text(selectedHostname, fontSize = 13.sp, color = if(isTimedLockActive) Color.Gray else CurbMeTheme.colors.accentCyan)
                        Icon(Icons.Rounded.ChevronRight, "", tint = CurbMeTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                    }

                    // Settings Shield Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Settings Shield", fontSize = 13.sp, color = CurbMeTheme.colors.textPrimary)
                            Text("Block Android Settings bypass", fontSize = 10.sp, color = CurbMeTheme.colors.textSecondary)
                        }
                        Switch(
                            checked = isSettingsLocked,
                            onCheckedChange = onSettingsLockToggle,
                            enabled = !isTimedLockActive || !isSettingsLocked,
                            colors = SwitchDefaults.colors(checkedTrackColor = CurbMeTheme.colors.accentCyan)
                        )
                    }
                }
            }

            // --- 3. Protection Lock Status Bar (Footer) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(footerBg)
                    .clickable(enabled = !isTimedLockActive) { onLockClick() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = if (isTimedLockActive) CurbMeTheme.colors.accentGreen else CurbMeTheme.colors.accentRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isTimedLockActive) {
                        val remaining = lockUntil - System.currentTimeMillis()
                        Text(
                            "Locked until ${formatRemainingTime(remaining)}",
                            color = CurbMeTheme.colors.accentGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            "Set Protection Lock",
                            color = CurbMeTheme.colors.accentRed,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
