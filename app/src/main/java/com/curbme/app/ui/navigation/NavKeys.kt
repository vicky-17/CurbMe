package com.curbme.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// ── Tab Destinations ────────────────────────────────────────────────────────
@Serializable
data object DashboardKey : NavKey

@Serializable
data object LocksKey : NavKey

@Serializable
data object SecurityKey : NavKey

@Serializable
data object SettingsKey : NavKey

// ── Full-screen Top-Level Destinations ──────────────────────────────────────
@Serializable
data object PermissionsKey : NavKey

@Serializable
data object AccountKey : NavKey

@Serializable
data object UsageBreakdownKey : NavKey
