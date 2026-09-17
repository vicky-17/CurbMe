package com.curbme.app.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.ui.auth.AccountScreen
import com.curbme.app.ui.auth.PinSetupActivity
import com.curbme.app.ui.dashboard.DashboardScreen
import com.curbme.app.ui.dashboard.UsageBreakdownScreen
import com.curbme.app.ui.dashboard.UsageViewModel
import com.curbme.app.ui.locks.LocksScreen
import com.curbme.app.ui.permissions.PermissionsScreen
import com.curbme.app.ui.security.SecurityScreen
import com.curbme.app.ui.settings.SettingsScreen

/**
 * Standard horizontal slide animation metadata for full-screen destinations.
 */
private val FullScreenTransitionMetadata = metadata {
    put(NavDisplay.TransitionKey) {
        slideInHorizontally(
            animationSpec = tween(300),
            initialOffsetX = { fullWidth -> fullWidth }
        ) togetherWith slideOutHorizontally(
            animationSpec = tween(300),
            targetOffsetX = { fullWidth -> -fullWidth }
        )
    }
    put(NavDisplay.PopTransitionKey) {
        slideInHorizontally(
            animationSpec = tween(300),
            initialOffsetX = { fullWidth -> -fullWidth }
        ) togetherWith slideOutHorizontally(
            animationSpec = tween(300),
            targetOffsetX = { fullWidth -> fullWidth }
        )
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        slideInHorizontally(
            animationSpec = tween(300),
            initialOffsetX = { fullWidth -> -fullWidth }
        ) togetherWith slideOutHorizontally(
            animationSpec = tween(300),
            targetOffsetX = { fullWidth -> fullWidth }
        )
    }
}

/**
 * Metadata for tab destinations (no sliding transition when switching tabs).
 */
private val TabTransitionMetadata = metadata {
    put(NavDisplay.TransitionKey) {
        EnterTransition.None togetherWith ExitTransition.None
    }
    put(NavDisplay.PopTransitionKey) {
        EnterTransition.None togetherWith ExitTransition.None
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        EnterTransition.None togetherWith ExitTransition.None
    }
}

fun createAppEntryProvider(
    prefs: PrefsManager,
    refreshKey: Long,
    onRefresh: () -> Unit,
    backStack: NavBackStack<NavKey>,
    usageViewModel: UsageViewModel,
    activityContext: Context
) = entryProvider {

    // ── Tab screens ──────────────────────────────────────────────────────────
    entry<DashboardKey>(metadata = TabTransitionMetadata) {
        DashboardScreen(
            prefs = prefs,
            refreshKey = refreshKey,
            onRefresh = onRefresh,
            onNavigateToUsageStats = { backStack.add(UsageBreakdownKey) },
            usageViewModel = usageViewModel
        )
    }

    entry<LocksKey>(metadata = TabTransitionMetadata) {
        LocksScreen(prefs = prefs)
    }

    entry<SecurityKey>(metadata = TabTransitionMetadata) {
        SecurityScreen(prefs = prefs)
    }

    entry<SettingsKey>(metadata = TabTransitionMetadata) {
        SettingsScreen(
            onNavigateToPermissions = { backStack.add(PermissionsKey) },
            onChangePinClick = {
                activityContext.startActivity(Intent(activityContext, PinSetupActivity::class.java))
            }
        )
    }

    // ── Full-screen destinations ─────────────────────────────────────────────
    entry<PermissionsKey>(metadata = FullScreenTransitionMetadata) {
        PermissionsScreen(
            onBackClick = { backStack.removeLastOrNull() }
        )
    }

    entry<AccountKey>(metadata = FullScreenTransitionMetadata) {
        AccountScreen(
            onBackClick = { backStack.removeLastOrNull() }
        )
    }

    entry<UsageBreakdownKey>(metadata = FullScreenTransitionMetadata) {
        UsageBreakdownScreen(
            viewModel = usageViewModel,
            onBack = { backStack.removeLastOrNull() }
        )
    }
}
