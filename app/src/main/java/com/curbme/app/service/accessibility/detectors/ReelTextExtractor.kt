package com.curbme.app.service.accessibility.detectors

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Fast, direct View ID extractor for short-video / reel apps.
 * Extracts dynamic comparator text strings (e.g. author name, title, caption)
 * directly by View ID to distinguish consecutive reels/shorts cleanly.
 */
object ReelTextExtractor {

    /**
     * Extracts dynamic comparator text from rootNode for the given package name, or returns null if not a reel view.
     */
    fun extractComparator(rootNode: AccessibilityNodeInfo?, packageName: String): String? {
        if (rootNode == null) return null

        return when {
            packageName.contains("youtube") -> extractYouTubeReelText(rootNode, packageName)
            packageName.contains("instagram") -> extractInstagramReelText(rootNode, packageName)
            packageName.contains("snapchat") -> extractSnapchatSpotlightText(rootNode, packageName)
            packageName.contains("facebook") -> extractFacebookReelText(rootNode, packageName)
            packageName.contains("musically") || packageName.contains("tiktok") -> extractTikTokReelText(rootNode, packageName)
            else -> null
        }
    }

    private fun extractYouTubeReelText(root: AccessibilityNodeInfo, pkg: String): String? {
        val container = root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_recycler")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_player_page_container") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_watch_fragment_root") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/shorts_player_container") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/shorts_container") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_watch_fragment") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_viewer") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/shorts_player") }

        val isShortsTabActive = ShortsDetector.shouldBlock(root, pkg)
        if (container.isNullOrEmpty() && !isShortsTabActive) {
            return null
        }

        var textBuilder = ""

        // 1. Channel Name / Byline
        val channelNodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_byline_text_offline")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_byline_text") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_channel_name_text_view") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_channel_name") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/channel_name") }
        for (node in channelNodes) {
            val text = collectNodeText(node, maxDepth = 2)
            if (text.isNotBlank()) {
                textBuilder += "$text "
                break
            }
        }

        // 2. Main Title / Caption
        val titleNodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_main_title_offline")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_main_title") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_title_text_view") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_title") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/shorts_title") }
        for (node in titleNodes) {
            val text = collectNodeText(node, maxDepth = 4)
            if (text.isNotBlank()) {
                textBuilder += "$text "
                break
            }
        }

        // 3. Scan player_overlay container for Title, Like Button, Comment Button descriptions (works for Subscribed channels)
        val overlayNodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/player_overlay")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_player_overlay_container") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_player_overlay_root") }
        for (overlay in overlayNodes) {
            val extractedOverlayText = extractOverlaySubtreeInfo(overlay)
            if (extractedOverlayText.isNotBlank()) {
                textBuilder += "$extractedOverlayText "
                break
            }
        }

        // 4. Fallback search for "Subscribe to @" anywhere in the tree
        if (textBuilder.isBlank()) {
            val subscribeButtons = root.findAccessibilityNodeInfosByText("Subscribe to @")
            if (!subscribeButtons.isNullOrEmpty()) {
                val desc = subscribeButtons[0].contentDescription?.toString() ?: subscribeButtons[0].text?.toString() ?: ""
                if (desc.isNotBlank()) {
                    textBuilder += "$desc "
                }
            }
        }

        val cleaned = cleanYouTubeComparator(textBuilder)
        return cleaned.ifBlank { "youtube_shorts_active" }
    }

    private fun extractOverlaySubtreeInfo(node: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (node == null || depth > 8) return ""
        val sb = StringBuilder()

        val desc = node.contentDescription?.toString()
        val text = node.text?.toString()

        val valToUse = when {
            !desc.isNullOrBlank() && !isGenericControlText(desc) -> desc
            !text.isNullOrBlank() && !isGenericControlText(text) -> text
            else -> ""
        }

        if (valToUse.isNotBlank() && valToUse.length > 5) {
            sb.append(valToUse).append(" ")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childText = extractOverlaySubtreeInfo(child, depth + 1)
            if (childText.isNotBlank()) {
                sb.append(childText).append(" ")
            }
            @Suppress("DEPRECATION")
            child.recycle()
        }

        return sb.toString().trim()
    }

    private fun extractInstagramReelText(root: AccessibilityNodeInfo, pkg: String): String? {
        val viewer = root.findAccessibilityNodeInfosByViewId("$pkg:id/clips_viewer_view_pager")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/clips_swipe_refresh_layout") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/clips_view_pager") }
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/root_clips_layout") }
        if (viewer.isNullOrEmpty()) return null

        val authorNodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/clips_author_username")
        if (!authorNodes.isNullOrEmpty()) {
            val author = collectNodeText(authorNodes[0], maxDepth = 2)
            if (author.isNotBlank()) return author
        }

        val captionNodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/clips_captions_component")
        if (!captionNodes.isNullOrEmpty()) {
            val caption = collectNodeText(captionNodes[0], maxDepth = 3)
            if (caption.isNotBlank()) return caption
        }

        return "instagram_reel_active"
    }

    private fun extractSnapchatSpotlightText(root: AccessibilityNodeInfo, pkg: String): String? {
        val container = root.findAccessibilityNodeInfosByViewId("$pkg:id/spotlight_container")
        if (container.isNullOrEmpty()) return null

        val opera = root.findAccessibilityNodeInfosByViewId("$pkg:id/opera_viewer")
        if (opera.isNullOrEmpty()) return "snapchat_spotlight_active"

        val title = collectNodeText(opera[0], maxDepth = 4)
        return title.ifBlank { "snapchat_spotlight_active" }
    }

    private fun extractFacebookReelText(root: AccessibilityNodeInfo, pkg: String): String? {
        val reelsTab = root.findAccessibilityNodeInfosByText("Reels tab details")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reels_viewer_fragment") }
        if (reelsTab.isNullOrEmpty()) return null

        val text = collectNodeText(reelsTab[0], maxDepth = 4)
        return text.ifBlank { "facebook_reel_active" }
    }

    private fun extractTikTokReelText(root: AccessibilityNodeInfo, pkg: String): String? {
        val viewer = root.findAccessibilityNodeInfosByViewId("$pkg:id/view_pager_layout_wrapper")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/long_press_layout") }
        if (viewer.isNullOrEmpty()) return null

        val title = root.findAccessibilityNodeInfosByViewId("$pkg:id/title")
        if (!title.isNullOrEmpty()) {
            val text = collectNodeText(title[0], maxDepth = 2)
            if (text.isNotBlank()) return text
        }
        return "tiktok_active"
    }

    private fun collectNodeText(node: AccessibilityNodeInfo?, maxDepth: Int, currentDepth: Int = 0): String {
        if (node == null || currentDepth > maxDepth) return ""
        val sb = StringBuilder()

        val text = node.text?.toString()
        if (!text.isNullOrBlank() && !isGenericControlText(text)) {
            sb.append(text).append(" ")
        }
        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank() && desc != text && !isGenericControlText(desc) && desc.length < 150) {
            sb.append(desc).append(" ")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(collectNodeText(child, maxDepth, currentDepth + 1))
            @Suppress("DEPRECATION")
            child.recycle()
        }

        return sb.toString().trim()
    }

    private fun isGenericControlText(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower == "pause video" ||
                lower == "play video" ||
                lower == "pause" ||
                lower == "play" ||
                lower == "video progress" ||
                lower == "tap to watch live" ||
                lower == "go to channel" ||
                lower == "sound" ||
                lower == "mute" ||
                lower == "unmute" ||
                lower == "join this channel" ||
                lower == "share this video" ||
                lower == "remix" ||
                lower == "save" ||
                lower.startsWith("double tap to") ||
                lower.contains("postpostpostlike")
    }

    private fun cleanYouTubeComparator(value: String): String {
        val compact = value.replace("\n", "")
        if (compact.contains("PostPostPostlike") || compact.length <= 5) return ""
        return compact.replace("Video Progress", "")
            .replace("Tap to watch live", "")
            .replace("Go to channel", "")
            .replace("soundVideo ProgressSearchMoreHomeHomeShortsShortsCreateSubscriptions", "")
            .replace("soundSearchMoreHomeHomeShortsShortsCreateSubscriptions", "")
    }
}