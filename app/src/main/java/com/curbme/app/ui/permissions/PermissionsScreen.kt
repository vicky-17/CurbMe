package com.curbme.app.ui.permissions

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.ui.components.cards.PermissionCard
import com.curbme.app.ui.theme.CurbMeTheme
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PermissionsScreen(
    onBackClick: () -> Unit,
    viewModel: PermissionsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkAllPermissions()
        viewModel.onDeviceAdminIntentHandled()
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onVpnPermissionResult(granted = result.resultCode == android.app.Activity.RESULT_OK)
        viewModel.onVpnPermissionIntentHandled()
    }

    val context = LocalContext.current

    LaunchedEffect(uiState.pendingDeviceAdminIntent) {
        uiState.pendingDeviceAdminIntent?.let { intent ->
            try {
                deviceAdminLauncher.launch(intent)
            } catch (_: Exception) {
                com.curbme.app.core.utils.PermissionHelper.openDeviceAdminSettings(context)
            } finally {
                viewModel.onDeviceAdminIntentHandled()
            }
        }
    }

    LaunchedEffect(uiState.pendingVpnPermissionIntent) {
        uiState.pendingVpnPermissionIntent?.let { intent ->
            vpnPermissionLauncher.launch(intent)
        }
    }

    DisposableEffect(Unit) {
        viewModel.checkAllPermissions()
        onDispose { }
    }

    PermissionsScreenContent(
        onBackClick            = onBackClick,
        isAccessibilityGranted = uiState.isAccessibilityGranted,
        isDeviceAdminGranted   = uiState.isDeviceAdminGranted,
        isUsageStatsGranted    = uiState.isUsageStatsGranted,
        isOverlayGranted       = uiState.isOverlayGranted,
        isVpnPermissionGranted = uiState.isVpnPermissionGranted,   // NEW
        isAlwaysOnVpnGranted   = uiState.isAlwaysOnVpnGranted,
        isBatteryExempt        = uiState.isBatteryExempt,
        hasNotification        = uiState.hasNotification,
        visitedAutostart       = uiState.visitedAutostart,
        visitedMiuiBgPopup     = uiState.visitedMiuiBgPopup,
        isXiaomiDevice         = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true),
        hasOemAutostart        = true,
        onRequestPermission    = { type -> viewModel.triggerPermissionIntent(type) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreenContent(
    onBackClick: () -> Unit,
    isAccessibilityGranted: Boolean,
    isDeviceAdminGranted: Boolean,
    isAlwaysOnVpnGranted: Boolean,
    isVpnPermissionGranted: Boolean,
    isUsageStatsGranted: Boolean,
    isOverlayGranted: Boolean,
    isBatteryExempt: Boolean,
    hasNotification: Boolean,
    visitedAutostart: Boolean,
    visitedMiuiBgPopup: Boolean,
    isXiaomiDevice: Boolean,
    hasOemAutostart: Boolean,
    onRequestPermission: (String) -> Unit
) {
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
                    title = {
                        Text("System Permissions", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    },
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
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Core Protection Requirements",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "CurbMe requires these system settings to successfully block distractions, restrict applications, and ensure continuous background protection.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                item { SectionHeader(title = "Critical Services") }

                item {
                    PermissionCard(
                        title = "Accessibility Service",
                        description = "Required to monitor app changes, block YouTube Shorts, and handle custom overlay screens dynamically.",
                        icon = Icons.Rounded.AccessibilityNew,
                        isGranted = isAccessibilityGranted,
                        onGrantClick = { onRequestPermission("ACCESSIBILITY") }
                    )
                }

                item {
                    PermissionCard(
                        title = "Disable Battery Optimization",
                        description = "Prevents Android from killing CurbMe background processes. Crucial for uninterrupted 24/7 rule enforcement.",
                        icon = Icons.Rounded.BatteryAlert,
                        isGranted = isBatteryExempt,
                        onGrantClick = { onRequestPermission("BATTERY_OPTIMIZATION") }
                    )
                }

                item {
                    PermissionCard(
                        title = "Display Over Other Apps",
                        description = "Allows CurbMe to instantly deploy full-screen block safety pages when a restricted app is launched.",
                        icon = Icons.Rounded.TextFormat,
                        isGranted = isOverlayGranted,
                        onGrantClick = { onRequestPermission("OVERLAY") }
                    )
                }

                if (isXiaomiDevice || hasOemAutostart) {
                    item { SectionHeader(title = "Background Stability (OEM Settings)") }
                }

                if (isXiaomiDevice) {
                    item {
                        PermissionCard(
                            title = "Background Pop-up Windows (MIUI)",
                            description = "Required on Xiaomi endpoints to allow background overlay drawing states outside interactive thread limits.",
                            icon = Icons.Rounded.Layers,
                            isGranted = visitedMiuiBgPopup,
                            onGrantClick = { onRequestPermission("MIUI_POPUP") }
                        )
                    }
                }

                if (hasOemAutostart) {
                    item {
                        PermissionCard(
                            title = "Background Autostart",
                            description = "Whitelists the companion watchdog subsystem structure to safely start execution patterns immediately upon hardware device boot tracking cascades.",
                            icon = Icons.Rounded.Autorenew,
                            isGranted = visitedAutostart,
                            onGrantClick = { onRequestPermission("AUTOSTART") }
                        )
                    }
                }

                item { SectionHeader(title = "Security & Analytics") }

                item {
                    PermissionCard(
                        title = "Device Administrator (Anti-Uninstall)",
                        description = "Activates platform-level authorization wrappers blocking regular uninstallation vectors unless verified by Parent PIN.",
                        icon = Icons.Rounded.AdminPanelSettings,
                        isGranted = isDeviceAdminGranted,
                        onGrantClick = { onRequestPermission("DEVICE_ADMIN") }
                    )
                }

                item {
                    PermissionCard(
                        title = "VPN Permission",
                        description = "Grants CurbMe permission to run the local content-filter VPN. Required before Always-On VPN can be configured.",
                        icon = Icons.Rounded.VpnKey,
                        isGranted = isVpnPermissionGranted,
                        onGrantClick = { onRequestPermission("VPN_PERMISSION") }
                    )
                }

                item {
                    PermissionCard(
                        title = "Always-On VPN Protection",
                        description = "Locks down device internet access. Ensures your custom web filters and content blocks cannot be bypassed or disabled.",
                        icon = Icons.Rounded.VpnLock,
                        isGranted = isAlwaysOnVpnGranted,
                        onGrantClick = { onRequestPermission("ALWAYS_ON_VPN") }
                    )
                }

                item {
                    PermissionCard(
                        title = "Usage Access Statistics",
                        description = "Grants read access to usage event streams to accurately monitor screen-time intervals and generate data records.",
                        icon = Icons.Rounded.QueryStats,
                        isGranted = isUsageStatsGranted,
                        onGrantClick = { onRequestPermission("USAGE_STATS") }
                    )
                }

                item {
                    PermissionCard(
                        title = "System Notifications",
                        description = "Allows the persistent tracking notification indicator status layout and pushes active real-time block notifications.",
                        icon = Icons.Rounded.NotificationsActive,
                        isGranted = hasNotification,
                        onGrantClick = { onRequestPermission("NOTIFICATIONS") }
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF3B82F6),
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
    )
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "Pending Action States", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
fun PermissionsScreenPendingPreview() {
    CurbMeTheme {
        PermissionsScreenContent(
            onBackClick            = {},
            isAccessibilityGranted = false,
            isDeviceAdminGranted   = false,
            isUsageStatsGranted    = false,
            isOverlayGranted       = false,
            isBatteryExempt        = false,
            hasNotification        = false,
            visitedAutostart       = false,
            visitedMiuiBgPopup     = false,
            isXiaomiDevice         = true,
            hasOemAutostart        = true,
            onRequestPermission    = {},
            isAlwaysOnVpnGranted   = false,
            isVpnPermissionGranted = false
        )
    }
}

@Preview(name = "Fully Activated Configuration", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
fun PermissionsScreenGrantedPreview() {
    CurbMeTheme {
        PermissionsScreenContent(
            onBackClick            = {},
            isAccessibilityGranted = true,
            isDeviceAdminGranted   = true,
            isUsageStatsGranted    = true,
            isOverlayGranted       = true,
            isBatteryExempt        = true,
            hasNotification        = true,
            visitedAutostart       = true,
            visitedMiuiBgPopup     = true,
            isXiaomiDevice         = true,
            hasOemAutostart        = true,
            onRequestPermission    = {},
            isAlwaysOnVpnGranted   = true,
            isVpnPermissionGranted = false
        )
    }
}
