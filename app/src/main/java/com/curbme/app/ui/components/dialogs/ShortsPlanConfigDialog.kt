package com.curbme.app.ui.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.curbme.app.data.models.ReelPlanConfig
import com.curbme.app.data.models.ShortsBlockMode

import com.curbme.app.ui.theme.CurbMeTheme

@Composable
fun ShortsPlanConfigDialog(
    initialConfig: ReelPlanConfig = ReelPlanConfig(),
    onSave: (ReelPlanConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(initialConfig.mode) }
    var isDisplayBadge by remember { mutableStateOf(initialConfig.isDisplayReelCounterOverlay) }
    var selectedApps by remember { mutableStateOf(initialConfig.enabledTargetApps) }
    var timeLimitMins by remember { mutableFloatStateOf(initialConfig.dailyTimeLimitMinutes.toFloat()) }
    var reelCapCount by remember { mutableFloatStateOf(initialConfig.dailyReelCountLimit.toFloat()) }

    val allApps = listOf(
        "com.google.android.youtube" to "YouTube Shorts",
        "com.instagram.android" to "Instagram Reels",
        "com.snapchat.android" to "Snapchat Spotlight",
        "com.facebook.katana" to "Facebook Reels",
        "com.zhiliaoapp.musically" to "TikTok"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.bgDeep),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CurbMeTheme.colors.accentSky.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚙️", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SHORTS PROTECTION PLAN",
                            color = CurbMeTheme.colors.accentSky,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Configure Protection Mode & Limits",
                            color = CurbMeTheme.colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Floating Reel Counter Badge Toggle (Curbox feature)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Floating Reel Badge", color = CurbMeTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Display live scroll count overlay on video apps", color = CurbMeTheme.colors.textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isDisplayBadge,
                        onCheckedChange = { isDisplayBadge = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CurbMeTheme.colors.accentSky
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("Protection Mode", color = CurbMeTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                // Mode Selector Chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShortsBlockMode.entries.forEach { mode ->
                        val isSelected = mode == selectedMode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) CurbMeTheme.colors.accentSky.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f),
                            border = if (isSelected) BorderStroke(1.dp, CurbMeTheme.colors.accentSky) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMode = mode }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (mode) {
                                        ShortsBlockMode.COMPLETE_BLOCK -> "🚫 Complete Block (All Apps)"
                                        ShortsBlockMode.SELECTIVE_APPS -> "🎯 Selective Apps Only"
                                        ShortsBlockMode.DAILY_TIME_LIMIT -> "⏳ Daily Time Limiter"
                                        ShortsBlockMode.REEL_COUNT_LIMIT -> "🎬 Reel Scroll Count Limit"
                                        ShortsBlockMode.SCHEDULED_WINDOWS -> "⏰ Scheduled Allowed Window"
                                    },
                                    color = if (isSelected) CurbMeTheme.colors.accentSky else CurbMeTheme.colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Mode 3: Daily Time Limit Slider
                if (selectedMode == ShortsBlockMode.DAILY_TIME_LIMIT) {
                    Text("Daily Short Video Limit: ${timeLimitMins.toInt()} mins", color = CurbMeTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = timeLimitMins,
                        onValueChange = { timeLimitMins = it },
                        valueRange = 5f..120f,
                        steps = 22,
                        colors = SliderDefaults.colors(thumbColor = CurbMeTheme.colors.accentSky, activeTrackColor = CurbMeTheme.colors.accentSky)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Mode 4: Reel Count Cap Slider
                if (selectedMode == ShortsBlockMode.REEL_COUNT_LIMIT) {
                    Text("Daily Reel Scroll Cap: ${reelCapCount.toInt()} reels", color = CurbMeTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = reelCapCount,
                        onValueChange = { reelCapCount = it },
                        valueRange = 5f..100f,
                        steps = 18,
                        colors = SliderDefaults.colors(thumbColor = CurbMeTheme.colors.accentViolet, activeTrackColor = CurbMeTheme.colors.accentViolet)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Target Apps Selector
                Text("Target Video Apps", color = CurbMeTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                allApps.forEach { (pkg, label) ->
                    val isChecked = selectedApps.contains(pkg)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedApps = if (isChecked) selectedApps - pkg else selectedApps + pkg
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                selectedApps = if (checked) selectedApps + pkg else selectedApps - pkg
                            },
                            colors = CheckboxDefaults.colors(checkedColor = CurbMeTheme.colors.accentSky)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, color = CurbMeTheme.colors.textPrimary, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = CurbMeTheme.colors.textSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val newConfig = initialConfig.copy(
                                mode = selectedMode,
                                isDisplayReelCounterOverlay = isDisplayBadge,
                                enabledTargetApps = selectedApps,
                                dailyTimeLimitMinutes = timeLimitMins.toInt(),
                                dailyReelCountLimit = reelCapCount.toInt()
                            )
                            onSave(newConfig)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CurbMeTheme.colors.accentSky),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Plan", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
