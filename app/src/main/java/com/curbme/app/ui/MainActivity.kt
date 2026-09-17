package com.curbme.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.curbme.app.core.base.BaseActivity
import com.curbme.app.core.utils.PermissionHelper
import com.curbme.app.core.utils.PersistenceManager
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.receiver.CurbMeDeviceAdminReceiver
import com.curbme.app.ui.auth.AuthViewModel
import com.curbme.app.ui.auth.PinGateScreen
import com.curbme.app.ui.auth.PinSetupActivity
import com.curbme.app.ui.dashboard.UsageViewModel
import com.curbme.app.ui.navigation.*
import com.curbme.app.ui.sidebar.PermissionsSidebar
import com.curbme.app.ui.splash.AppSplashScreen
import com.curbme.app.ui.theme.CurbMeTheme

data class PermissionsState(
    val isAccessibilityOn: Boolean,
    val isBatteryExempt: Boolean,
    val canDrawOverlays: Boolean,
    val isDeviceAdmin: Boolean,
    val hasUsageStats: Boolean,
    val hasNotification: Boolean,
    val visitedAutostart: Boolean,
    val visitedMiuiPower: Boolean,
    val visitedMiuiBgPopup: Boolean
)

class MainActivity : BaseActivity() {

    private val requestNotificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = PrefsManager(this)

        if (!prefs.hasPin()) {
            startActivity(Intent(this, PinSetupActivity::class.java))
            finish()
            return
        }

        setContent {
            CurbMeTheme {
                var showSplash by remember { mutableStateOf(true) }

                AnimatedContent(
                    targetState = showSplash,
                    transitionSpec = {
                        if (targetState) {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        } else {
                            (fadeIn(animationSpec = tween(350, easing = LinearOutSlowInEasing)) +
                                    scaleIn(animationSpec = tween(350, easing = FastOutSlowInEasing), initialScale = 0.96f))
                                .togetherWith(
                                    fadeOut(animationSpec = tween(300, easing = FastOutLinearInEasing)) +
                                            scaleOut(animationSpec = tween(300, easing = FastOutLinearInEasing), targetScale = 1.04f)
                                )
                                .using(SizeTransform(clip = false))
                        }
                    },
                    label = "splash_transition"
                ) { isSplashShowing ->
                    if (isSplashShowing) {
                        AppSplashScreen(
                            onSplashFinished = { showSplash = false }
                        )
                    } else {
                        AppContent(prefs)
                    }
                }
            }
        }
    }

    private fun getPermissionsState(context: Context): PermissionsState {
        val sharedPrefs = context.getSharedPreferences("monk_prefs", MODE_PRIVATE)
        return PermissionsState(
            isAccessibilityOn = PermissionHelper.isAccessibilityEnabled(context),
            isBatteryExempt   = PersistenceManager.isBatteryOptimizationDisabled(context),
            canDrawOverlays   = PersistenceManager.canDrawOverlays(context),
            isDeviceAdmin     = CurbMeDeviceAdminReceiver.isAdminActive(context),
            hasUsageStats     = PersistenceManager.hasUsageStatsPermission(context),
            hasNotification   = PermissionHelper.hasNotificationPermission(context),
            visitedAutostart  = sharedPrefs.getBoolean("visited_autostart", false),
            visitedMiuiPower  = sharedPrefs.getBoolean("visited_miui_power", false),
            visitedMiuiBgPopup = sharedPrefs.getBoolean("visited_miui_bg_popup", false)
        )
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionHelper.hasNotificationPermission(this)) {
                requestNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @Composable
    fun AppContent(prefs: PrefsManager) {
        var isUnlocked by remember { mutableStateOf(false) }

        LaunchedEffect(isUnlocked) {
            if (isUnlocked) askForNotificationPermission()
        }

        isUnlocked = true

        if (isUnlocked) {
            MainNavigationShell(prefs, onLock = { isUnlocked = false })
        } else {
            PinGateScreen(
                viewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return AuthViewModel(prefs) as T
                        }
                    }
                ),
                onSuccess = { isUnlocked = true }
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation 3 Main Shell
    // ─────────────────────────────────────────────────────────────────────────
    @Composable
    fun MainNavigationShell(prefs: PrefsManager, onLock: () -> Unit) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        // ── Single Navigation 3 Back Stack ────────────────────────────────────
        val backStack = rememberNavBackStack(DashboardKey)
        val currentKey = backStack.lastOrNull() ?: DashboardKey

        // Determine if current destination is a bottom-tab screen
        val isTabDestination = currentKey is DashboardKey ||
                currentKey is LocksKey ||
                currentKey is SecurityKey ||
                currentKey is SettingsKey

        var sidebarOpen by remember { mutableStateOf(false) }
        var refreshKey by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var permissionsState by remember { mutableStateOf(getPermissionsState(context)) }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    permissionsState = getPermissionsState(context)
                    refreshKey = System.currentTimeMillis()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // ── System Back Button Handling ──────────────────────────────────────
        BackHandler(enabled = backStack.size > 1) {
            backStack.removeLastOrNull()
        }

        val usageViewModel: UsageViewModel = viewModel()

        val scrimAlpha by animateFloatAsState(
            targetValue = if (sidebarOpen) 0.6f else 0f,
            animationSpec = tween(300),
            label = "scrim"
        )

        val entryProvider = remember(prefs, refreshKey, backStack, usageViewModel, context) {
            createAppEntryProvider(
                prefs = prefs,
                refreshKey = refreshKey,
                onRefresh = { refreshKey = System.currentTimeMillis() },
                backStack = backStack,
                usageViewModel = usageViewModel,
                activityContext = context
            )
        }

        Box(modifier = Modifier.fillMaxSize().background(CurbMeTheme.colors.bgDeep)) {
            Scaffold(
                topBar = {
                    if (isTabDestination) {
                        DashboardHeader(
                            onBack = { sidebarOpen = true },
                            onAccountClick = { backStack.add(AccountKey) }
                        )
                    }
                },
                bottomBar = {
                    if (isTabDestination) {
                        GlassNavigationBar(
                            currentTab = currentKey,
                            onTabSelected = { targetTab ->
                                if (backStack.lastOrNull() != targetTab) {
                                    // Replace tab entry on backstack instead of pushing
                                    val top = backStack.lastOrNull()
                                    if (top is DashboardKey || top is LocksKey || top is SecurityKey || top is SettingsKey) {
                                        backStack.removeLastOrNull()
                                    }
                                    backStack.add(targetTab)
                                }
                            }
                        )
                    }
                },
                containerColor = CurbMeTheme.colors.bgDeep
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isTabDestination) innerPadding else PaddingValues(0.dp))
                ) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = entryProvider
                    )
                }
            }

            // Scrim for Sidebar
            if (scrimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(scrimAlpha)
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures { sidebarOpen = false }
                        }
                )
            }

            // Sidebar navigation
            AnimatedVisibility(
                visible = sidebarOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                PermissionsSidebar(
                    prefs = prefs,
                    permissionsState = permissionsState,
                    onRefresh = { refreshKey = System.currentTimeMillis() },
                    onClose = { sidebarOpen = false }
                )
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    onBack: () -> Unit,
    onAccountClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(CurbMeTheme.colors.glassBg)
            .border(1.dp, CurbMeTheme.colors.glassBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Menu",
                    tint = CurbMeTheme.colors.textPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Text(
                text = "CurbMe",
                color = CurbMeTheme.colors.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            IconButton(onClick = onAccountClick) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = "Account",
                    tint = CurbMeTheme.colors.textPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private data class TabNavItem(
    val key: NavKey,
    val title: String,
    val icon: String
)

@Composable
private fun GlassNavigationBar(
    currentTab: NavKey,
    onTabSelected: (NavKey) -> Unit
) {
    val tabs = listOf(
        TabNavItem(DashboardKey, "Dashboard", "📊"),
        TabNavItem(LocksKey, "Locks", "🔒️"),
        TabNavItem(SecurityKey, "Security", "🛡️"),
        TabNavItem(SettingsKey, "Settings", "⚙️")
    )
    val selectedIndex = tabs.indexOfFirst { it.key == currentTab }.coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(64.dp)
            .clip(CurbMeTheme.shapes.cardLarge)
            .background(CurbMeTheme.colors.glassBg)
            .border(1.dp, CurbMeTheme.colors.glassBorder, CurbMeTheme.shapes.cardLarge)
            .padding(6.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val pillWidth = maxWidth / tabs.size
            val pillOffset by animateDpAsState(
                targetValue = pillWidth * selectedIndex,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
                label = "nav_pill"
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(pillOffset.roundToPx(), 0) }
                    .width(pillWidth)
                    .fillMaxHeight()
                    .clip(CurbMeTheme.shapes.card)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF6096FF).copy(alpha = 0.45f),
                                Color(0xFF6096FF).copy(alpha = 0.20f)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF96BEFF).copy(alpha = 0.45f), CurbMeTheme.shapes.card)
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = selectedIndex == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab.key) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = tab.icon,
                            fontSize = if (isSelected) 20.sp else 18.sp,
                            modifier = Modifier.alpha(if (isSelected) 1f else 0.7f)
                        )
                        Text(
                            text = tab.title,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
