package com.curbme.app.service.overlay

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun FocusModeOverlayContent(
    durationSeconds: Int = 10,
    onFinished: () -> Unit
) {
    var remainingSeconds by remember { mutableIntStateOf(durationSeconds) }
    var progressNormalized by remember { mutableFloatStateOf(1f) }

    val animatedProgress by animateFloatAsState(
        targetValue = progressNormalized,
        animationSpec = tween(durationMillis = 80),
        label = "FocusProgress"
    )

    LaunchedEffect(durationSeconds) {
        val totalMs = durationSeconds * 1000L
        val startTime = SystemClock.elapsedRealtime()

        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - startTime
            val remainingMs = (totalMs - elapsed).coerceAtLeast(0L)
            
            val sec = (remainingMs / 1000L).toInt() + (if (remainingMs % 1000L > 0) 1 else 0)
            remainingSeconds = sec
            progressNormalized = remainingMs.toFloat() / totalMs.toFloat()

            if (remainingMs <= 0L) {
                remainingSeconds = 0
                progressNormalized = 0f
                delay(200)
                onFinished()
                break
            }
            delay(50)
        }
    }

    val displayTime = if (remainingSeconds >= 60) {
        val mins = remainingSeconds / 60
        val secs = remainingSeconds % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    } else {
        "$remainingSeconds"
    }

    val displayLabel = if (remainingSeconds >= 60) {
        "MINUTES REMAINING"
    } else if (remainingSeconds == 1) {
        "SECOND"
    } else {
        "SECONDS"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Intercept all touch events across full screen
            .pointerInput(Unit) {
                detectTapGestures { }
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E1B4B), // Deep indigo
                        Color(0xFF0F172A), // Dark slate
                        Color(0xFF020617)  // Pure dark edge
                    ),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Text(
                text = "🧘",
                fontSize = 48.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "FOCUS MODE ACTIVE",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Take a mindful pause and clear distractions.",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            // Ring Counter
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(230.dp)
            ) {
                // Outer Track
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White.copy(alpha = 0.08f),
                    strokeWidth = 14.dp,
                )

                // Active Progress Ring
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF6366F1), // Bright indigo accent
                    strokeWidth = 14.dp,
                )

                // Number/Time inside ring
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = displayTime,
                        fontSize = if (remainingSeconds >= 60) 48.sp else 64.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = displayLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF818CF8),
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "🔒 Screen locked until timer reaches zero",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
