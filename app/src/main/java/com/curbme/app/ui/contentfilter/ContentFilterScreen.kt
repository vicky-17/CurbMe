package com.curbme.app.ui.contentfilter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.curbme.app.R
import com.curbme.app.core.utils.TimeUtils
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.entity.WebsiteStatsEntity
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.ui.components.cards.ToggleCard
import com.curbme.app.ui.components.common.SectionLabel
import com.curbme.app.ui.theme.CurbMeTheme
import com.curbme.app.ui.components.dialogs.PinDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ContentFilterScreen controls DNS/VPN filtering, website usage, and advanced browser protection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentFilterScreen() {
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context.applicationContext) }
    val dataStoreManager = remember { DataStoreManager(context.applicationContext) }
    val viewModel = remember { ContentFilterViewModel(prefs, dataStoreManager, context.applicationContext) }

    // UI State for toggles
    var isSafeSearchEnabled by remember { mutableStateOf(prefs.isSafeSearchEnabled) }
    var isYoutubeFilterEnabled by remember { mutableStateOf(prefs.isYoutubeFilterEnabled) }
    var isFallbackEnabled by remember { mutableStateOf(prefs.isBlockUnsupportedBrowsers) }

    val isTorInstalled by viewModel.isTorBrowserInstalled.collectAsState(false)

    // Security State
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingToggle by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (showPinDialog) {
        PinDialog(
            prefs = prefs,
            onSuccess = {
                showPinDialog = false
                pendingToggle?.invoke()
                pendingToggle = null
            },
            onDismiss = {
                showPinDialog = false
                pendingToggle = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Content Filtering", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CurbMeTheme.colors.bgDeep)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CurbMeTheme.colors.bgDeep)
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionLabel("Web Protection")

            ToggleCard(
                emoji = "🌐",
                title = "Enforce Safe Search",
                subtitle = "Forces Google, Bing, and DuckDuckGo into strict filtering mode.",
                isEnabled = isSafeSearchEnabled,
                onToggle = { newValue ->
                    pendingToggle = {
                        viewModel.setSafeSearchEnabled(newValue)
                        isSafeSearchEnabled = newValue
                    }
                    showPinDialog = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel("Social Media")

            ToggleCard(
                emoji = "▶️",
                title = "YouTube Restricted Mode",
                subtitle = "Hides potentially mature videos and filters comments on YouTube.",
                isEnabled = isYoutubeFilterEnabled,
                onToggle = { newValue ->
                    pendingToggle = {
                        viewModel.setYoutubeFilterEnabled(newValue)
                        isYoutubeFilterEnabled = newValue
                    }
                    showPinDialog = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel("Advanced Browser Protection")

            ToggleCard(
                emoji = "🛡️",
                title = stringResource(R.string.unsupported_browser_fallback),
                subtitle = stringResource(R.string.unsupported_browser_fallback_desc),
                isEnabled = isFallbackEnabled,
                onToggle = { newValue ->
                    pendingToggle = {
                        viewModel.setUnsupportedBrowserFallback(newValue)
                        isFallbackEnabled = newValue
                    }
                    showPinDialog = true
                }
            )

            if (isFallbackEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.unsupported_browser_warning),
                        color = Color(0xFFFDBA74),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tor Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.bgElevated),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isTorInstalled) Color(0xFF22C55E) else Color(0xFFEAB308)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Tor Browser Support",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (isTorInstalled)
                                stringResource(R.string.tor_browser_tracking_enabled)
                            else
                                stringResource(R.string.tor_browser_not_installed),
                            color = if (isTorInstalled) Color(0xFF4ADE80) else Color(0xFFFDE047),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            var isWebTrackingEnabled by remember { mutableStateOf(true) }
            var trackedWebsites by remember { mutableStateOf<List<WebsiteStatsEntity>>(emptyList()) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val today = TimeUtils.todayKey()
                    trackedWebsites = db.websiteStatsDao().getForDate(today)
                } catch (_: Exception) {}
            }

            WebsiteUsageCard(
                websites = trackedWebsites,
                isTrackingEnabled = isWebTrackingEnabled,
                onToggleTracking = { newValue ->
                    isWebTrackingEnabled = newValue
                    scope.launch(Dispatchers.IO) {
                        dataStoreManager.updateSettings { it.copy(isWebsiteUsageTrackingEnabled = newValue) }
                    }
                },
                onBlockDomain = { domain ->
                    scope.launch(Dispatchers.IO) {
                        dataStoreManager.updateSettings { it.copy(blockedWebsites = it.blockedWebsites + domain) }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Helpful tip for the parent
            Card(
                colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.bgElevated),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = CurbMeTheme.colors.accentBlue)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "These filters apply system-wide using a local VPN tunnel.",
                        color = CurbMeTheme.colors.textSubtle,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
