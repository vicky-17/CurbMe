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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.data.models.ReelPlanConfig
import com.curbme.app.data.models.ShortsBlockMode

import com.curbme.app.ui.theme.CurbMeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsConfigBottomSheet(
    initialConfig: ReelPlanConfig = ReelPlanConfig(),
    isOverlayOn: Boolean = true,
    onSave: (newConfig: ReelPlanConfig, newOverlayOn: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var sheetWindowY by remember { mutableFloatStateOf(0f) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            if (targetValue == SheetValue.Hidden) {
                // Require dragging at least 800px down on screen before allowing swipe-to-dismiss.
                // Prevents accidental slight swipes from closing the sheet.
                sheetWindowY > 500f || sheetWindowY == 0f
            } else {
                true
            }
        }
    )

    var selectedMode by remember { mutableStateOf(initialConfig.mode) }
    var isDisplayBadge by remember { mutableStateOf(isOverlayOn) }
    var selectedApps by remember { mutableStateOf(initialConfig.enabledTargetApps) }
    var timeLimitMins by remember { mutableFloatStateOf(initialConfig.dailyTimeLimitMinutes.coerceAtLeast(5).toFloat()) }
    var reelCapCount by remember { mutableFloatStateOf(initialConfig.dailyReelCountLimit.coerceAtLeast(5).toFloat()) }

    var startHour by remember { mutableIntStateOf(initialConfig.startHour) }
    var startMinute by remember { mutableIntStateOf(initialConfig.startMinute) }
    var endHour by remember { mutableIntStateOf(initialConfig.endHour) }
    var endMinute by remember { mutableIntStateOf(initialConfig.endMinute) }

    val allApps = listOf(
        "com.google.android.youtube" to "YouTube Shorts 🎬",
        "com.instagram.android" to "Instagram Reels 📸",
        "com.snapchat.android" to "Snapchat Spotlight 👻",
        "com.facebook.katana" to "Facebook Reels 📘",
        "com.zhiliaoapp.musically" to "TikTok 🎵",
        "com.reddit.frontpage" to "Reddit Reels 🤖",
        "com.twitter.android" to "X / Twitter Videos 🪶",
        "com.pinterest" to "Pinterest Watch 📌",
        "in.mohabat.app" to "Moj Shorts 📱",
        "com.eterno.shortvideos" to "Josh Videos 🎥"
    )

    fun formatAmPm(hour: Int, minute: Int): String {
        val amPm = if (hour >= 12) "PM" else "AM"
        val h = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val m = if (minute < 10) "0$minute" else "$minute"
        return "$h:$m $amPm"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CurbMeTheme.colors.bgDeep,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    sheetWindowY = coordinates.positionInWindow().y
                }
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CurbMeTheme.colors.accentSky.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙️", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SHORTS & REELS SETTINGS",
                        color = CurbMeTheme.colors.accentSky,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Configure App Targets, Overlay & Protection Limits",
                        color = CurbMeTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Floating Badge Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Floating Reel Counter Badge", color = CurbMeTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Live scroll badge overlay when browsing short videos", color = CurbMeTheme.colors.textSecondary, fontSize = 11.sp)
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

            Text("Protection Mode", color = CurbMeTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShortsBlockMode.entries.forEach { mode ->
                    val isSelected = mode == selectedMode
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) CurbMeTheme.colors.accentSky.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f),
                        border = if (isSelected) BorderStroke(1.dp, CurbMeTheme.colors.accentSky) else BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = mode }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (mode) {
                                    ShortsBlockMode.COMPLETE_BLOCK -> "🚫 Complete Block (All Short Video Apps)"
                                    ShortsBlockMode.SELECTIVE_APPS -> "🎯 Selective App Blocking"
                                    ShortsBlockMode.DAILY_TIME_LIMIT -> "⏳ Daily Time Limiter"
                                    ShortsBlockMode.REEL_COUNT_LIMIT -> "🎬 Reel Scroll Count Cap"
                                    ShortsBlockMode.SCHEDULED_WINDOWS -> "⏰ Scheduled Allowed Hours"
                                },
                                color = if (isSelected) CurbMeTheme.colors.accentSky else CurbMeTheme.colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (selectedMode == ShortsBlockMode.DAILY_TIME_LIMIT) {
                Text("Daily Short Video Time Limit: ${timeLimitMins.toInt()} mins", color = CurbMeTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = timeLimitMins,
                    onValueChange = { timeLimitMins = it },
                    valueRange = 5f..120f,
                    steps = 22,
                    colors = SliderDefaults.colors(thumbColor = CurbMeTheme.colors.accentSky, activeTrackColor = CurbMeTheme.colors.accentSky)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (selectedMode == ShortsBlockMode.REEL_COUNT_LIMIT) {
                Text("Daily Reel Scroll Limit: ${reelCapCount.toInt()} reels", color = CurbMeTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = reelCapCount,
                    onValueChange = { reelCapCount = it },
                    valueRange = 5f..100f,
                    steps = 18,
                    colors = SliderDefaults.colors(thumbColor = CurbMeTheme.colors.accentViolet, activeTrackColor = CurbMeTheme.colors.accentViolet)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (selectedMode == ShortsBlockMode.SCHEDULED_WINDOWS) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text("⏰ Allowed Time Schedule", color = CurbMeTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Short videos are allowed ONLY between start and end time. Outside these hours, they will be blocked.",
                        color = CurbMeTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Start Allowed Time", color = CurbMeTheme.colors.textSecondary, fontSize = 11.sp)
                            Text(formatAmPm(startHour, startMinute), color = CurbMeTheme.colors.accentSky, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("End Allowed Time", color = CurbMeTheme.colors.textSecondary, fontSize = 11.sp)
                            Text(formatAmPm(endHour, endMinute), color = CurbMeTheme.colors.accentSky, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Start Hour: ${formatAmPm(startHour, 0)}", color = CurbMeTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = startHour.toFloat(),
                        onValueChange = { startHour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 22,
                        colors = SliderDefaults.colors(thumbColor = CurbMeTheme.colors.accentSky, activeTrackColor = CurbMeTheme.colors.accentSky)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("End Hour: ${formatAmPm(endHour, 0)}", color = CurbMeTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = endHour.toFloat(),
                        onValueChange = { endHour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 22,
                        colors = SliderDefaults.colors(thumbColor = CurbMeTheme.colors.accentSky, activeTrackColor = CurbMeTheme.colors.accentSky)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            Text("Target Video Apps", color = CurbMeTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (selectedMode == ShortsBlockMode.COMPLETE_BLOCK) 
                    "Complete Block mode applies to all supported apps automatically." 
                else 
                    "Select which apps to enforce rules on",
                color = CurbMeTheme.colors.textSecondary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(8.dp)
            ) {
                allApps.forEach { (pkg, label) ->
                    val isChecked = selectedApps.contains(pkg)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedApps = if (isChecked) selectedApps - pkg else selectedApps + pkg
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
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
                        Text(label, color = CurbMeTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Action
            Button(
                onClick = {
                    val updatedConfig = initialConfig.copy(
                        mode = selectedMode,
                        isDisplayReelCounterOverlay = isDisplayBadge,
                        enabledTargetApps = selectedApps,
                        dailyTimeLimitMinutes = timeLimitMins.toInt(),
                        dailyReelCountLimit = reelCapCount.toInt(),
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute
                    )
                    onSave(updatedConfig, isDisplayBadge)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CurbMeTheme.colors.accentSky),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Save Configuration", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
