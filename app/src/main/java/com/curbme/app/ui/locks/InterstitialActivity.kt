package com.curbme.app.ui.locks

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class InterstitialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        val appName = intent.getStringExtra("app_name") ?: "This App"
        val packageName = intent.getStringExtra("package_name") ?: ""
        val limitText = intent.getStringExtra("limit_text") ?: "Restricted"

        setContent {
            InterstitialScreen(
                appName = appName,
                limitText = limitText,
                onClose = {
                    returnToHome()
                    finish()
                },
                onUseFor = { minutes ->
                    // Logic to temporarily allow app for X minutes
                    // This will be handled by the service via a broadcast or shared state
                    finish()
                }
            )
        }
    }

    private fun returnToHome() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    companion object {
        fun createIntent(context: Context, packageName: String, appName: String, limitText: String): Intent {
            return Intent(context, InterstitialActivity::class.java).apply {
                putExtra("package_name", packageName)
                putExtra("app_name", appName)
                putExtra("limit_text", limitText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
        }
    }
}

@Composable
fun InterstitialScreen(
    appName: String,
    limitText: String,
    onClose: () -> Unit,
    onUseFor: (Int) -> Unit
) {
    val ScreenBg = Color(0xFF080E1A)
    val CardBg = Color(0xFF111827)
    val AccentCyan = Color(0xFF06B6D4)
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecond = Color(0xFF64748B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.HourglassBottom,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Red.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Y", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(limitText, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(24.dp))

                LinearProgressIndicator(
                    progress = { 0.1f },
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    color = AccentCyan,
                    trackColor = Color.White.copy(alpha = 0.1f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("0m", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Spent today", color = TextSecond, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("4h", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Limit left", color = TextSecond, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        Text(
            "How long do you want to use?",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )

        Spacer(Modifier.height(24.dp))

        val options = listOf(2, 5, 10, 20)
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                options.take(2).forEach { mins ->
                    Button(
                        onClick = { onUseFor(mins) },
                        modifier = Modifier.weight(1f).padding(4.dp).height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("${mins} mins", color = TextPrimary)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                options.takeLast(2).forEach { mins ->
                    Button(
                        onClick = { onUseFor(mins) },
                        modifier = Modifier.weight(1f).padding(4.dp).height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("${mins} mins", color = TextPrimary)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3B1B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Close $appName", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}
