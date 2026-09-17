package com.curbme.app.ui.auth

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.curbme.app.ui.components.common.SectionLabel
import com.curbme.app.ui.theme.CurbMeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBackClick: () -> Unit,
    viewModel: AccountViewModel = viewModel(),
) {
    val user by viewModel.currentUser.collectAsState()
    val error by viewModel.signInError.collectAsState()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        CurbMeTheme.colors.bgDeep,
                        Color(0xFF080B1A), // Deep subtle tint
                        CurbMeTheme.colors.bgDeep
                    )
                )
            )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Account", fontWeight = FontWeight.Bold, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (user == null || user?.isAnonymous == true) {
                    // Auth UI
                    Text(
                        text = if (isSignUpMode) "Create Account" else "Sign In",
                        color = CurbMeTheme.colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp).align(Alignment.Start)
                    )
                    Text(
                        text = if (isSignUpMode) "Sign up to sync your settings." else "Welcome back! Sign in to continue.",
                        color = CurbMeTheme.colors.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 24.dp).align(Alignment.Start)
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CurbMeTheme.colors.accentBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = CurbMeTheme.colors.textPrimary,
                            unfocusedTextColor = CurbMeTheme.colors.textPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CurbMeTheme.colors.accentBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = CurbMeTheme.colors.textPrimary,
                            unfocusedTextColor = CurbMeTheme.colors.textPrimary
                        )
                    )

                    if (!isSignUpMode) {
                        TextButton(
                            onClick = { viewModel.sendPasswordReset(email) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Forgot Password?", color = CurbMeTheme.colors.accentBlue, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (isSignUpMode) viewModel.signUpWithEmail(email, password)
                            else viewModel.signInWithEmail(email, password)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CurbMeTheme.colors.accentBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(if (isSignUpMode) "Sign Up" else "Login", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isSignUpMode) "Already have an account?" else "Don't have an account?", color = CurbMeTheme.colors.textSecondary, fontSize = 14.sp)
                        TextButton(onClick = { isSignUpMode = !isSignUpMode }) {
                            Text(if (isSignUpMode) "Login" else "Sign Up", color = CurbMeTheme.colors.accentBlue, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val activity = context as? android.app.Activity
                            if (activity != null) {
                                viewModel.signInWithGoogle(activity)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Sign in with Google", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    if (error != null) {
                        Text(error!!, color = if (error!!.contains("sent")) Color.Green else Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                    }
                } else {
                    // Profile UI
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.07f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = user!!.photoUrl,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(user!!.displayName ?: "User", color = CurbMeTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(user!!.email ?: "", color = CurbMeTheme.colors.textSecondary, fontSize = 14.sp)

                    Spacer(modifier = Modifier.height(32.dp))

                    SectionLabel("Manage Devices", modifier = Modifier.padding(horizontal = 4.dp).align(Alignment.Start))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.glassBg),
                        shape = CurbMeTheme.shapes.cardLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CurbMeTheme.colors.glassBorder, CurbMeTheme.shapes.cardLarge)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Devices, contentDescription = null, tint = CurbMeTheme.colors.accentBlue)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("This Device", fontWeight = FontWeight.SemiBold, color = CurbMeTheme.colors.textPrimary)
                                Text("Registered: ${Build.MODEL}", color = CurbMeTheme.colors.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    TextButton(
                        onClick = { viewModel.signOut() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = Color.Red)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
