package com.curbme.app.ui.block

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbme.app.ui.theme.CurbMeTheme

// ── Data model for a gate action button ──────────────────────────────────────

/**
 * Describes a single action button on the gate screen.
 *
 * @param label      Button text shown to the user.
 * @param isPrimary  true → filled blue button; false → outlined secondary button.
 * @param onClick    Lambda called when the button is tapped.
 */
data class GateAction(
    val label: String,
    val isPrimary: Boolean = true,
    val onClick: () -> Unit
)

// ── Severity determines the accent colour of the icon ring ───────────────────

enum class GateSeverity {
    /** Red ring — something is actively broken or bypassed. */
    CRITICAL,
    /** Amber ring — permission missing but not yet exploited. */
    WARNING,
    /** Blue ring — informational / setup nudge. */
    INFO
}

// ── Main composable ───────────────────────────────────────────────────────────

/**
 * ProtectionGateScreen
 *
 * A full-screen blocking UI that covers the device when a protection gap is
 * detected. All content is data-driven so the same composable handles every
 * scenario: VPN bypass, accessibility off, overlay missing, etc.
 *
 * Touch events on the background are consumed so the child cannot interact
 * with whatever is behind the gate. Action buttons are excluded from that
 * consume so they remain tappable.
 *
 * @param emoji       Large emoji shown at the top (e.g. "🛡️", "⚠️", "🔒").
 * @param title       Bold headline (e.g. "Protection Disabled").
 * @param message     Body text explaining what's wrong.
 * @param severity    Controls the accent ring colour around the emoji.
 * @param steps       Optional numbered steps shown below the message.
 * @param actions     List of [GateAction] buttons. First primary action is
 *                    rendered as the main CTA; secondary actions follow below.
 */
@Composable
fun ProtectionGateScreen(
    emoji: String,
    title: String,
    message: String,
    severity: GateSeverity = GateSeverity.WARNING,
    steps: List<String> = emptyList(),
    actions: List<GateAction> = emptyList()
) {
    // Pulse animation on the icon ring — draws the eye without being distracting
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue  = 1.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val ringColor = when (severity) {
        GateSeverity.CRITICAL -> CurbMeTheme.colors.accentRed
        GateSeverity.WARNING  -> CurbMeTheme.colors.accentAmber
        GateSeverity.INFO     -> CurbMeTheme.colors.accentBlue
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CurbMeTheme.colors.bgDeep, CurbMeTheme.colors.bgDeep)))
            // Consume all background touches — child cannot tap through the gate
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp)
                // This column's touches pass through (buttons need to be tappable)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
        ) {

            // ── Icon with animated ring ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(ringColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(ringColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 38.sp)
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Title ─────────────────────────────────────────────────────────
            Text(
                text       = title,
                color      = CurbMeTheme.colors.textPrimary,
                fontSize   = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                lineHeight = 30.sp
            )

            Spacer(Modifier.height(12.dp))

            // ── Message ───────────────────────────────────────────────────────
            Text(
                text      = message,
                color     = CurbMeTheme.colors.textSecondary,
                fontSize  = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            // ── Steps (optional) ──────────────────────────────────────────────
            if (steps.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.bgCard),
                    shape  = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "How to fix",
                            color      = ringColor,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        steps.forEachIndexed { index, step ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                // Step number badge
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(ringColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${index + 1}",
                                        color      = ringColor,
                                        fontSize   = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    step,
                                    color      = CurbMeTheme.colors.textPrimary.copy(alpha = 0.9f),
                                    fontSize   = 13.sp,
                                    lineHeight = 18.sp,
                                    modifier   = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Action buttons ────────────────────────────────────────────────
            actions.forEach { action ->
                if (action.isPrimary) {
                    Button(
                        onClick  = action.onClick,
                        colors   = ButtonDefaults.buttonColors(containerColor = ringColor),
                        shape    = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            action.label,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick  = action.onClick,
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = CurbMeTheme.colors.textSecondary
                        ),
                        shape    = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(action.label, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "VPN Bypass — Critical", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
private fun PreviewCritical() {
    CurbMeTheme {
        ProtectionGateScreen(
            emoji    = "⚠️",
            title    = "VPN Filter Bypassed",
            message  = "Another VPN app is active on this device. CurbMe's content filter is not running.",
            severity = GateSeverity.CRITICAL,
            steps    = listOf(
                "Disconnect the other VPN app",
                "Return to CurbMe dashboard",
                "Re-enable SafeSearch & Web Filter"
            ),
            actions  = listOf(
                GateAction("Go to Home Screen") {},
                GateAction("Open CurbMe", isPrimary = false) {}
            )
        )
    }
}

@Preview(name = "Permissions Missing — Warning", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
private fun PreviewWarning() {
    CurbMeTheme {
        ProtectionGateScreen(
            emoji    = "🔒",
            title    = "Permission Required",
            message  = "Accessibility access was turned off. App blocking and Shorts filtering are not active.",
            severity = GateSeverity.WARNING,
            steps    = listOf(
                "Tap 'Fix Permission' below",
                "Find CurbMe in the list",
                "Enable the toggle",
                "Return here — protection activates instantly"
            ),
            actions  = listOf(
                GateAction("Fix Permission") {},
                GateAction("Go to Home Screen", isPrimary = false) {}
            )
        )
    }
}

@Preview(name = "Info — Always-On VPN", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
private fun PreviewInfo() {
    CurbMeTheme {
        ProtectionGateScreen(
            emoji    = "🛡️",
            title    = "Lock Down the Filter",
            message  = "Always-On VPN is not set. A reboot or network change could disable the content filter.",
            severity = GateSeverity.INFO,
            actions  = listOf(
                GateAction("Open VPN Settings") {},
                GateAction("Remind me later", isPrimary = false) {}
            )
        )
    }
}