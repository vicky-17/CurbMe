package com.curbme.app.service.accessibility.handlers

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.curbme.app.service.accessibility.detectors.BrowserUrlReader

/**
 * Detects and blocks unsupported browsers that do not expose standard URL bars
 * when unsupported browser protection is enabled in settings.
 */
class BrowserBlocker(private val service: AccessibilityService) {

    private val cacheBrowserApps = HashSet<String>()
    private val cacheNonBrowserApps = HashSet<String>()

    fun handleEvent(event: AccessibilityEvent?, isBlockUnsupportedEnabled: Boolean, performAction: (Int) -> Boolean) {
        if (!isBlockUnsupportedEnabled || event == null) return
        val packageName = event.packageName?.toString() ?: return

        if (isUnsupportedBrowser(service, packageName)) {
            Log.w("BrowserBlocker", "🚫 Unsupported browser detected: $packageName -> Redirecting to HOME")
            Toast.makeText(service, "Unsupported browser blocked to prevent bypass.", Toast.LENGTH_SHORT).show()
            performAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }

    private fun isUnsupportedBrowser(context: Context, packageName: String): Boolean {
        if (BrowserUrlReader.isSupportedBrowser(packageName)) return false
        if (cacheBrowserApps.contains(packageName)) return true
        if (cacheNonBrowserApps.contains(packageName)) return false

        val isBrowser = resolveIsBrowser(context, packageName)
        if (isBrowser) {
            cacheBrowserApps.add(packageName)
        } else {
            cacheNonBrowserApps.add(packageName)
        }

        return isBrowser
    }

    private fun resolveIsBrowser(context: Context, packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
            intent.setPackage(packageName)
            val pm = context.packageManager
            val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            activities.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}
