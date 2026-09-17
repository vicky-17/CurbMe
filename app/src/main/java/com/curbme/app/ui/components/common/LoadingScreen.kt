package com.curbme.app.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.curbme.app.ui.theme.CurbMeTheme

/**
 * LoadingScreen remains in Kotlin to support Compose UI.
 */
@Composable
fun LoadingScreen(
    message: String = "Loading..."
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CurbMeTheme.colors.bgDeep)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = CurbMeTheme.colors.accentBlue,
            strokeWidth = 4.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = CurbMeTheme.colors.textSubtle
        )
    }
}
