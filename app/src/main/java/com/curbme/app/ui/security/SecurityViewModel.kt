package com.curbme.app.ui.security

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curbme.app.core.deviceowner.DevicePolicyHelper
import com.curbme.app.core.utils.PermissionHelper
import com.curbme.app.core.utils.ShizukuManager
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.entity.FocusSessionEntity
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.data.local.prefs.Settings as MonkSettings
import com.curbme.app.service.WatchdogService
import com.curbme.app.service.accessibility.GuardianAccessibilityService
import com.curbme.app.service.overlay.FocusModeOverlayService
import com.curbme.app.service.overlay.FocusSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SecurityViewModel(
    private val dataStoreManager: DataStoreManager,
    private val context: Context
) : ViewModel() {

    private val _settings = MutableStateFlow(MonkSettings())
    val settings: StateFlow<MonkSettings> = _settings.asStateFlow()

    // ── Active Focus Session State ────────────────────────────────────────────
    val activeFocusSession: StateFlow<FocusSessionEntity?> = AppDatabase.getDatabase(context)
        .focusSessionDao()
        .getActiveSessionFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Strict Permission Blocking State ──────────────────────────────────────
    private val _isPermissionBlockEnabled = MutableStateFlow(false)
    val isPermissionBlockEnabled: StateFlow<Boolean> = _isPermissionBlockEnabled.asStateFlow()

    private val _showConfirmDialog = MutableStateFlow(false)
    val showConfirmDialog: StateFlow<Boolean> = _showConfirmDialog.asStateFlow()
    private var pendingToggleState = true

    // ── Private DNS States ────────────────────────────────────────────────────
    private val _isPrivateDnsEnabled = MutableStateFlow(false)
    val isPrivateDnsEnabled: StateFlow<Boolean> = _isPrivateDnsEnabled.asStateFlow()

    private val _selectedHostname = MutableStateFlow("")
    val selectedHostname: StateFlow<String> = _selectedHostname.asStateFlow()

    private val _hostnameList = MutableStateFlow(emptySet<String>())
    val hostnameList: StateFlow<Set<String>> = _hostnameList.asStateFlow()

    private val _showAddHostnameDialog = MutableStateFlow(false)
    val showAddHostnameDialog: StateFlow<Boolean> = _showAddHostnameDialog.asStateFlow()

    private val _newHostnameInput = MutableStateFlow("")
    val newHostnameInput: StateFlow<String> = _newHostnameInput.asStateFlow()

    // Shown every time the user flips the toggle ON — forces an explicit host pick,
    // it is NEVER auto-populated from a previous session's applied host.
    private val _showEnableHostnameDialog = MutableStateFlow(false)
    val showEnableHostnameDialog: StateFlow<Boolean> = _showEnableHostnameDialog.asStateFlow()

    // Applying state — lets the UI show a spinner while the blocking DoT check runs
    private val _isApplyingPrivateDns = MutableStateFlow(false)
    val isApplyingPrivateDns: StateFlow<Boolean> = _isApplyingPrivateDns.asStateFlow()

    // ── Private DNS "lock settings" state ─────────────────────────────────────
    private val _isPrivateDnsLocked = MutableStateFlow(false)
    val isPrivateDnsLocked: StateFlow<Boolean> = _isPrivateDnsLocked.asStateFlow()

    // ── Banking Mode States ──────────────────────────────────────────────────
    private val _isBankingBypassEnabled = MutableStateFlow(false)
    val isBankingBypassEnabled: StateFlow<Boolean> = _isBankingBypassEnabled.asStateFlow()

    private val _bankingBypassPackage = MutableStateFlow<String?>(null)
    val bankingBypassPackage: StateFlow<String?> = _bankingBypassPackage.asStateFlow()

    private val _showBankingAppPicker = MutableStateFlow(false)
    val showBankingAppPicker: StateFlow<Boolean> = _showBankingAppPicker.asStateFlow()

    private val _bankingApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val bankingApps: StateFlow<List<AppInfo>> = _bankingApps.asStateFlow()

    private val _isCheckingBypassTimeout = MutableStateFlow(false)
    val isCheckingBypassTimeout: StateFlow<Boolean> = _isCheckingBypassTimeout.asStateFlow()

    // Surfaces failures instead of swallowing them silently
    private val _privateDnsError = MutableStateFlow<String?>(null)
    val privateDnsError: StateFlow<String?> = _privateDnsError.asStateFlow()

    private val _deviceOwnerError = MutableStateFlow<String?>(null)
    val deviceOwnerError: StateFlow<String?> = _deviceOwnerError.asStateFlow()

    data class AppInfo(
        val packageName: String,
        val name: String,
        val icon: Drawable?,
        val isUninstallBlocked: Boolean,
        val isForceStopBlocked: Boolean
    )

    data class AppConfirmData(
        val packageName: String,
        val type: AppProtectionType,
        val appName: String
    )
    enum class AppProtectionType { UNINSTALL, FORCE_STOP }

    private val _showAppConfirmDialog = MutableStateFlow<AppConfirmData?>(null)
    val showAppConfirmDialog: StateFlow<AppConfirmData?> = _showAppConfirmDialog.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _showAppListDialog = MutableStateFlow(false)
    val showAppListDialog: StateFlow<Boolean> = _showAppListDialog.asStateFlow()

    private val _showDeviceOwnerRequiredDialog = MutableStateFlow(false)
    val showDeviceOwnerRequiredDialog: StateFlow<Boolean> = _showDeviceOwnerRequiredDialog.asStateFlow()

    private val _showAntiUninstallPermissionDialog = MutableStateFlow(false)
    val showAntiUninstallPermissionDialog: StateFlow<Boolean> = _showAntiUninstallPermissionDialog.asStateFlow()

    // ── Shizuku State Flows ──────────────────────────────────────────────────
    private val _isShizukuInstalled = MutableStateFlow(ShizukuManager.isShizukuInstalled(context))
    val isShizukuInstalled: StateFlow<Boolean> = _isShizukuInstalled.asStateFlow()

    private val _isShizukuAvailable = MutableStateFlow(ShizukuManager.isShizukuAvailable())
    val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable.asStateFlow()

    private val _hasShizukuPermission = MutableStateFlow(ShizukuManager.hasShizukuPermission())
    val hasShizukuPermission: StateFlow<Boolean> = _hasShizukuPermission.asStateFlow()

    private val _isSecureSettingsGranted = MutableStateFlow(
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    )
    val isSecureSettingsGranted: StateFlow<Boolean> = _isSecureSettingsGranted.asStateFlow()

    private val _showShizukuGuideDialog = MutableStateFlow(false)
    val showShizukuGuideDialog: StateFlow<Boolean> = _showShizukuGuideDialog.asStateFlow()

    private val _showDeviceOwnerConfirmDialog = MutableStateFlow(false)
    val showDeviceOwnerConfirmDialog: StateFlow<Boolean> = _showDeviceOwnerConfirmDialog.asStateFlow()

    private val _isAutoHealEnabled = MutableStateFlow(true)
    val isAutoHealEnabled: StateFlow<Boolean> = _isAutoHealEnabled.asStateFlow()

    init {
        ShizukuManager.initListeners {
            refreshShizukuState()
        }
        refreshShizukuState()
        refreshPrivateDnsState()

        viewModelScope.launch {
            dataStoreManager.settings.collect {
                _settings.value = it
                _isPermissionBlockEnabled.value = it.isPermissionBlockEnabled
                _isPrivateDnsEnabled.value = it.isPrivateDnsEnabled
                _selectedHostname.value = it.selectedPrivateDnsHostname
                _hostnameList.value = it.customPrivateDnsHostnames
                _isBankingBypassEnabled.value = it.isBankingBypassEnabled
                _bankingBypassPackage.value = it.bankingBypassPackage
                _isPrivateDnsLocked.value = it.isPrivateDnsLocked
                _isAutoHealEnabled.value = it.isAutoHealEnabled
            }
        }
    }

    fun startFocusModeSession(
        context: Context,
        durationSeconds: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val active = FocusSessionManager.getActiveSession(context)
            if (active != null) {
                val remainingSec = FocusSessionManager.calculateRemainingSeconds(context, active)
                if (remainingSec > 0) {
                    withContext(Dispatchers.Main) {
                        onError("A Focus session is already active with ${remainingSec}s remaining.")
                    }
                    return@launch
                }
            }

            val newSession = FocusSessionManager.startNewSession(context, durationSeconds)
            if (newSession != null) {
                withContext(Dispatchers.Main) {
                    FocusModeOverlayService.startFocusMode(context, durationSeconds)
                    onSuccess()
                }
            } else {
                withContext(Dispatchers.Main) {
                    onError("Failed to start Focus session.")
                }
            }
        }
    }

    fun refreshShizukuState() {
        _isShizukuInstalled.value = ShizukuManager.isShizukuInstalled(context)
        _isShizukuAvailable.value = ShizukuManager.isShizukuAvailable()
        _hasShizukuPermission.value = ShizukuManager.hasShizukuPermission()
        _isSecureSettingsGranted.value = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    fun requestShizukuPermission() {
        ShizukuManager.requestPermission()
        refreshShizukuState()
    }

    fun grantSecureSettings() {
        ShizukuManager.grantSecureSettings(context) { success ->
            refreshShizukuState()
        }
    }

    fun promoteDeviceOwner() {
        _deviceOwnerError.value = null
        ShizukuManager.setDeviceOwner(context) { success, msg ->
            refreshShizukuState()
            if (!success) {
                _deviceOwnerError.value = msg
            }
        }
    }

    fun dismissDeviceOwnerError() {
        _deviceOwnerError.value = null
    }

    fun reinforceBackgroundExecution() {
        ShizukuManager.reinforceBackgroundExecution(context)
    }

    fun onToggleAutoHeal(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setAutoHealEnabled(enabled)
            _isAutoHealEnabled.value = enabled
        }
    }

    fun openShizukuGuide() { _showShizukuGuideDialog.value = true }
    fun dismissShizukuGuide() { _showShizukuGuideDialog.value = false }

    fun openDeviceOwnerConfirm() { _showDeviceOwnerConfirmDialog.value = true }
    fun dismissDeviceOwnerConfirm() { _showDeviceOwnerConfirmDialog.value = false }

    val isAccessibilityGranted: Boolean
        get() = PermissionHelper.isAccessibilityEnabled(context)

    val isDeviceAdminGranted: Boolean
        get() = PermissionHelper.isDeviceAdminActive(context)

    fun showDeviceOwnerRequiredDialog() {
        _showDeviceOwnerRequiredDialog.value = true
    }

    fun dismissDeviceOwnerRequiredDialog() {
        _showDeviceOwnerRequiredDialog.value = false
    }

    fun dismissAntiUninstallPermissionDialog() {
        _showAntiUninstallPermissionDialog.value = false
    }

    fun onAntiUninstallToggleRequested(requested: Boolean, onEnabled: () -> Unit) {
        if (!requested) return

        if (isAccessibilityGranted && isDeviceAdminGranted) {
            viewModelScope.launch {
                dataStoreManager.updateSettings { it.copy(isAntiUninstallEnabled = true) }
            }
            onEnabled()
        } else {
            _showAntiUninstallPermissionDialog.value = true
        }
    }

    // ── App Protection Confirmation ──────────────────────────────────────────
    fun clearPrivateDnsError() {
        _privateDnsError.value = null
    }
    /**
     * Called when the user flips the Private DNS switch.
     * - Turning ON never auto-applies a remembered host: it opens the picker dialog
     *   and only enables once the user explicitly confirms a host.
     * - Turning OFF disables immediately, no prompt required.
     */
    fun onPrivateDnsToggleRequested(enabledRequested: Boolean) {
        if (!canControlPrivateDns) {
            _showDeviceOwnerRequiredDialog.value = true
            return
        }
        if (enabledRequested) {
            _showEnableHostnameDialog.value = true
        } else {
            disablePrivateDns()
        }
    }

    /** Immediately switches Private DNS to opportunistic (i.e. off / system default). */
    private fun disablePrivateDns() {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreManager.setPrivateDnsEnabled(false)
            _isPrivateDnsEnabled.value = false
            _privateDnsError.value = null

            _isApplyingPrivateDns.value = true
            DevicePolicyHelper.applyPrivateDns(context, false, "")
            _isApplyingPrivateDns.value = false
        }
    }

    /** User picked a host in the enable dialog and confirmed it. */
    fun confirmEnablePrivateDns(hostname: String) {
        _showEnableHostnameDialog.value = false
        _privateDnsError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            _isApplyingPrivateDns.value = true
            val success = DevicePolicyHelper.applyPrivateDns(context, true, hostname)
            _isApplyingPrivateDns.value = false

            if (success) {
                dataStoreManager.updateSettings { 
                    it.copy(isPrivateDnsEnabled = true, selectedPrivateDnsHostname = hostname)
                }
                _isPrivateDnsEnabled.value = true
                _selectedHostname.value = hostname
                WatchdogService.start(context)
            } else {
                _privateDnsError.value =
                    "Could not enable Private DNS using \"$hostname\". " +
                            "This host doesn't support DNS-over-TLS, or the connection check failed. " +
                            "Pick a different host and try again."
            }
        }
    }

    fun deleteHostname(hostname: String) {
        viewModelScope.launch {
            dataStoreManager.updateSettings { current ->
                val updated = current.customPrivateDnsHostnames.toMutableSet()
                updated.remove(hostname)
                current.copy(customPrivateDnsHostnames = updated)
            }
        }
    }

    fun isDefaultHostname(hostname: String): Boolean =
        _settings.value.customPrivateDnsHostnames.contains(hostname) // Heuristic

    /** User dismissed the enable dialog without picking a host — toggle stays off. */
    fun dismissEnableHostnameDialog() {
        _showEnableHostnameDialog.value = false
    }

    fun onAddHostnameClicked() {
        _showAddHostnameDialog.value = true
    }

    fun dismissAddHostnameDialog() {
        _showAddHostnameDialog.value = false
        _newHostnameInput.value = ""
    }

    fun updateNewHostnameInput(input: String) {
        _newHostnameInput.value = input
    }

    fun saveNewHostname() {
        val input = _newHostnameInput.value.trim()
        if (input.isNotEmpty()) {
            viewModelScope.launch {
                dataStoreManager.updateSettings { current ->
                    val currentList = current.customPrivateDnsHostnames.toMutableSet()
                    currentList.add(input)
                    current.copy(customPrivateDnsHostnames = currentList)
                }
            }
        }
        dismissAddHostnameDialog()
    }
    // ── Device Owner & Control Checks ─────────────────────────────────────────
    val isDeviceOwner: Boolean
        get() {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                dpm?.isDeviceOwnerApp(context.packageName) == true
            } catch (t: Throwable) {
                false
            }
        }

    val canControlPrivateDns: Boolean
        get() = isDeviceOwner || isSecureSettingsGranted.value

    // ── Methods for Permission Blocking ───────────────────────────────────────
    fun onToggleClicked(isChecked: Boolean) {
        pendingToggleState = isChecked
        _showConfirmDialog.value = true
    }

    fun confirmToggle() {
        viewModelScope.launch {
            dataStoreManager.setPermissionBlockEnabled(pendingToggleState)
            _isPermissionBlockEnabled.value = pendingToggleState
            _showConfirmDialog.value = false
        }
    }

    fun dismissDialog() {
        _showConfirmDialog.value = false
    }

    /**
     * Re-syncs Private DNS UI state from the actual system settings rather than
     * trusting the cached preference. Called every time this ViewModel is
     * created (i.e. every time the Security screen is opened) so the toggle
     * never shows a stale state.
     */
    fun refreshPrivateDnsState() {
        if (!canControlPrivateDns) return
        viewModelScope.launch(Dispatchers.IO) {
            // Re-apply Private DNS policy first if Settings Shield is enabled and DNS was tampered with
            DevicePolicyHelper.reapplyPolicyIfMismatched(context)

            val (systemEnabled, systemHost) = DevicePolicyHelper.getCurrentPrivateDnsState(context)
            val lockActive = if (isDeviceOwner) DevicePolicyHelper.isPrivateDnsSettingsLocked(context) else _isPrivateDnsLocked.value

            _isPrivateDnsEnabled.value = systemEnabled
            dataStoreManager.setPrivateDnsEnabled(systemEnabled)

            if (systemEnabled && systemHost.isNotBlank()) {
                _selectedHostname.value = systemHost
                dataStoreManager.setSelectedPrivateDnsHostname(systemHost)
            }

            _isPrivateDnsLocked.value = lockActive
            dataStoreManager.setPrivateDnsLocked(lockActive)
        }
    }

    fun onPrivateDnsLockToggleRequested(lockRequested: Boolean) {
        if (!canControlPrivateDns) {
            _showDeviceOwnerRequiredDialog.value = true
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (isDeviceOwner) {
                DevicePolicyHelper.setPrivateDnsUserRestriction(context, lockRequested)
            }
            _isPrivateDnsLocked.value = lockRequested
            dataStoreManager.setPrivateDnsLocked(lockRequested)
            if (lockRequested) {
                DevicePolicyHelper.reapplyPolicyIfMismatched(context)
                WatchdogService.start(context)
            }
        }
    }






    fun onOpenAppListRequested() {
        if (!isDeviceOwner) {
            _showDeviceOwnerRequiredDialog.value = true
            return
        }
        _showAppListDialog.value = true
        fetchInstalledApps()
    }

    fun dismissAppListDialog() {
        _showAppListDialog.value = false
    }

    private fun fetchInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            val pm = context.packageManager

            // Get currently protected packages from the system
            val protectedFromStop = DevicePolicyHelper.getControlDisabledPackages(context)

            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
                .map { app ->
                    AppInfo(
                        packageName = app.packageName,
                        name = app.loadLabel(pm).toString(),
                        icon = app.loadIcon(pm),
                        isUninstallBlocked = DevicePolicyHelper.isUninstallBlocked(context, app.packageName),
                        isForceStopBlocked = protectedFromStop.contains(app.packageName)
                    )
                }
                .sortedBy { it.name }
            _installedApps.value = apps
            _isLoadingApps.value = false
        }
    }

    fun toggleUninstallProtection(packageName: String, blocked: Boolean) {
        if (!isDeviceOwner) {
            _showDeviceOwnerRequiredDialog.value = true
            return
        }

        // 1. Check if settings are locked when trying to turn OFF
        if (!blocked && settings.value.isSettingsLocked) {
            Toast.makeText(context, "Cannot disable protection while settings are locked.", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. If turning ON, show confirmation dialog
        if (blocked) {
            val app = _installedApps.value.find { it.packageName == packageName }
            _showAppConfirmDialog.value = AppConfirmData(packageName, AppProtectionType.UNINSTALL, app?.name ?: packageName)
            return
        }

        // 3. Otherwise (turning OFF and not locked), proceed
        executeToggleUninstall(packageName, false)
    }

    fun toggleForceStopProtection(packageName: String, blocked: Boolean) {
        if (!isDeviceOwner) {
            _showDeviceOwnerRequiredDialog.value = true
            return
        }

        if (!blocked && settings.value.isSettingsLocked) {
            Toast.makeText(context, "Cannot disable protection while settings are locked.", Toast.LENGTH_SHORT).show()
            return
        }

        if (blocked) {
            val app = _installedApps.value.find { it.packageName == packageName }
            _showAppConfirmDialog.value = AppConfirmData(packageName, AppProtectionType.FORCE_STOP, app?.name ?: packageName)
            return
        }

        executeToggleForceStop(packageName, false)
    }

    // --- Confirmation & Execution Helpers ---

    fun confirmAppToggle() {
        val data = _showAppConfirmDialog.value ?: return
        _showAppConfirmDialog.value = null
        when (data.type) {
            AppProtectionType.UNINSTALL -> executeToggleUninstall(data.packageName, true)
            AppProtectionType.FORCE_STOP -> executeToggleForceStop(data.packageName, true)
        }
    }

    fun dismissAppConfirmDialog() {
        _showAppConfirmDialog.value = null
    }

    private fun executeToggleUninstall(packageName: String, blocked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DevicePolicyHelper.setUninstallBlocked(context, packageName, blocked)
            withContext(Dispatchers.Main) {
                if (success) {
                    _installedApps.value = _installedApps.value.map {
                        if (it.packageName == packageName) it.copy(isUninstallBlocked = blocked) else it
                    }
                    Toast.makeText(context, "Protection ${if (blocked) "Enabled" else "Disabled"} for $packageName", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun executeToggleForceStop(packageName: String, blocked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = DevicePolicyHelper.getControlDisabledPackages(context).toMutableList()
            if (blocked) {
                if (!currentList.contains(packageName)) currentList.add(packageName)
            } else {
                currentList.remove(packageName)
            }

            val success = DevicePolicyHelper.setControlDisabledPackages(context, currentList)
            if (success) {
                _installedApps.value = _installedApps.value.map {
                    if (it.packageName == packageName) it.copy(isForceStopBlocked = blocked) else it
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Force Stop ${if (blocked) "Disabled" else "Enabled"} for $packageName", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Banking Mode Logic ────────────────────────────────────────────────────

    fun onBankingBypassToggleRequested(enabled: Boolean) {
        if (enabled) {
            _showBankingAppPicker.value = true
            fetchAppsForBankingMode()
        } else {
            // "Finish" Banking Mode
            disableBankingBypass()
            // Redirect to accessibility settings to turn it back ON
            openAccessibilitySettings()
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun disableBankingBypass() {
        viewModelScope.launch {
            dataStoreManager.setBankingBypassEnabled(false)
            _isBankingBypassEnabled.value = false
            _bankingBypassPackage.value = null
        }
    }

    fun confirmBankingBypass(packageName: String) {
        viewModelScope.launch {
            dataStoreManager.setBankingBypassEnabled(true, packageName)
            _isBankingBypassEnabled.value = true
            _bankingBypassPackage.value = packageName
            _showBankingAppPicker.value = false

            // ONE-TAP TURN OFF: Disable accessibility service programmatically
            GuardianAccessibilityService.disableService(context)
            
            Toast.makeText(context, "Banking Mode Active. Accessibility turned OFF for you.", Toast.LENGTH_LONG).show()
        }
    }

    fun dismissBankingAppPicker() {
        _showBankingAppPicker.value = false
    }

    private fun fetchAppsForBankingMode() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            val pm = context.packageManager
            val blacklistKeywords = listOf("facebook", "instagram", "tiktok", "youtube", "snapchat", "twitter", "x.android", "netflix", "primevideo", "disney", "hotstar")
            val bankingKeywords = listOf("bank", "pay", "wallet", "finance", "invest", "crypto", "trading", "cash", "upi", "stock")

            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { app ->
                    // Exclude system apps that aren't updated
                    ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) &&
                    app.packageName != context.packageName
                }
                .filter { app ->
                    // Layer 1: Metadata Check
                    val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.category else -1
                    category != ApplicationInfo.CATEGORY_SOCIAL && category != ApplicationInfo.CATEGORY_VIDEO && category != ApplicationInfo.CATEGORY_GAME
                }
                .filter { app ->
                    // Layer 4: Hardcoded Safety List
                    !blacklistKeywords.any { app.packageName.lowercase().contains(it) }
                }
                .map { app ->
                    val name = app.loadLabel(pm).toString()
                    val hasSmsPerm = try {
                        val pkgInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                        pkgInfo.requestedPermissions?.any { it.contains("SMS") } == true
                    } catch (e: Exception) { false }

                    val isSuggested = hasSmsPerm || bankingKeywords.any { name.lowercase().contains(it) || app.packageName.lowercase().contains(it) }

                    Triple(app, name, isSuggested)
                }
                .sortedWith(compareByDescending<Triple<ApplicationInfo, String, Boolean>> { it.third }.thenBy { it.second })
                .map { (app, name, _) ->
                    AppInfo(
                        packageName = app.packageName,
                        name = name,
                        icon = app.loadIcon(pm),
                        isUninstallBlocked = false,
                        isForceStopBlocked = false
                    )
                }

            _bankingApps.value = apps
            _isLoadingApps.value = false
        }
    }


}
