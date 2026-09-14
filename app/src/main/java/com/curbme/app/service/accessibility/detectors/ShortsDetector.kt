package com.curbme.app.service.accessibility.detectors

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object ShortsDetector {
    private const val TAG = "ShortsDetector"

    // Packages where the entire app is short-form video (e.g., TikTok, Moj, Josh)
    private val FULL_APP_BLOCKED_PACKAGES: Set<String> = setOf(
        "com.ss.android.ugc.trill",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.aweme",
        "com.zhiliaoapp.musically.go",
        "in.mohabat.app",
        "com.eterno.shortvideos",
        "com.sharechat.mza"
    )

    // Known target packages where Shorts/Reels/Spotlight detection is performed
    val TARGET_SHORT_VIDEO_PACKAGES: Set<String> = setOf(
        "com.google.android.youtube",
        "app.revanced.android.youtube",
        "app.morphe.android.youtube",
        "com.vanced.android.youtube",
        "com.instagram.android",
        "com.myinsta.android",
        "com.instagram.lite",
        "com.snapchat.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.reddit.frontpage",
        "com.twitter.android",
        "com.pinterest",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill"
    )

    // Exact view IDs for YouTube / ReVanced Shorts
    private val YOUTUBE_SHORTS_VIEW_IDS = setOf(
        "reel_recycler",
        "reel_recycler_view",
        "shorts_player_container",
        "shorts_container",
        "reel_watch_fragment",
        "reel_player_page_container",
        "reel_viewer",
        "shorts_player",
        "reel_content",
        "shorts_video_player",
        "reel_control_bar",
        "reel_interaction_layer",
        "shorts_pivot_button",
        "shorts_camera_button",
        "player_overlay",
        "reel_player_overlay_container",
        "reel_player_overlay_root",
        "reel_byline_text_offline",
        "reel_main_title_offline",
        "reel_byline_text",
        "reel_main_title",
        "reel_channel_name_text_view",
        "reel_channel_name",
        "channel_name",
        "reel_title",
        "reel_watch_fragment_root"
    )

    // Exact view IDs for Instagram Reels
    private val INSTAGRAM_REELS_VIEW_IDS = setOf(
        "root_clips_layout",
        "clips_video_container",
        "clips_swipe_refresh_layout",
        "clips_view_pager",
        "clips_viewer_container",
        "reels_video_container",
        "reply_bar_container",
        "reel_viewer_image_view",
        "clips_viewer_fragment",
        "clips_item_layout",
        "reel_viewer_title",
        "clips_action_bar",
        "clips_viewer_view_pager",
        "clips_author_username",
        "clips_captions_component"
    )

    // Exact view IDs for Snapchat Spotlight
    private val SNAPCHAT_SPOTLIGHT_VIEW_IDS = setOf(
        "spotlight_tab",
        "spotlight_page",
        "spotlight_video_container",
        "spotlight_view_pager"
    )

    // Exact view IDs for Facebook Reels
    private val FACEBOOK_REELS_VIEW_IDS = setOf(
        "reels_viewer_fragment",
        "reels_tab",
        "facebook_reels_container"
    )

    // View IDs for Reddit & Twitter Video Reels
    private val OTHER_REELS_VIEW_IDS = setOf(
        "video_player_container",
        "reels_feed_container",
        "short_video_view_pager",
        "watch_feed_container"
    )

    /**
     * Determines if the current screen contains short-form video content.
     */
    @JvmStatic
    fun shouldBlock(rootNode: AccessibilityNodeInfo?, packageName: String?): Boolean {
        if (packageName == null) return false

        // 1. Check if the entire app is blocked (e.g., TikTok, Moj, Josh)
        if (FULL_APP_BLOCKED_PACKAGES.contains(packageName)) {
            Log.d(TAG, "Full app blocked package match: $packageName")
            return true
        }

        if (rootNode == null) return false

        // 2. Fast check known view IDs using findAccessibilityNodeInfosByViewId
        if (checkKnownViewIds(rootNode, packageName)) {
            return true
        }

        // 3. Recursive inspection for view ID patterns or selected navigation tabs
        if (checkNodeTreePatterns(rootNode, packageName)) {
            return true
        }

        return false
    }

    private fun checkKnownViewIds(root: AccessibilityNodeInfo, packageName: String): Boolean {
        val idsToCheck = when {
            packageName.contains("youtube") -> YOUTUBE_SHORTS_VIEW_IDS
            packageName.contains("instagram") -> INSTAGRAM_REELS_VIEW_IDS
            packageName.contains("snapchat") -> SNAPCHAT_SPOTLIGHT_VIEW_IDS
            packageName.contains("facebook") -> FACEBOOK_REELS_VIEW_IDS
            else -> OTHER_REELS_VIEW_IDS
        }

        for (idName in idsToCheck) {
            val fullViewId = "$packageName:id/$idName"
            if (hasVisibleViewId(root, fullViewId)) {
                return true
            }
        }
        return false
    }

    private fun hasVisibleViewId(root: AccessibilityNodeInfo, viewId: String): Boolean {
        try {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                var foundVisible = false
                val screenBounds = Rect()
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        node.getBoundsInScreen(screenBounds)
                        if (screenBounds.width() > 0 && screenBounds.height() > 0) {
                            foundVisible = true
                        }
                    }
                    @Suppress("DEPRECATION")
                    node.recycle()
                }
                return foundVisible
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding view ID: $viewId", e)
        }
        return false
    }

    private fun checkNodeTreePatterns(root: AccessibilityNodeInfo, packageName: String): Boolean {
        val visitedCount = intArrayOf(0)
        return scanNode(root, depth = 0, visitedCount = visitedCount, packageName = packageName)
    }

    private fun scanNode(
        node: AccessibilityNodeInfo?,
        depth: Int,
        visitedCount: IntArray,
        packageName: String
    ): Boolean {
        if (node == null || depth > 20 || visitedCount[0] > 120) return false
        visitedCount[0]++

        val viewId = node.viewIdResourceName
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val isSelected = node.isSelected || node.isFocused

        if (viewId != null) {
            val lowerId = viewId.lowercase()
            when {
                packageName.contains("youtube") -> {
                    // Exclude shorts_shelf as it's just the home feed carousel
                    if (!lowerId.contains("shorts_shelf") &&
                        (lowerId.contains(":id/reel_") || lowerId.contains(":id/shorts_"))
                    ) {
                        if (node.isVisibleToUser) return true
                    }
                }
                packageName.contains("instagram") -> {
                    if (lowerId.contains(":id/clips_") || lowerId.contains(":id/reel_") || lowerId.contains(":id/reels_")) {
                        if (node.isVisibleToUser) return true
                    }
                }
                packageName.contains("snapchat") -> {
                    if (lowerId.contains(":id/spotlight_")) {
                        if (node.isVisibleToUser) return true
                    }
                }
                packageName.contains("facebook") -> {
                    if (lowerId.contains(":id/reels_") || lowerId.contains(":id/reel_")) {
                        if (node.isVisibleToUser) return true
                    }
                }
                packageName.contains("reddit") || packageName.contains("twitter") || packageName.contains("pinterest") -> {
                    if (lowerId.contains(":id/reel") || lowerId.contains(":id/short") || lowerId.contains(":id/video_player")) {
                        if (node.isVisibleToUser) return true
                    }
                }
            }
        }

        // Check selected navigation tabs or bottom bar items (e.g., Shorts or Reels tab selected)
        if (isSelected && node.isVisibleToUser) {
            val label = (text ?: desc ?: "").trim()
            if (label.equals("Shorts", ignoreCase = true) && packageName.contains("youtube")) {
                return true
            }
            if (label.equals("Reels", ignoreCase = true) && packageName.contains("instagram")) {
                return true
            }
            if (label.equals("Spotlight", ignoreCase = true) && packageName.contains("snapchat")) {
                return true
            }
            if ((label.equals("Watch", ignoreCase = true) || label.equals("Reels", ignoreCase = true)) &&
                (packageName.contains("facebook") || packageName.contains("reddit"))
            ) {
                return true
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val result = scanNode(child, depth + 1, visitedCount, packageName)
            @Suppress("DEPRECATION")
            child.recycle()
            if (result) return true
        }

        return false
    }

    /**
     * Checks if the screen explicitly displays a non-Shorts section (e.g. Home feed, Search, Subscriptions, Profile).
     */
    @JvmStatic
    fun isExplicitNonShortsPage(rootNode: AccessibilityNodeInfo?, packageName: String?): Boolean {
        if (rootNode == null || packageName == null) return false

        // If Shorts UI elements are present on screen, it CANNOT be a non-shorts page!
        if (shouldBlock(rootNode, packageName)) {
            return false
        }

        val visitedCount = intArrayOf(0)
        return scanForNonShorts(rootNode, depth = 0, visitedCount = visitedCount, packageName = packageName)
    }

    private fun scanForNonShorts(
        node: AccessibilityNodeInfo?,
        depth: Int,
        visitedCount: IntArray,
        packageName: String
    ): Boolean {
        if (node == null || depth > 20 || visitedCount[0] > 120) return false
        visitedCount[0]++

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val isSelected = node.isSelected

        when {
            packageName.contains("youtube") -> {
                // Selected bottom navigation tabs for non-Shorts sections
                if (isSelected && node.isVisibleToUser) {
                    val label = (if (text.isNotBlank()) text else desc).trim()
                    if (label.equals("Home", ignoreCase = true) ||
                        label.equals("Subscriptions", ignoreCase = true) ||
                        label.equals("You", ignoreCase = true) ||
                        label.equals("Library", ignoreCase = true)
                    ) {
                        return true
                    }
                }
                // Explicit view IDs for YouTube Search
                if (viewId.contains(":id/search_edit_text") ||
                    viewId.contains(":id/search_input")
                ) {
                    if (node.isVisibleToUser) return true
                }
            }
            packageName.contains("instagram") -> {
                if (isSelected && node.isVisibleToUser) {
                    val label = (if (text.isNotBlank()) text else desc).trim()
                    if (label.equals("Home", ignoreCase = true) ||
                        label.equals("Search", ignoreCase = true) ||
                        label.equals("Profile", ignoreCase = true)
                    ) {
                        return true
                    }
                }
                if (viewId.contains(":id/direct_inbox") || viewId.contains(":id/direct_thread")) {
                    if (node.isVisibleToUser) return true
                }
            }
            packageName.contains("snapchat") -> {
                if (isSelected && node.isVisibleToUser) {
                    val label = (if (text.isNotBlank()) text else desc).trim()
                    if (label.equals("Chat", ignoreCase = true) ||
                        label.equals("Camera", ignoreCase = true) ||
                        label.equals("Map", ignoreCase = true) ||
                        label.equals("Stories", ignoreCase = true)
                    ) {
                        return true
                    }
                }
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val result = scanForNonShorts(child, depth + 1, visitedCount, packageName)
            @Suppress("DEPRECATION")
            child.recycle()
            if (result) return true
        }

        return false
    }
}
