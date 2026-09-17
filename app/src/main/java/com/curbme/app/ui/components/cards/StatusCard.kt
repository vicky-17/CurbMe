package com.curbme.app.ui.components.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.ui.theme.CurbMeTheme

/**
 * StatusCard remains in Kotlin.
 * It displays real-time status data provided by Java utility classes.
 */
@Composable
fun StatusCard(
    label: String,
    statusText: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = CurbMeTheme.colors.bgElevated
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    color = CurbMeTheme.colors.textSubtle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = statusText,
                    color = CurbMeTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Status Indicator Dot
            Surface(
                modifier = Modifier.size(12.dp),
                shape = CircleShape,
                color = if (isActive) CurbMeTheme.colors.accentGreen else CurbMeTheme.colors.accentRed
            ) {}
        }
    }
}

@Preview(name = "Status — Active", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
fun StatusCardActivePreview() {
    MaterialTheme {
        StatusCard(
            label = "VPN STATUS",
            statusText = "Connected & Filtering",
            isActive = true
        )
    }
}

@Preview(name = "Status — Inactive", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
fun StatusCardInactivePreview() {
    MaterialTheme {
        StatusCard(
            label = "VPN STATUS",
            statusText = "Disconnected",
            isActive = false
        )
    }
}
