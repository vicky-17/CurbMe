package com.curbme.app.ui.security

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.curbme.app.core.utils.MonochromeHelper
import com.curbme.app.core.utils.OemAutostartHelper
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.ui.components.cards.ActionCard
import com.curbme.app.ui.components.cards.AdvancedProtectionCard
import com.curbme.app.ui.components.cards.DnsProtectionCard
import com.curbme.app.ui.components.cards.ToggleCard
import com.curbme.app.ui.components.dialogs.AntiUninstallPermissionDialog
import com.curbme.app.ui.components.dialogs.ConfirmDialog
import com.curbme.app.ui.components.dialogs.DeviceOwnerAccountErrorDialog
import com.curbme.app.ui.components.dialogs.DeviceOwnerConfirmDialog
import com.curbme.app.ui.components.dialogs.DeviceOwnerRequiredDialog
import com.curbme.app.ui.components.dialogs.LockSettingsDialog
import com.curbme.app.ui.components.dialogs.PinGateDialog
import com.curbme.app.ui.components.dialogs.PreventVpnOverrideDialog
import com.curbme.app.ui.components.dialogs.ShizukuGuideDialog
import com.curbme.app.ui.components.dialogs.VpnKeepAliveDialog
import com.curbme.app.ui.sidebar.formatRemainingTime
import com.curbme.app.ui.theme.CurbMeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SecurityScreen(prefs: PrefsManager) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val viewModel = remember { SecurityViewModel(dataStoreManager, context) }
    val scope = rememberCoroutineScope()
    
    val settings by viewModel.settings.collectAsState()

    val isPermissionBlockEnabled by viewModel.isPermissionBlockEnabled.collectAsState()
    val showConfirmDialog by viewModel.showConfirmDialog.collectAsState()

    // ── Private DNS States ──
    val isDeviceOwner = viewModel.isDeviceOwner
    val isPrivateDnsEnabled by viewModel.isPrivateDnsEnabled.collectAsState()
    val selectedHostname by viewModel.selectedHostname.collectAsState()
    val hostnameList by viewModel.hostnameList.collectAsState()
    val showAddDialog by viewModel.showAddHostnameDialog.collectAsState()
    val newHostnameInput by viewModel.newHostnameInput.collectAsState()
    val showEnableHostnameDialog by viewModel.showEnableHostnameDialog.collectAsState()
    val isApplyingPrivateDns by viewModel.isApplyingPrivateDns.collectAsState()
    val privateDnsError by viewModel.privateDnsError.collectAsState()
    val isPrivateDnsLocked by viewModel.isPrivateDnsLocked.collectAsState()
    val showAppConfirmDialog by viewModel.showAppConfirmDialog.collectAsState()

    // ── Banking Mode States ──────────────────────────────────────────────────
    val isBankingBypassEnabled by viewModel.isBankingBypassEnabled.collectAsState()
    val bankingBypassPackage by viewModel.bankingBypassPackage.collectAsState()
    val showBankingAppPicker by viewModel.showBankingAppPicker.collectAsState()
    val bankingApps by viewModel.bankingApps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()

    // ── Shizuku & Advanced States ────────────────────────────────────────────
    val isShizukuInstalled by viewModel.isShizukuInstalled.collectAsState()
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsState()
    val hasShizukuPermission by viewModel.hasShizukuPermission.collectAsState()
    val isSecureSettingsGranted by viewModel.isSecureSettingsGranted.collectAsState()
    val isAutoHealEnabled by viewModel.isAutoHealEnabled.collectAsState()
    val showShizukuGuideDialog by viewModel.showShizukuGuideDialog.collectAsState()
    val showDeviceOwnerConfirmDialog by viewModel.showDeviceOwnerConfirmDialog.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshShizukuState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    var keepVpnAlive            by remember { mutableStateOf(settings.isKeepVpnAlive) }
    var preventVpnOverride      by remember { mutableStateOf(settings.isPreventVpnOverride) }
    var antiUninstallEnabled    by remember { mutableStateOf(settings.isAntiUninstallEnabled) }
    
    LaunchedEffect(settings) {
        keepVpnAlive = settings.isKeepVpnAlive
        preventVpnOverride = settings.isPreventVpnOverride
        antiUninstallEnabled = settings.isAntiUninstallEnabled
    }

    // ── Dialog visibility ─────────────────────────────────────────────────────
    var showKeepAliveInfoDialog     by remember { mutableStateOf(false) }
    var showPreventVpnDialog        by remember { mutableStateOf(false) }
    var showDisableVpnPinDialog     by remember { mutableStateOf(false) }
    var showAntiUninstallPinDialog  by remember { mutableStateOf(false) }

    var showDnsLockDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF04040c), Color(0xFF080B1A), Color(0xFF04040c))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            SectionLabel("VPN PROTECTION")

            ToggleCard(
                emoji    = "♻️",
                title    = "Keep VPN Alive",
                subtitle = "Some phones kill VPN unexpectedly. We'll attempt to keep it on for as long as possible.",
                isEnabled = keepVpnAlive,
                onToggle = { newValue ->
                    if (settings.isSettingsLocked) {
                        Toast.makeText(
                            context,
                            "Settings are locked for ${formatRemainingTime(settings.lockUntilTimestamp - System.currentTimeMillis())}",
                            Toast.LENGTH_LONG
                        ).show()
                        return@ToggleCard
                    }
                    if (newValue) {
                        showKeepAliveInfoDialog = true
                    } else {
                        keepVpnAlive = false
                        scope.launch {
                            dataStoreManager.updateSettings { it.copy(isKeepVpnAlive = false) }
                        }
                    }
                }
            )

            HorizontalDivider(color = CurbMeTheme.colors.divider, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))

            ToggleCard(
                emoji    = "🔒",
                title    = "Prevent VPN Override",
                subtitle = "Prevents another VPN app from overriding CurbMe's filter.",
                isEnabled = preventVpnOverride,
                onToggle = { newValue ->
                    if (settings.isSettingsLocked) {
                        Toast.makeText(
                            context,
                            "Settings are locked for ${formatRemainingTime(settings.lockUntilTimestamp - System.currentTimeMillis())}",
                            Toast.LENGTH_LONG
                        ).show()
                        return@ToggleCard
                    }
                    if (newValue) {
                        showPreventVpnDialog = true
                    } else {
                        showDisableVpnPinDialog = true
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            SectionLabel("ANTI-UNINSTALL")

            AdvancedProtectionCard(
                isShizukuInstalled = isShizukuInstalled,
                isShizukuAvailable = isShizukuAvailable,
                hasShizukuPermission = hasShizukuPermission,
                isSecureSettingsGranted = isSecureSettingsGranted,
                isDeviceOwner = isDeviceOwner,
                isAutoHealEnabled = isAutoHealEnabled,
                onRequestShizukuPermission = { viewModel.requestShizukuPermission() },
                onGrantSecureSettings = { viewModel.grantSecureSettings() },
                onPromoteDeviceOwner = { viewModel.openDeviceOwnerConfirm() },
                onOpenShizukuGuide = { viewModel.openShizukuGuide() },
                onReinforceBackground = { viewModel.reinforceBackgroundExecution() },
                onToggleAutoHeal = { viewModel.onToggleAutoHeal(it) }
            )

            Spacer(Modifier.height(12.dp))

            ActionCard(
                title = "OEM Autostart & Battery Whitelist",
                description = "Prevent aggressive OEM battery killers (Xiaomi, Samsung, Vivo, Oppo, OnePlus) from stopping CurbMe.",
                icon = Icons.Rounded.Security,
                onClick = {
                    val launched = OemAutostartHelper.launchAutostartSettings(context)
                    if (!launched) {
                        Toast.makeText(context, "OEM autostart settings not required or not found on this device.", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            var isGrayscaleEnabled by remember { mutableStateOf(false) }

            ToggleCard(
                emoji    = "🎨",
                title    = "Dopamine Detox Grayscale Mode",
                subtitle = "Turns screen black & white on short-video apps to remove visual stimulation.",
                isEnabled = isGrayscaleEnabled,
                onToggle = { newValue ->
                    isGrayscaleEnabled = newValue
                    if (newValue) {
                        MonochromeHelper.enableMonochrome(context)
                    } else {
                        MonochromeHelper.disableMonochrome(context)
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            ToggleCard(
                emoji    = "🛡️",
                title    = "Anti-Uninstall Protection",
                subtitle = "Uses Accessibility Service & Device Admin to block unauthorized uninstallation, Force Stop, and settings tampering for CurbMe.",
                isEnabled = antiUninstallEnabled,
                onToggle = { newValue ->
                    if (settings.isSettingsLocked) {
                        Toast.makeText(
                            context,
                            "Settings are locked for ${formatRemainingTime(settings.lockUntilTimestamp - System.currentTimeMillis())}",
                            Toast.LENGTH_LONG
                        ).show()
                        return@ToggleCard
                    }
                    if (newValue) {
                        viewModel.onAntiUninstallToggleRequested(true) {
                            antiUninstallEnabled = true
                        }
                    } else {
                        showAntiUninstallPinDialog = true
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            ActionCard(
                title = "App Uninstall Protection",
                description = "Choose apps that cannot be uninstalled from this device.",
                icon = androidx.compose.material.icons.Icons.Rounded.Security,
                onClick = { viewModel.onOpenAppListRequested() }
            )

            SectionLabel("STRICT PERMISSION ENFORCEMENT")

            ToggleCard(
                emoji     = "⚠️",
                title     = "Strict Permission Blocking",
                subtitle  = "Block entire screen if required permissions are missing.",
                isEnabled = isPermissionBlockEnabled,
                onToggle  = { isChecked ->
                    viewModel.onToggleClicked(isChecked)
                }
            )

            if (isPermissionBlockEnabled) {
                Spacer(Modifier.height(24.dp))
                SectionLabel("BANKING COMPATIBILITY")

                ActionCard(
                    title = if (isBankingBypassEnabled) "Finish Banking Mode" else "Banking Mode (Temp Bypass)",
                    description = if (isBankingBypassEnabled) "Tap to finish and re-enable Accessibility permission." else "One-tap turn OFF accessibility to use one banking app.",
                    icon = androidx.compose.material.icons.Icons.Rounded.Security,
                    onClick = { viewModel.onBankingBypassToggleRequested(!isBankingBypassEnabled) }
                )
                
                if (isBankingBypassEnabled) {
                    Text(
                        "Currently allowing: $bankingBypassPackage\nCurbMe Accessibility is OFF. All non-banking apps are blocked.",
                        fontSize = 11.sp,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionLabel("PRIVATE DNS PROTECTION")

            val canControlPrivateDns = viewModel.canControlPrivateDns

            DnsProtectionCard(
                isEnabled = isPrivateDnsEnabled && canControlPrivateDns,
                selectedHostname = selectedHostname,
                isSettingsLocked = isPrivateDnsLocked && canControlPrivateDns,
                isTimedLockActive = settings.isSettingsLocked,
                lockUntil = settings.lockUntilTimestamp,
                isDeviceOwner = canControlPrivateDns,
                onDnsToggle = { newValue ->
                    if (!canControlPrivateDns) {
                        viewModel.showDeviceOwnerRequiredDialog()
                    } else if (!settings.isSettingsLocked) {
                        viewModel.onPrivateDnsToggleRequested(newValue)
                    }
                },
                onHostClick = {
                    if (!canControlPrivateDns) {
                        viewModel.showDeviceOwnerRequiredDialog()
                    } else {
                        viewModel.onPrivateDnsToggleRequested(true)
                    }
                },
                onSettingsLockToggle = { newValue ->
                    if (!canControlPrivateDns) {
                        viewModel.showDeviceOwnerRequiredDialog()
                    } else if (settings.isSettingsLocked && !newValue) {
                        Toast.makeText(context, "Cannot disable shield while locked.", Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.onPrivateDnsLockToggleRequested(newValue)
                    }
                },
                onLockClick = {
                    if (isPrivateDnsEnabled) {
                        showDnsLockDialog = true
                    } else {
                        Toast.makeText(context, "Turn on DNS filtering first to lock it.", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(Modifier.height(40.dp))
        }

        val showDeviceOwnerRequiredDialog by viewModel.showDeviceOwnerRequiredDialog.collectAsState()
        if (showDeviceOwnerRequiredDialog) {
            DeviceOwnerRequiredDialog(
                onDismiss = { viewModel.dismissDeviceOwnerRequiredDialog() }
            )
        }

        val showAntiUninstallPermissionDialog by viewModel.showAntiUninstallPermissionDialog.collectAsState()
        if (showAntiUninstallPermissionDialog) {
            AntiUninstallPermissionDialog(
                isAccessibilityGranted = viewModel.isAccessibilityGranted,
                isDeviceAdminGranted = viewModel.isDeviceAdminGranted,
                onDismiss = { viewModel.dismissAntiUninstallPermissionDialog() },
                onPermissionGrantedCheck = {
                    if (viewModel.isAccessibilityGranted && viewModel.isDeviceAdminGranted) {
                        antiUninstallEnabled = true
                        scope.launch {
                            dataStoreManager.updateSettings { it.copy(isAntiUninstallEnabled = true) }
                        }
                    }
                }
            )
        }

        val showAppListDialog by viewModel.showAppListDialog.collectAsState()
        if (showAppListDialog) {
            AppUninstallProtectionDialog(viewModel, prefs)
        }

        if (showBankingAppPicker) {
            BankingAppPickerDialog(
                apps = bankingApps,
                isLoading = isLoadingApps,
                onAppSelected = { viewModel.confirmBankingBypass(it) },
                onDismiss = { viewModel.dismissBankingAppPicker() }
            )
        }

        if (showShizukuGuideDialog) {
            ShizukuGuideDialog(
                onDismiss = { viewModel.dismissShizukuGuide() }
            )
        }

        if (showDeviceOwnerConfirmDialog) {
            DeviceOwnerConfirmDialog(
                onConfirm = { viewModel.promoteDeviceOwner() },
                onDismiss = { viewModel.dismissDeviceOwnerConfirm() }
            )
        }

        val deviceOwnerError by viewModel.deviceOwnerError.collectAsState()
        if (deviceOwnerError != null) {
            DeviceOwnerAccountErrorDialog(
                errorMessage = deviceOwnerError!!,
                onDismiss = { viewModel.dismissDeviceOwnerError() }
            )
        }

        if (showDnsLockDialog) {
            LockSettingsDialog(
                onConfirm = { durationMs ->
                    val now = System.currentTimeMillis()
                    scope.launch {
                        dataStoreManager.updateSettings { 
                            it.copy(
                                lockDurationMs = durationMs,
                                lockAnchorElapsed = SystemClock.elapsedRealtime(),
                                lockUntilTimestamp = now + durationMs,
                                lastKnownDeviceTime = now,
                                lockNtpOffset = Long.MIN_VALUE
                            )
                        }
                    }
                    showDnsLockDialog = false

                    scope.launch(Dispatchers.IO) {
                        val ntpTime = com.curbme.app.core.utils.NtpFetcher.fetchNtpTime()
                        if (ntpTime > 0) {
                            val offset = ntpTime - System.currentTimeMillis()
                            dataStoreManager.updateSettings {
                                it.copy(
                                    lockNtpOffset = offset,
                                    lockUntilTimestamp = ntpTime + durationMs
                                )
                            }
                        }
                    }
                },
                onDismiss = { showDnsLockDialog = false }
            )
        }

        if (showEnableHostnameDialog) {
            var tempSelection by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { viewModel.dismissEnableHostnameDialog() },
                title = { Text("Choose a Private DNS host") },
                text = {
                    Column {
                        Text("Select which secure DNS provider to use.", fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        if (isApplyingPrivateDns) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Checking host…", fontSize = 13.sp)
                            }
                        } else {
                            Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                                hostnameList.forEach { hostname ->
                                    Row(modifier = Modifier.fillMaxWidth().clickable { tempSelection = hostname }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = (tempSelection == hostname), onClick = { tempSelection = hostname })
                                        Spacer(Modifier.width(8.dp))
                                        Text(hostname, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        if (!viewModel.isDefaultHostname(hostname)) {
                                            IconButton(onClick = { viewModel.deleteHostname(hostname) }, modifier = Modifier.size(32.dp)) {
                                                Icon(imageVector = androidx.compose.material.icons.Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { viewModel.onAddHostnameClicked() }, modifier = Modifier.fillMaxWidth()) { Text("+ Add Custom Hostname") }
                        }
                    }
                },
                confirmButton = { TextButton(enabled = tempSelection != null && !isApplyingPrivateDns, onClick = { tempSelection?.let { viewModel.confirmEnablePrivateDns(it) } }) { Text("Enable") } },
                dismissButton = { TextButton(enabled = !isApplyingPrivateDns, onClick = { viewModel.dismissEnableHostnameDialog() }) { Text("Cancel") } }
            )
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAddHostnameDialog() },
                title = { Text("Add Custom Hostname") },
                text = {
                    Column {
                        Text("Enter a valid DNS hostname:", fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = newHostnameInput, onValueChange = { viewModel.updateNewHostnameInput(it) }, singleLine = true)
                    }
                },
                confirmButton = { TextButton(onClick = { viewModel.saveNewHostname() }) { Text("Add & Set") } },
                dismissButton = { TextButton(onClick = { viewModel.dismissAddHostnameDialog() }) { Text("Cancel") } }
            )
        }

        if (showConfirmDialog) {
            ConfirmDialog(
                title = "Change Security Setting",
                message = "Are you sure you want to change the permission blocking behavior?",
                onConfirm = { viewModel.confirmToggle() },
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        if (showKeepAliveInfoDialog) {
            VpnKeepAliveDialog(
                onConfirm = {
                    showKeepAliveInfoDialog = false
                    keepVpnAlive = true
                    scope.launch { dataStoreManager.updateSettings { it.copy(isKeepVpnAlive = true) } }
                },
                onDismiss = { showKeepAliveInfoDialog = false }
            )
        }

        if (showPreventVpnDialog) {
            PreventVpnOverrideDialog(
                onConfirm = {
                    showPreventVpnDialog = false
                    preventVpnOverride = true
                    scope.launch { dataStoreManager.updateSettings { it.copy(isPreventVpnOverride = true) } }
                },
                onDismiss = { showPreventVpnDialog = false }
            )
        }

        if (showDisableVpnPinDialog) {
            PinGateDialog(
                prefs = prefs,
                title = "Disable VPN Override Protection",
                message = "Enter your parent PIN.",
                onSuccess = {
                    showDisableVpnPinDialog = false
                    preventVpnOverride = false
                    scope.launch { dataStoreManager.updateSettings { it.copy(isPreventVpnOverride = false) } }
                },
                onDismiss = { showDisableVpnPinDialog = false }
            )
        }

        if (showAntiUninstallPinDialog) {
            PinGateDialog(
                prefs = prefs,
                title = "Disable Anti-Uninstall Protection",
                message = "Enter your parent PIN.",
                onSuccess = {
                    showAntiUninstallPinDialog = false
                    antiUninstallEnabled = false
                    scope.launch { dataStoreManager.updateSettings { it.copy(isAntiUninstallEnabled = false) } }
                },
                onDismiss = { showAntiUninstallPinDialog = false }
            )
        }

        showAppConfirmDialog?.let { data ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissAppConfirmDialog() },
                title = { Text("Enable Protection", color = Color.White) },
                text = { Text("Enable protection for ${data.appName}?", color = Color(0xFF94A3B8)) },
                containerColor = Color(0xFF0F172A),
                confirmButton = { TextButton(onClick = { viewModel.confirmAppToggle() }) { Text("Confirm", color = Color(0xFF3B82F6)) } },
                dismissButton = { TextButton(onClick = { viewModel.dismissAppConfirmDialog() }) { Text("Cancel", color = Color.White) } }
            )
        }
    }
}

@Composable
private fun BankingAppPickerDialog(
    apps: List<SecurityViewModel.AppInfo>,
    isLoading: Boolean,
    onAppSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = { Text("Select Banking App", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Only one app can be allowed for temporary bypass.", color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF3B82F6))
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(apps.size) { index ->
                                val app = apps[index]
                                Row(modifier = Modifier.fillMaxWidth().clickable { onAppSelected(app.packageName) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Image(painter = rememberAsyncImagePainter(app.icon), contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(app.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(app.packageName, fontSize = 11.sp, color = Color(0xFF64748B))
                                    }
                                }
                                if (index < apps.size - 1) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } }
    )
}

@Composable
private fun AppUninstallProtectionDialog(viewModel: SecurityViewModel, prefs: PrefsManager) {
    val apps by viewModel.installedApps.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var showLockDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { viewModel.dismissAppListDialog() },
        containerColor = Color(0xFF0F172A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Uninstall Protection", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showLockDialog = true }) {
                    Icon(imageVector = androidx.compose.material.icons.Icons.Rounded.Lock, contentDescription = null, tint = if (settings.isSettingsLocked) Color(0xFF3B82F6) else Color.White)
                }
            }
        },
        text = {
            Column {
                if (settings.isSettingsLocked) {
                    Text("Locked for ${formatRemainingTime(settings.lockUntilTimestamp - System.currentTimeMillis())}", color = Color(0xFF3B82F6), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text("Selected apps cannot be uninstalled.", color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))
                Box(modifier = Modifier.heightIn(max = 450.dp)) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF3B82F6))
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(apps.size) { index ->
                                val app = apps[index]
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Image(painter = rememberAsyncImagePainter(app.icon), contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(app.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(app.packageName, fontSize = 11.sp, color = Color(0xFF64748B))
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Anti-Uninstall", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                            Switch(checked = app.isUninstallBlocked, onCheckedChange = { viewModel.toggleUninstallProtection(app.packageName, it) }, modifier = Modifier.scale(0.8f))
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Block Force Stop", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                            Switch(checked = app.isForceStopBlocked, onCheckedChange = { viewModel.toggleForceStopProtection(app.packageName, it) }, modifier = Modifier.scale(0.8f))
                                        }
                                    }
                                }
                                if (index < apps.size - 1) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { viewModel.dismissAppListDialog() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))) { Text("Done", color = Color.White) } }
    )
}

@Composable
private fun SectionLabel(label: String) {
    Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155), letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
}

@Preview(name = "Security Screen", showBackground = true, backgroundColor = 0xFF080E1A, device = "spec:width=411dp,height=1600dp")
@Composable
fun SecurityScreenPreview() {
    val context = LocalContext.current
    CurbMeTheme {
        SecurityScreen(prefs = remember { PrefsManager(context) })
    }
}

@Preview(name = "Private DNS Card — Set to DNS", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
fun PrivateDnsCardSetToDnsPreview() {
    CurbMeTheme {
        Box(Modifier.padding(16.dp)) {
            DnsProtectionCard(
                isEnabled = true,
                selectedHostname = "family.adguard-dns.com",
                isSettingsLocked = true,
                isTimedLockActive = false,
                lockUntil = 0L,
                isDeviceOwner = true,
                onDnsToggle = {},
                onHostClick = {},
                onSettingsLockToggle = {},
                onLockClick = {}
            )
        }
    }
}
