package com.curbme.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.ui.theme.CurbMeTheme

/**
 * OnboardingScreen remains in Kotlin.
 * This is the entry UI for Peak Iconic Innovations' Digital Monk app.
 */
@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CurbMeTheme.colors.bgDeep)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Icon or Logo Placeholder
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "App Logo",
            tint = CurbMeTheme.colors.accentBlue,
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Welcome to CurbMe",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = CurbMeTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Take control of digital well-being. Monitor app usage, filter harmful content, and block distracting shorts with ease.",
            fontSize = 16.sp,
            color = CurbMeTheme.colors.textSubtle,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        // This button triggers the navigation to PinSetupActivity
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CurbMeTheme.colors.accentBlue
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Get Started",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "By continuing, you agree to allow CurbMe to monitor device usage for parental control purposes.",
            fontSize = 12.sp,
            color = CurbMeTheme.colors.textMuted,
            textAlign = TextAlign.Center
        )
    }
}
