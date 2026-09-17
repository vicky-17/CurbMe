package com.curbme.app.ui.locks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.curbme.app.data.local.db.entity.AppGroupEntity
import com.curbme.app.ui.theme.CurbMeTheme

@Composable
fun AppGroupCard(
    group: AppGroupEntity,
    onToggleActive: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.glassBg),
        shape = CurbMeTheme.shapes.cardLarge,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .border(1.dp, CurbMeTheme.colors.glassBorder, CurbMeTheme.shapes.cardLarge)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CurbMeTheme.colors.accentViolet.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📁", fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = group.name,
                        color = CurbMeTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (group.dailyLimitMinutes > 0) "Daily Limit: ${group.dailyLimitMinutes} mins" else "No shared limit",
                        color = CurbMeTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Switch(
                checked = group.isActive,
                onCheckedChange = onToggleActive,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CurbMeTheme.colors.accentViolet,
                    uncheckedThumbColor = Color(0xFF64748B),
                    uncheckedTrackColor = Color(0xFF1E293B)
                )
            )
        }
    }
}
