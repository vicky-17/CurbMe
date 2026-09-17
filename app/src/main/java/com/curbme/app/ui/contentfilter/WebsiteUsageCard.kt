package com.curbme.app.ui.contentfilter

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
import com.curbme.app.data.local.db.entity.WebsiteStatsEntity

import com.curbme.app.ui.theme.CurbMeTheme

@Composable
fun WebsiteUsageCard(
    websites: List<WebsiteStatsEntity>,
    isTrackingEnabled: Boolean = true,
    onToggleTracking: (Boolean) -> Unit = {},
    onBlockDomain: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CurbMeTheme.colors.accentCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🌐", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "WEB DOMAIN GUARD",
                            color = CurbMeTheme.colors.accentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Browser Websites Visited Today",
                            color = CurbMeTheme.colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = isTrackingEnabled,
                    onCheckedChange = onToggleTracking,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CurbMeTheme.colors.accentCyan
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (websites.isEmpty()) {
                Text(
                    text = "No web domains tracked yet today. Open Chrome, Brave, or Firefox to track browser visits.",
                    color = CurbMeTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    websites.take(5).forEach { site ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = site.domain,
                                    color = CurbMeTheme.colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = TimeUtils.formatDurationShort(site.totalTime),
                                    color = CurbMeTheme.colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Button(
                                onClick = { onBlockDomain(site.domain) },
                                colors = ButtonDefaults.buttonColors(containerColor = CurbMeTheme.colors.accentRed.copy(alpha = 0.15f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Block", color = CurbMeTheme.colors.accentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
