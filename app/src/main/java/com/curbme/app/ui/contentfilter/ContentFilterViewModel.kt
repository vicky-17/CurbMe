package com.curbme.app.ui.contentfilter

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.curbme.app.core.base.BaseViewModel
import com.curbme.app.core.utils.Constants.BrowserConstants
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.data.local.prefs.PrefsManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ContentFilterViewModel manages state for content filtering and browser security settings.
 */
class ContentFilterViewModel(
    private val prefs: PrefsManager,
    private val dataStoreManager: DataStoreManager? = null,
    private val context: Context? = null
) : BaseViewModel() {

    // ── UI State ──
    private val _isSafeSearchEnabled = MutableStateFlow(prefs.isSafeSearchEnabled)
    val isSafeSearchEnabled: StateFlow<Boolean> = _isSafeSearchEnabled.asStateFlow()

    private val _isYoutubeFilterEnabled = MutableStateFlow(prefs.isYoutubeFilterEnabled)
    val isYoutubeFilterEnabled: StateFlow<Boolean> = _isYoutubeFilterEnabled.asStateFlow()

    private val _isUnsupportedBrowserFallbackEnabled = MutableStateFlow(prefs.isBlockUnsupportedBrowsers)
    val isUnsupportedBrowserFallbackEnabled: StateFlow<Boolean> = _isUnsupportedBrowserFallbackEnabled.asStateFlow()

    val isTorBrowserInstalled: Flow<Boolean> = flow {
        val isInstalled = if (context != null) {
            val pm = context.packageManager
            try {
                pm.getPackageInfo(BrowserConstants.TOR, 0)
                true
            } catch (_: Exception) {
                try {
                    pm.getPackageInfo(BrowserConstants.TOR_ALPHA, 0)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        } else false
        emit(isInstalled)
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun setSafeSearchEnabled(enabled: Boolean) {
        prefs.isSafeSearchEnabled = enabled
        _isSafeSearchEnabled.value = enabled
        if (dataStoreManager != null) {
            viewModelScope.launch { dataStoreManager.setSafeSearchEnabled(enabled) }
        }
    }

    fun setYoutubeFilterEnabled(enabled: Boolean) {
        prefs.isYoutubeFilterEnabled = enabled
        _isYoutubeFilterEnabled.value = enabled
    }

    fun setUnsupportedBrowserFallback(enabled: Boolean) {
        prefs.isBlockUnsupportedBrowsers = enabled
        _isUnsupportedBrowserFallbackEnabled.value = enabled
        if (dataStoreManager != null) {
            viewModelScope.launch { dataStoreManager.setBlockUnsupportedBrowsers(enabled) }
        }
    }

    fun refreshSettings() {
        _isSafeSearchEnabled.value = prefs.isSafeSearchEnabled
        _isYoutubeFilterEnabled.value = prefs.isYoutubeFilterEnabled
        _isUnsupportedBrowserFallbackEnabled.value = prefs.isBlockUnsupportedBrowsers
    }
}
