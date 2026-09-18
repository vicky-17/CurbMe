package com.curbme.app.ui.dashboard

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.curbme.app.core.utils.NtpFetcher
import com.curbme.app.core.utils.PermissionHelper
import com.curbme.app.core.utils.TimeUtils
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.entity.WebsiteStatsEntity
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.data.local.prefs.Settings
import com.curbme.app.data.models.AppUsageInfo
import com.curbme.app.service.vpn.DnsVpnService
import com.curbme.app.ui.components.common.SectionLabel
import com.curbme.app.ui.sidebar.formatRemainingTime
import com.curbme.app.ui.components.dialogs.AlwaysOnVpnDialog
import com.curbme.app.ui.components.dialogs.LockSettingsDialog
import com.curbme.app.ui.components.dialogs.ShortsConfigBottomSheet
import com.curbme.app.ui.contentfilter.WebsiteUsageCard
import com.curbme.app.ui.theme.CurbMeTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    prefs: PrefsManager,
    refreshKey: Long,
    onRefresh: () -> Unit,
    onNavigateToUsageStats: () -> Unit,
    usageViewModel: UsageViewModel
) {
    val context = LocalContext.current
    val currentSettings by DataStoreManager(context).settings.collectAsState(initial = Settings())

    var safeSearchEnabled by remember { mutableStateOf(prefs.isSafeSearchEnabled) }
    var blockShorts by remember { mutableStateOf(prefs.isBlockShorts) }
    var isOverlayOn by remember { mutableStateOf(prefs.isReelCounterOverlayOn) }
    var reelCount by remember { mutableStateOf(0) }
    var reelTimeMs by remember { mutableStateOf(0L) }
    var showLockDialog by remember { mutableStateOf(false) }
    var showAlwaysOnDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showShortsConfigSheet by remember { mutableStateOf(false) }

    var trackedWebsites by remember { mutableStateOf<List<WebsiteStatsEntity>>(emptyList()) }
    var isWebTrackingEnabled by remember { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isAccessibilityGranted by remember {
        mutableStateOf(PermissionHelper.isAccessibilityEnabled(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityGranted = PermissionHelper.isAccessibilityEnabled(context)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val today = TimeUtils.todayKey()
                        val count = db.reelStatsDao().getCount(today) ?: 0
                        val stats = db.reelUsageStatsDao().getForDate(today)
                        val timeMs = stats.sumOf { it.totalTime }
                        val websites = db.websiteStatsDao().getForDate(today)
                        withContext(Dispatchers.Main) {
                            reelCount = count
                            reelTimeMs = timeMs
                            trackedWebsites = websites
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isLocked = remember(refreshKey) { PrefsManager(context).isSettingsLocked }

    LaunchedEffect(refreshKey) {
        usageViewModel.refreshStats()
        try {
            val db = AppDatabase.getDatabase(context)
            val today = TimeUtils.todayKey()
            reelCount = db.reelStatsDao().getCount(today) ?: 0
            val stats = db.reelUsageStatsDao().getForDate(today)
            reelTimeMs = stats.sumOf { it.totalTime }
            trackedWebsites = db.websiteStatsDao().getForDate(today)
        } catch (_: Exception) {}
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            safeSearchEnabled = true
            prefs.isSafeSearchEnabled = true
            context.startService(Intent(context, DnsVpnService::class.java))
        } else {
            safeSearchEnabled = false
            prefs.isSafeSearchEnabled = false
            Toast.makeText(context, "VPN Permission is required for Web Filtering", Toast.LENGTH_LONG).show()
        }
    }

    DashboardContent(
        safeSearchEnabled = safeSearchEnabled,
        blockShorts = blockShorts,
        isAccessibilityGranted = isAccessibilityGranted,
        isLocked = isLocked,
        onSafeSearchToggle = { isChecked ->
            val prefsCheck = PrefsManager(context)
            if (prefsCheck.isSettingsLocked && !isChecked) {
                Toast.makeText(context, "Settings are locked for ${formatRemainingTime(prefsCheck.lockUntil - System.currentTimeMillis())}", Toast.LENGTH_LONG).show()
                return@DashboardContent
            }
            if (isChecked) {
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) {
                    vpnPermissionLauncher.launch(vpnIntent)
                } else {
                    safeSearchEnabled = true
                    prefs.isSafeSearchEnabled = true
                    context.startService(Intent(context, DnsVpnService::class.java))
                    showAlwaysOnDialog = true
                }
            } else {
                safeSearchEnabled = false
                prefs.isSafeSearchEnabled = false
                context.startService(Intent(context, DnsVpnService::class.java).apply {
                    action = DnsVpnService.ACTION_STOP
                })
            }
        },
        onBlockShortsToggle = { newVal ->
            val prefsCheck = PrefsManager(context)
            if (prefsCheck.isSettingsLocked && !newVal) {
                Toast.makeText(context, "Settings are locked for ${formatRemainingTime(prefsCheck.lockUntil - System.currentTimeMillis())}", Toast.LENGTH_LONG).show()
                return@DashboardContent
            }
            if (newVal && !PermissionHelper.isAccessibilityEnabled(context)) {
                showAccessibilityDialog = true
                return@DashboardContent
            }
            blockShorts = newVal
            prefs.isBlockShorts = newVal
            CoroutineScope(Dispatchers.IO).launch {
                DataStoreManager(context).setBlockShorts(newVal)
            }
        },
        onGrantAccessibilityClick = {
            PermissionHelper.openAccessibilityServiceScreen(context)
        },
        onConfigureShortsClick = {
            showShortsConfigSheet = true
        },
        onLockClick = { if (!isLocked) showLockDialog = true },
        onLockdownVpnClick = { showAlwaysOnDialog = true },
        onNavigateToUsageStats = onNavigateToUsageStats,
        usageViewModel = usageViewModel,
        reelCount = reelCount,
        reelTimeMs = reelTimeMs,
        isOverlayEnabled = isOverlayOn,
        trackedWebsites = trackedWebsites,
        isWebTrackingEnabled = isWebTrackingEnabled,
        onToggleWebTracking = { newValue ->
            isWebTrackingEnabled = newValue
            val dataStore = DataStoreManager(context)
            CoroutineScope(Dispatchers.IO).launch {
                dataStore.updateSettings { it.copy(isWebsiteUsageTrackingEnabled = newValue) }
            }
        },
        onBlockDomain = { domain ->
            val dataStore = DataStoreManager(context)
            CoroutineScope(Dispatchers.IO).launch {
                dataStore.updateSettings { it.copy(blockedWebsites = it.blockedWebsites + domain) }
            }
        }
    )

    if (showAccessibilityDialog) {
        AccessibilityPromptDialog(
            onGrantClick = {
                PermissionHelper.openAccessibilityServiceScreen(context)
            },
            onDismiss = { showAccessibilityDialog = false }
        )
    }

    if (showLockDialog) {
        LockSettingsDialog(
            onConfirm = { durationMs ->
                val prefsLocal = PrefsManager(context)
                val now = System.currentTimeMillis()
                prefsLocal.lockDurationMs = durationMs
                prefsLocal.lockAnchorElapsed = SystemClock.elapsedRealtime()
                prefsLocal.lockUntil = now + durationMs
                prefsLocal.lastKnownDeviceTime = now
                prefsLocal.lockNtpOffset = Long.MIN_VALUE
                showLockDialog = false
                onRefresh()
                Thread {
                    val ntpTime = NtpFetcher.fetchNtpTime()
                    if (ntpTime > 0) {
                        val offset = ntpTime - System.currentTimeMillis()
                        prefsLocal.lockNtpOffset = offset
                        prefsLocal.lockUntil = ntpTime + durationMs
                    }
                }.start()
            },
            onDismiss = { showLockDialog = false }
        )
    }

    if (showShortsConfigSheet) {
        ShortsConfigBottomSheet(
            initialConfig = currentSettings.reelPlanConfig,
            isOverlayOn = isOverlayOn,
            onSave = { newConfig, newOverlay ->
                showShortsConfigSheet = false
                isOverlayOn = newOverlay
                prefs.isReelCounterOverlayOn = newOverlay
                CoroutineScope(Dispatchers.IO).launch {
                    DataStoreManager(context).updateSettings { it.copy(reelPlanConfig = newConfig) }
                }
            },
            onDismiss = { showShortsConfigSheet = false }
        )
    }

    if (showAlwaysOnDialog) {
        AlwaysOnVpnDialog(
            onOpenSettings = {
                showAlwaysOnDialog = false
                try {
                    context.startActivity(Intent("android.net.vpn.SETTINGS").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (e: Exception) {
                    Toast.makeText(context, "Go to Settings → Network → VPN", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { showAlwaysOnDialog = false }
        )
    }
}

@Composable
fun DashboardContent(
    safeSearchEnabled: Boolean,
    blockShorts: Boolean,
    isAccessibilityGranted: Boolean = true,
    isLocked: Boolean,
    onSafeSearchToggle: (Boolean) -> Unit,
    onBlockShortsToggle: (Boolean) -> Unit,
    onGrantAccessibilityClick: () -> Unit = {},
    onConfigureShortsClick: () -> Unit = {},
    onLockClick: () -> Unit,
    onLockdownVpnClick: () -> Unit,
    onNavigateToUsageStats: () -> Unit,
    usageViewModel: UsageViewModel? = null,
    reelCount: Int = 0,
    reelTimeMs: Long = 0L,
    isOverlayEnabled: Boolean = true,
    trackedWebsites: List<WebsiteStatsEntity> = emptyList(),
    isWebTrackingEnabled: Boolean = true,
    onToggleWebTracking: (Boolean) -> Unit = {},
    onBlockDomain: (String) -> Unit = {},
    // Preview-only parameters
    previewUsageStats: List<AppUsageInfo>? = null,
    previewComparisonPercent: Int = 0
) {
    var startEntrance by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startEntrance = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (startEntrance) 1f else 0f,
        animationSpec = tween(400, easing = LinearOutSlowInEasing),
        label = "contentAlpha"
    )

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

        var selectedDashboardTab by remember { mutableIntStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                }
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp, top = 0.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            DashboardTabs(
                selectedTab = selectedDashboardTab,
                onTabSelected = { selectedDashboardTab = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (usageViewModel != null) {
                    UsageStatsSection(
                        viewModel = usageViewModel,
                        selectedTab = selectedDashboardTab,
                        onOpenAllStats = onNavigateToUsageStats
                    )
                } else if (previewUsageStats != null) {
                    UsageStatsSection(
                        usageStats = previewUsageStats,
                        comparisonPercent = previewComparisonPercent,
                        isPermissionGranted = true,
                        onOpenAllStats = onNavigateToUsageStats,
                        onGrantPermission = {}
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                SectionLabel("Content Filters")
                Spacer(modifier = Modifier.height(8.dp))

                ShortsGuardCard(
                    isEnabled = blockShorts,
                    isAccessibilityGranted = isAccessibilityGranted,
                    reelCount = reelCount,
                    reelTimeMs = reelTimeMs,
                    isOverlayEnabled = isOverlayEnabled,
                    onToggle = onBlockShortsToggle,
                    onConfigureClick = onConfigureShortsClick,
                    onGrantPermission = onGrantAccessibilityClick
                )

                Spacer(modifier = Modifier.height(12.dp))

                WebsiteUsageCard(
                    websites = trackedWebsites,
                    isTrackingEnabled = isWebTrackingEnabled,
                    onToggleTracking = onToggleWebTracking,
                    onBlockDomain = onBlockDomain
                )

                Spacer(modifier = Modifier.height(12.dp))

                DashboardToggleCard(
                    title = "SafeSearch & Web Filter",
                    description = "Forces SafeSearch on Google/YouTube & blocks adult sites",
                    emoji = "🛡️",
                    isEnabled = safeSearchEnabled,
                    onToggle = onSafeSearchToggle
                )

                Spacer(modifier = Modifier.height(24.dp))

                DashboardActionCard(
                    title = if (isLocked) "🔒 Settings Locked" else "Lock Settings",
                    description = if (isLocked) "Settings are currently protected" else "Prevent disabling protections for a set period",
                    emoji = "⏳",
                    onClick = onLockClick
                )

                Spacer(modifier = Modifier.height(24.dp))

                DashboardActionCard(
                    title = "Lockdown VPN (Prevent Bypass)",
                    description = "Make the filter permanent so it can't be disabled",
                    emoji = "🔒",
                    onClick = onLockdownVpnClick
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun DashboardTabs(
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {}
) {
    val tabs = listOf("Daily", "Weekly")
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .padding(5.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val pillWidth = maxWidth / tabs.size
            val pillOffset by animateDpAsState(
                targetValue = pillWidth * selectedTab,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                label = "pill"
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(pillOffset.roundToPx(), 0) }
                    .width(pillWidth)
                    .fillMaxHeight()
                    .padding(1.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF6096FF).copy(alpha = 0.55f),
                                Color(0xFF6096FF).copy(alpha = 0.28f)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF96BEFF).copy(alpha = 0.55f), RoundedCornerShape(13.dp))
            )
        }
        
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color(0xFFeaf1ff) else CurbMeTheme.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortsGuardCard(
    isEnabled: Boolean,
    isAccessibilityGranted: Boolean,
    reelCount: Int,
    reelTimeMs: Long,
    isOverlayEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onConfigureClick: () -> Unit,
    onGrantPermission: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.glassBg),
        shape = CurbMeTheme.shapes.cardLarge,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CurbMeTheme.colors.glassBorder, CurbMeTheme.shapes.cardLarge)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CurbMeTheme.colors.accentSky.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📵", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Block Short Videos", fontWeight = FontWeight.Bold, color = CurbMeTheme.colors.textPrimary, fontSize = 15.sp)
                    Text("YouTube Shorts, Reels, TikTok & more", color = CurbMeTheme.colors.textSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CurbMeTheme.colors.accentBlue,
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = CurbMeTheme.colors.textMuted
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stats row (clickable to configure)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onConfigureClick),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text("Reels Scrolled Today", color = CurbMeTheme.colors.textSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$reelCount",
                        color = CurbMeTheme.colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text("Time on Reels Today", color = CurbMeTheme.colors.textSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = TimeUtils.formatDurationShort(reelTimeMs),
                        color = CurbMeTheme.colors.accentViolet,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Configure Button Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable(onClick = onConfigureClick)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚙️", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Configure Apps & Limits",
                        color = CurbMeTheme.colors.accentSky,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isOverlayEnabled) "Badge Overlay: ON →" else "Badge Overlay: OFF →",
                    color = CurbMeTheme.colors.textSecondary,
                    fontSize = 11.sp
                )
            }

            if (isEnabled && !isAccessibilityGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.accentRed.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CurbMeTheme.colors.accentRed.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = CurbMeTheme.colors.accentRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Accessibility Permission Required", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Required to detect and block short video feeds.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onGrantPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = CurbMeTheme.colors.accentRed),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardToggleCard(
    title: String, description: String, emoji: String,
    isEnabled: Boolean, onToggle: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.glassBg),
        shape = CurbMeTheme.shapes.cardLarge,
        modifier = Modifier.fillMaxWidth().border(1.dp, CurbMeTheme.colors.glassBorder, CurbMeTheme.shapes.cardLarge)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = CurbMeTheme.colors.textPrimary, fontSize = 15.sp)
                Text(description, color = CurbMeTheme.colors.textSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = isEnabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = CurbMeTheme.colors.accentBlue,
                    uncheckedThumbColor = Color(0xFF94A3B8), uncheckedTrackColor = CurbMeTheme.colors.textMuted
                )
            )
        }
    }
}

@Composable
private fun DashboardActionCard(
    title: String, description: String, emoji: String, onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.glassBg),
        shape = CurbMeTheme.shapes.cardLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).border(1.dp, CurbMeTheme.colors.glassBorder, CurbMeTheme.shapes.cardLarge)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = CurbMeTheme.colors.textPrimary, fontSize = 15.sp)
                Text(description, color = CurbMeTheme.colors.textSecondary, fontSize = 12.sp)
            }
            Text("→", color = CurbMeTheme.colors.textMuted, fontSize = 18.sp)
        }
    }
}

@Composable
private fun AccessibilityPromptDialog(
    onGrantClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Accessibility Required",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Shorts & Reels Blocking",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "To block YouTube Shorts, Instagram Reels, and TikTok, CurbMe requires Accessibility Service permission to detect short video feeds in real time.",
                    fontSize = 13.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        onGrantClick()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Grant Permission", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = Color(0xFF64748B), fontSize = 14.sp)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    val mockUsageData = listOf(
        AppUsageInfo("com.google.android.youtube", "YouTube", null, 120 * 60 * 1000L, 10),
        AppUsageInfo("com.instagram.android", "Instagram", null, 45 * 60 * 1000L, 5),
        AppUsageInfo("com.zhiliaoapp.musically", "TikTok", null, 30 * 60 * 1000L, 8)
    )

    DashboardContent(
        safeSearchEnabled = true,
        blockShorts = false,
        isLocked = false,
        onSafeSearchToggle = {},
        onBlockShortsToggle = {},
        onLockClick = {},
        onLockdownVpnClick = {},
        onNavigateToUsageStats = {},
        previewUsageStats = mockUsageData,
        previewComparisonPercent = -12
    )
}
