package com.curbme.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.core.utils.TimeUtils

import com.curbme.app.ui.theme.CurbMeTheme

@Composable
fun ReelStatsCard(
    reelCount: Int,
    reelTimeMs: Long,
    isOverlayEnabled: Boolean,
    onOverlayToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding( vertical = 6.dp)
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CurbMeTheme.colors.accentSky.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎬", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "REELS & SHORTS GUARD",
                            color = CurbMeTheme.colors.accentSky,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Short Video Scroll Counter",
                            color = CurbMeTheme.colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = isOverlayEnabled,
                    onCheckedChange = onOverlayToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CurbMeTheme.colors.accentSky,
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Reel Count
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text("Reels Scrolled", color = CurbMeTheme.colors.textSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$reelCount",
                        color = CurbMeTheme.colors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Reel Time
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text("Time on Reels", color = CurbMeTheme.colors.textSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = TimeUtils.formatDurationShort(reelTimeMs),
                        color = CurbMeTheme.colors.accentViolet,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle
            Text(
                text = "Monitors scrolling transitions in YouTube Shorts, Instagram Reels, Snapchat Spotlight, Facebook Reels, and TikTok.",
                color = CurbMeTheme.colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}
