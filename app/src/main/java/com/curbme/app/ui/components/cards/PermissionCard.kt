package com.curbme.app.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.ui.theme.CurbMeTheme

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emoji: String? = null,
    isCritical: Boolean = false,
    onGrantClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CurbMeTheme.colors.glassBorder, CurbMeTheme.shapes.cardLarge),
        shape = CurbMeTheme.shapes.cardLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFF0D2B1A).copy(alpha = 0.6f)
            else CurbMeTheme.colors.glassBg
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isGranted) CurbMeTheme.colors.accentGreen.copy(alpha = 0.15f)
                        else if (isCritical) CurbMeTheme.colors.accentRed.copy(alpha = 0.15f)
                        else CurbMeTheme.colors.accentBlue.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) CurbMeTheme.colors.accentGreen else CurbMeTheme.colors.accentBlue,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (!emoji.isNullOrEmpty()) {
                    Text(emoji, fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CurbMeTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = CurbMeTheme.colors.textSubtle
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (isGranted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Active",
                            tint = CurbMeTheme.colors.accentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Permission Active",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CurbMeTheme.colors.accentGreen
                        )
                    }
                } else {
                    Button(
                        onClick = onGrantClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCritical) CurbMeTheme.colors.accentRed else CurbMeTheme.colors.accentBlue,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Activate Setting", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
fun PermissionCardPreview() {
    CurbMeTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            PermissionCard(
                title = "Accessibility Service",
                description = "Required for app & Shorts blocking",
                isGranted = false,
                isCritical = true,
                onGrantClick = {}
            )
        }
    }
}
