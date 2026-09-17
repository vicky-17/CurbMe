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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.curbme.app.core.base.BaseActivity
import com.curbme.app.core.utils.PermissionHelper
import com.curbme.app.core.utils.PersistenceManager
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.receiver.CurbMeDeviceAdminReceiver
import com.curbme.app.ui.auth.AuthViewModel
import com.curbme.app.ui.auth.PinGateScreen
import com.curbme.app.ui.auth.PinSetupActivity
import com.curbme.app.ui.permissions.PermissionsScreen
import com.curbme.app.ui.settings.SettingsScreen
import com.curbme.app.ui.sidebar.PermissionsSidebar
import com.curbme.app.ui.auth.AccountScreen
import com.curbme.app.ui.theme.CurbMeTheme
import kotlinx.coroutines.launch
import com.curbme.app.ui.dashboard.DashboardScreen
import com.curbme.app.ui.dashboard.UsageBreakdownScreen
import com.curbme.app.ui.dashboard.UsageViewModel
import com.curbme.app.ui.security.SecurityScreen
import com.curbme.app.ui.locks.LocksScreen
import com.curbme.app.ui.splash.AppSplashScreen

// ── Color palette ─────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF080E1A)
private val BgCard      = Color(0xFF111827)
private val AccentBlue  = Color(0xFF3B82F6)
private val AccentRed   = Color(0xFFEF4444)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecond  = Color(0xFF64748B)
private val TextMuted   = Color(0xFF334155)

// ── Bottom Navigation Screen Routes ───────────────────────────────────────────
enum class Screen(
    val route: String,
    val title: String,
    val icon: String,
    val contentDescription: String
) {
    DASHBOARD("dashboard", "Dashboard", "📊", "Dashboard"),
    LOCKS("locks", "Locks", "🔒️", "Locks"),
    SECURITY("security", "Security", "🛡️", "WebLock"),
    SETTINGS("settings", "Settings", "⚙️", "Settings")
}

// ── Top-level navigation destinations ─────────────────────────────────────────
// Sealed class instead of a Boolean flag — scales cleanly if you add more
// full-screen destinations later (e.g. AppLockDetail, ScheduleEditor, etc.)
sealed class AppDestination {
    /** The main shell: TopBar + BottomBar + tab content */
    object Main : AppDestination()

    /** Full-screen Permissions screen — no TopBar / BottomBar */
    object Permissions : AppDestination()

    /** Full-screen Account / Login screen */
    object Account : AppDestination()

    /** Full-screen Usage Breakdown screen */
    object UsageBreakdown : AppDestination()
}

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

    // ─────────────────────────────────────────────────────────────────────────
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
    // Main Navigation Shell
    // ─────────────────────────────────────────────────────────────────────────
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainNavigationShell(prefs: PrefsManager, onLock: () -> Unit) {
        val screens = Screen.entries
        val pagerState = rememberPagerState(pageCount = { screens.size })
        val currentScreen = screens[pagerState.currentPage]
        val scope = rememberCoroutineScope()
        
        var sidebarOpen    by remember { mutableStateOf(false) }

        // ── Single source of truth for which top-level destination is active ──
        var destination by remember { mutableStateOf<AppDestination>(AppDestination.Main) }

        val context       = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        var refreshKey       by remember { mutableLongStateOf(System.currentTimeMillis()) }
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

        // ── System back button / gesture handling ─────────────────────────────
        // Intercept back press when we're NOT on Main, navigate back instead of
        // closing the app. BackHandler is inactive when destination == Main so
        // Android's default back behaviour (exit app) is preserved.
        BackHandler(enabled = destination != AppDestination.Main) {
            destination = AppDestination.Main
        }
        
        val usageViewModel: UsageViewModel = viewModel()

        val scrimAlpha by animateFloatAsState(
            targetValue    = if (sidebarOpen) 0.6f else 0f,
            animationSpec  = tween(300),
            label          = "scrim"
        )

        // ── AnimatedContent: only ONE destination in the composition tree ─────
        // slideInHorizontally / slideOutHorizontally gives the standard Android
        // "push forward / pop back" feel. The Scaffold + Sidebar are only
        // composed when destination == Main, so PermissionsScreen gets a clean,
        // lean composition tree — fixes the Realme crash.
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                if (targetState == AppDestination.Permissions) {
                    // Navigating forward → slide in from right, old screen exits left
                    slideInHorizontally(
                        animationSpec  = tween(300),
                        initialOffsetX = { fullWidth -> fullWidth }
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(300),
                        targetOffsetX = { fullWidth -> -fullWidth }
                    )
                } else {
                    // Navigating back → slide in from left, old screen exits right
                    slideInHorizontally(
                        animationSpec  = tween(300),
                        initialOffsetX = { fullWidth -> -fullWidth }
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(300),
                        targetOffsetX = { fullWidth -> -fullWidth }
                    )
                }
            },
            label = "destination_transition"
        ) { currentDestination ->

            when (currentDestination) {

                // ── MAIN shell (Scaffold + sidebar) ───────────────────────────
                AppDestination.Main -> {
                    Box(modifier = Modifier.fillMaxSize().background(BgDeep)) {

                        Scaffold(
                            topBar = {
                                DashboardHeader(
                                    onBack = { sidebarOpen = true },
                                    onAccountClick = { destination = AppDestination.Account }
                                )
                            },
                            bottomBar = {
                                GlassNavigationBar(
                                    currentScreen = currentScreen,
                                    onScreenSelected = { screen ->
                                        scope.launch {
                                            pagerState.animateScrollToPage(screens.indexOf(screen))
                                        }
                                    }
                                )
                            },
                            containerColor = BgDeep
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    beyondViewportPageCount = 0
                                ) { page ->
                                    when (screens[page]) {
                                        Screen.DASHBOARD -> DashboardScreen(
                                            prefs           = prefs,
                                            refreshKey      = refreshKey,
                                            onRefresh       = { refreshKey = System.currentTimeMillis() },
                                            onNavigateToUsageStats = { destination = AppDestination.UsageBreakdown },
                                            usageViewModel = usageViewModel
                                        )
                                        Screen.LOCKS    -> LocksScreen(prefs = prefs)
                                        Screen.SECURITY -> SecurityScreen(prefs = prefs)
                                        Screen.SETTINGS -> SettingsScreen(
                                            onNavigateToPermissions = {
                                                destination = AppDestination.Permissions
                                            },
                                            onChangePinClick = {
                                                startActivity(Intent(this@MainActivity, PinSetupActivity::class.java))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Scrim — rendered above Scaffold, below sidebar
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

                        // Sidebar — slides in from left, globally accessible
                        AnimatedVisibility(
                            visible = sidebarOpen,
                            enter   = slideInHorizontally(initialOffsetX = { -it }),
                            exit    = slideOutHorizontally(targetOffsetX = { -it })
                        ) {
                            PermissionsSidebar(
                                prefs            = prefs,
                                permissionsState = permissionsState,
                                onRefresh        = { refreshKey = System.currentTimeMillis() },
                                onClose          = { sidebarOpen = false }
                            )
                        }
                    }
                }

                // ── PERMISSIONS full-screen (no TopBar / BottomBar) ───────────
                // Only this composable is in the tree — Scaffold is fully gone.
                AppDestination.Permissions -> {
                    PermissionsScreen(
                        onBackClick = { destination = AppDestination.Main }
                    )
                }

                // ── ACCOUNT full-screen ───────────────────────────────────────
                AppDestination.Account -> {
                    AccountScreen(
                        onBackClick = { destination = AppDestination.Main }
                    )
                }

                // ── USAGE BREAKDOWN full-screen ───────────────────────────────
                AppDestination.UsageBreakdown -> {
                    UsageBreakdownScreen(
                        viewModel = usageViewModel,
                        onBack = { destination = AppDestination.Main }
                    )
                }
            }
        }
    }

    @Composable
    fun FullScreenPlaceholder(label: String) {
        Box(
            modifier        = Modifier.fillMaxSize().background(BgDeep),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = label,
                color      = TextSecond,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    onBack: () -> Unit,
    onAccountClick: () -> Unit
) {
    val TextPrimary = Color(0xFFF1F5F9)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
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
                    tint = TextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Text(
                text = "CurbMe",
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            IconButton(onClick = onAccountClick) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = "Account",
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun GlassNavigationBar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    val screens = Screen.entries
    val selectedIndex = screens.indexOf(currentScreen)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
            .padding(6.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val pillWidth = maxWidth / screens.size
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF6096FF).copy(alpha = 0.45f),
                                Color(0xFF6096FF).copy(alpha = 0.20f)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF96BEFF).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            screens.forEachIndexed { index, screen ->
                val isSelected = selectedIndex == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onScreenSelected(screen) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = screen.icon,
                            fontSize = if (isSelected) 20.sp else 18.sp,
                            modifier = Modifier.alpha(if (isSelected) 1f else 0.7f)
                        )
                        Text(
                            text = screen.title,
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

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun MainPreview() {
    val BgDeep      = Color(0xFF080E1A)
    val BgCard      = Color(0xFF111827)
    val AccentBlue  = Color(0xFF3B82F6)
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecond  = Color(0xFF64748B)

    com.curbme.app.ui.theme.CurbMeTheme {
        Scaffold(
            topBar = {
                DashboardHeader(onBack = {}, onAccountClick = {})
            },
            bottomBar = {
                GlassNavigationBar(
                    currentScreen = Screen.DASHBOARD,
                    onScreenSelected = {}
                )
            },
            containerColor = BgDeep
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                com.curbme.app.ui.dashboard.DashboardContent(
                    safeSearchEnabled = true,
                    blockShorts = false,
                    isLocked = false,
                    onSafeSearchToggle = {},
                    onBlockShortsToggle = {},
                    onLockClick = {},
                    onLockdownVpnClick = {},
                    onNavigateToUsageStats = {},
                    previewUsageStats = listOf(
                        com.curbme.app.data.models.AppUsageInfo("com.google.android.youtube", "YouTube", null, 120 * 60 * 1000L, 10),
                        com.curbme.app.data.models.AppUsageInfo("com.instagram.android", "Instagram", null, 45 * 60 * 1000L, 5)
                    ),
                    previewComparisonPercent = -15
                )
            }
        }
    }
}
