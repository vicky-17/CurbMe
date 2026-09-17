package com.curbme.app.ui.components.dialogs

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

import com.curbme.app.ui.theme.CurbMeTheme

@Composable
fun MindfulPauseDialog(
    appName: String = "this app",
    onProceed: () -> Unit,
    onStayFocused: () -> Unit
) {
    var secondsRemaining by remember { mutableStateOf(5) }

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
        }
    }

    // Breathing pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Dialog(
        onDismissRequest = onStayFocused,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CurbMeTheme.colors.bgDeep)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Breathing Ring
                Box(
                    modifier = Modifier
                        .size((120 * pulseScale).dp)
                        .clip(CircleShape)
                        .background(CurbMeTheme.colors.accentCyan.copy(alpha = 0.15f))
                        .border(2.dp, CurbMeTheme.colors.accentCyan.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🧘", fontSize = 36.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Take a Deep Breath",
                    color = CurbMeTheme.colors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Are you opening $appName intentionally, or out of impulse?",
                    color = CurbMeTheme.colors.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Proceed Button (Disabled until countdown finishes)
                Button(
                    onClick = onProceed,
                    enabled = secondsRemaining == 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CurbMeTheme.colors.accentCyan,
                        disabledContainerColor = CurbMeTheme.colors.bgElevated
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = if (secondsRemaining > 0) "Proceed in ${secondsRemaining}s..." else "Continue to $appName",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (secondsRemaining == 0) Color.White else Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stay Focused Button
                OutlinedButton(
                    onClick = onStayFocused,
                    shape = RoundedCornerShape(16.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color(0xFF334155))),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "Stay Focused & Go Back",
                        color = CurbMeTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
