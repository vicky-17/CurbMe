package com.curbme.app.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Security
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

/**
 * ActionCard designed to match the SettingsNavigationCard style.
 */
@Composable
fun ActionCard(
    title: String,
    description: String,
    icon: ImageVector,
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(CurbMeTheme.colors.accentBlue.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CurbMeTheme.colors.accentBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    color = CurbMeTheme.colors.textPrimary,
                    fontSize = 15.sp
                )
                Text(
                    text = description,
                    color = CurbMeTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = CurbMeTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview(name = "Action Card — Settings Style", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
fun ActionCardPreview() {
    MaterialTheme {
        ActionCard(
            title = "Anti-Uninstall Protection",
            description = "Manage apps that cannot be uninstalled from this device.",
            icon = Icons.Rounded.Security,
            onClick = {}
        )
    }
}
