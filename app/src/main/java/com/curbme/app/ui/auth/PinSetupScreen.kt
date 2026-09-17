package com.curbme.app.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.ui.theme.CurbMeTheme

/**
 * PinSetupScreen remains in Kotlin.
 * It calls the Java PrefsManager to save security credentials.
 */
@Composable
fun PinSetupScreen(onPinSaved: () -> Unit) {
    val context = LocalContext.current
    // Accessing the Java PrefsManager
    val prefs = remember { PrefsManager(context.applicationContext) }

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CurbMeTheme.colors.bgDeep)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🔒 Set Parent PIN",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = CurbMeTheme.colors.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This PIN protects the parental settings.\nKeep it secret from children.",
            fontSize = 14.sp,
            color = CurbMeTheme.colors.textSubtle,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6) pin = it },
            label = { Text("Create PIN", color = CurbMeTheme.colors.textSubtle) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CurbMeTheme.colors.accentBlue,
                unfocusedBorderColor = CurbMeTheme.colors.textMuted,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 6) confirmPin = it },
            label = { Text("Confirm PIN", color = CurbMeTheme.colors.textSubtle) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CurbMeTheme.colors.accentBlue,
                unfocusedBorderColor = CurbMeTheme.colors.textMuted,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = CurbMeTheme.colors.accentRed,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                when {
                    pin.length < 4 -> errorMessage = "PIN must be at least 4 digits"
                    pin != confirmPin -> errorMessage = "PINs do not match"
                    else -> {
                        // Persisting data via Java PrefsManager
                        prefs.savePin(pin)

                        // Critical: Ensures the App recognizes setup is done
                        prefs.isSetupComplete = true

                        Toast.makeText(context, "PIN saved ✅", Toast.LENGTH_SHORT).show()
                        onPinSaved()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CurbMeTheme.colors.accentBlue),
            enabled = pin.isNotEmpty() && confirmPin.isNotEmpty()
        ) {
            Text("Save PIN & Continue →", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}