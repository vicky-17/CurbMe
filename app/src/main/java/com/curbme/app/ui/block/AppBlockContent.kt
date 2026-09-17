package com.curbme.app.ui.block

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.ui.theme.CurbMeTheme
import com.curbme.app.ui.theme.inknutAntiqua

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun AppBlockContentPreview() {
    AppBlockContent(
        appName = "Instagram",
        reason = "Daily limit of 30 mins reached",
        planType = "TIME_LIMIT",
        onGoHome = {},
        onRegain = {}
    )
}

@Composable
fun AppBlockContent(
    appName: String,
    reason: String,
    planType: String,
    onGoHome: () -> Unit,
    onRegain: (Int) -> Unit
) {
    if (planType == "STAY_FOCUSED") {
        FullBlockUI(appName, reason, onGoHome)
    } else {
        BottomSheetBlockUI(appName, reason, onGoHome, onRegain)
    }
}

@Composable
private fun FullBlockUI(appName: String, reason: String, onGoHome: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CurbMeTheme.colors.bgDeep
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = CurbMeTheme.colors.accentCyan,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "🛡️ CurbMe",
                    color = CurbMeTheme.colors.textPrimary,
                    fontFamily = inknutAntiqua,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "$appName is restricted",
                    color = CurbMeTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = reason,
                    color = CurbMeTheme.colors.accentCyan,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(64.dp))
                Button(
                    onClick = onGoHome,
                    colors = ButtonDefaults.buttonColors(containerColor = CurbMeTheme.colors.accentCyan),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Rounded.Home, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("GO TO HOME", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BottomSheetBlockUI(
    appName: String,
    reason: String,
    onGoHome: () -> Unit,
    onRegain: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f),
            colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.bgDeep),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp, 4.dp)
                            .clip(CircleShape)
                            .background(CurbMeTheme.colors.textSecondary.copy(alpha = 0.3f))
                    )
                    Spacer(Modifier.height(32.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = CurbMeTheme.colors.accentCyan, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = appName,
                            color = CurbMeTheme.colors.textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            fontFamily = inknutAntiqua
                        )
                    }
                    
                    Text(reason, color = CurbMeTheme.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Quick Regain Options",
                        color = CurbMeTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    val options = listOf(2, 5, 10, 20)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        options.forEach { mins ->
                            OutlinedButton(
                                onClick = { onRegain(mins) },
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CurbMeTheme.colors.accentCyan),
                                border = BorderStroke(1.dp, CurbMeTheme.colors.accentCyan.copy(alpha = 0.4f))
                            ) {
                                Text("${mins}m", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                Button(
                    onClick = onGoHome,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Icon(Icons.Rounded.Home, contentDescription = null, tint = CurbMeTheme.colors.textPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text("GO TO HOME", color = CurbMeTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
