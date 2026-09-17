package com.curbme.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.R
import com.curbme.app.ui.theme.CurbMeTheme
import kotlinx.coroutines.delay

@Composable
fun AppSplashScreen(
    onSplashFinished: () -> Unit
) {
    val isPreview = LocalInspectionMode.current
    var progressTarget by remember { mutableFloatStateOf(if (isPreview) 1f else 0f) }
    
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (!isPreview) {
            delay(50)
            progressTarget = 0.35f
            delay(120)
            progressTarget = 0.75f
            delay(150)
            progressTarget = 1.0f
            delay(180)
            onSplashFinished()
        }
    }

    LaunchedEffect(progressTarget) {
        animatedProgress.animateTo(
            targetValue = progressTarget,
            animationSpec = tween(
                durationMillis = if (progressTarget == 1.0f) 220 else 280,
                easing = FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CurbMeTheme.colors.bgDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(CurbMeTheme.colors.bgCard)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(CurbMeTheme.colors.accentCyan, CurbMeTheme.colors.accentViolet)
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "CurbMe Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "CurbMe",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                style = TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.White, CurbMeTheme.colors.accentCyan, CurbMeTheme.colors.accentViolet)
                    )
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Digital Wellbeing & Focus Engine",
                color = CurbMeTheme.colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = animatedProgress.value.coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(CurbMeTheme.colors.accentBlue, CurbMeTheme.colors.accentCyan, CurbMeTheme.colors.accentViolet)
                            )
                        )
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF04040C)
@Composable
fun AppSplashScreenPreview() {
    CurbMeTheme {
        AppSplashScreen(onSplashFinished = {})
    }
}
