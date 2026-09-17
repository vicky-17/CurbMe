package com.curbme.app.ui.components.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.ui.theme.CurbMeTheme

/**
 * SectionLabel remains in Kotlin to support Compose UI.
 * It provides a consistent styling for headers throughout the app.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = CurbMeTheme.colors.textSubtle,
        letterSpacing = 1.sp,
        modifier = modifier.padding(vertical = 8.dp)
    )
}
