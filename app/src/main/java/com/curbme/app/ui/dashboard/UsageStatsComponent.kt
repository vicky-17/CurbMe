package com.curbme.app.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.curbme.app.data.models.AppUsageInfo
import com.curbme.app.ui.theme.CurbMeTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.03f),
        Color.White.copy(alpha = 0.20f),
        Color.White.copy(alpha = 0.03f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim.value - 300f, y = translateAnim.value - 300f),
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    return this.background(brush)
}

@Composable
fun ShimmerUsageSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Hero Skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .shimmerEffect()
            )

            Spacer(Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .shimmerEffect()
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmerEffect()
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .shimmerEffect()
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .shimmerEffect()
        )

        Spacer(Modifier.height(20.dp))

        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .shimmerEffect()
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                }
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
            }
        }
    }
}

@Composable
fun UsageStatsSection(
    viewModel: UsageViewModel,
    selectedTab: Int = 0, // 0 = Daily, 1 = Weekly
    onOpenAllStats: () -> Unit
) {
    val todayStats by viewModel.todayStats.collectAsState()
    val weeklyStats by viewModel.weeklyTotalStats.collectAsState()
    val weeklyDateRangeLabel by viewModel.weeklyDateRangeLabel.collectAsState()
    val weeklyTotalTimeMs by viewModel.weeklyTotalTimeMs.collectAsState()

    val comparisonPercent by viewModel.comparisonPercent.collectAsState()
    val isPermissionGranted by viewModel.isPermissionGranted.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    val activeStats = if (selectedTab == 1) weeklyStats else todayStats
    val dateLabel = if (selectedTab == 1) {
        if (weeklyDateRangeLabel.isNotBlank()) weeklyDateRangeLabel else "This Week"
    } else {
        val todayStr = remember { SimpleDateFormat("d MMM", Locale.getDefault()).format(Date()) }
        "Today, $todayStr"
    }
    val subtitleText = if (selectedTab == 1) {
        val avgMs = (weeklyTotalTimeMs / 7).coerceAtLeast(0L)
        "Avg: ${formatDuration(avgMs)} / day"
    } else {
        val isLess = comparisonPercent <= 0
        "${if (isLess) "↓" else "↑"} ${abs(comparisonPercent)}% vs yesterday"
    }

    UsageStatsSection(
        usageStats = activeStats,
        headerTitle = if (selectedTab == 1) "Weekly Screen Time" else "Total Screen Time",
        dateRangeLabel = dateLabel,
        subtitleText = subtitleText,
        isLessVsPrevious = comparisonPercent <= 0,
        comparisonPercent = comparisonPercent,
        isPermissionGranted = isPermissionGranted,
        isLoading = isLoading,
        onOpenAllStats = onOpenAllStats,
        onGrantPermission = {
            try {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {}
        }
    )
}

@Composable
fun UsageStatsSection(
    usageStats: List<AppUsageInfo>,
    headerTitle: String = "Total Screen Time",
    dateRangeLabel: String = "",
    subtitleText: String = "",
    isLessVsPrevious: Boolean = true,
    comparisonPercent: Int = 0,
    isPermissionGranted: Boolean = true,
    isLoading: Boolean = false,
    onOpenAllStats: () -> Unit = {},
    onGrantPermission: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.glassBg),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, CurbMeTheme.colors.glassBorder)
        ) {
            if (!isPermissionGranted) {
                PermissionRequestCard(onGrant = onGrantPermission)
            } else {
                Crossfade(
                    targetState = isLoading && usageStats.isEmpty(),
                    animationSpec = tween(400),
                    label = "usageStatsLoading"
                ) { loading ->
                    if (loading) {
                        ShimmerUsageSkeleton()
                    } else {
                        Column {
                            UsageHero(
                                stats = usageStats,
                                headerTitle = headerTitle,
                                dateRangeLabel = dateRangeLabel,
                                subtitleText = subtitleText,
                                isLessVsPrevious = isLessVsPrevious
                            )

                            Spacer(Modifier.height(24.dp))

                            InsightCard(comparisonPercent)

                            Spacer(Modifier.height(14.dp))

                            MostUsedAppsSection(usageStats, onOpenAllStats)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageHero(
    stats: List<AppUsageInfo>,
    headerTitle: String,
    dateRangeLabel: String,
    subtitleText: String,
    isLessVsPrevious: Boolean
) {
    // Exclude Launcher time from the total to match Digital Wellbeing's "Screen Time"
    val activeStats = stats.filter { it.category != "Launcher" }
    val totalTimeMs = activeStats.sumOf { it.usageTimeMs }
    
    // Take 3 specific apps, "Others" will be the 4th item
    val topApps = activeStats.take(3)
    val remainingMs = totalTimeMs - topApps.sumOf { it.usageTimeMs }

    val colors = listOf(
        CurbMeTheme.colors.accentBlue,
        CurbMeTheme.colors.accentViolet,
        CurbMeTheme.colors.accentPink,
        CurbMeTheme.colors.accentAmber,
        CurbMeTheme.colors.accentCyan
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Left Side: Precision Dual Ring Layout ────────────────────────
        Box(
            modifier = Modifier
                .weight(1.35f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            UsageRingLayout(topApps, totalTimeMs.toFloat().coerceAtLeast(1f), colors)

            // ── Inner Solid Circle (Strictly contains all text safely) ──
            Box(
                modifier = Modifier
                    .fillMaxSize(0.74f)
                    .clip(CircleShape)
                    .background(CurbMeTheme.colors.bgDeep)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(headerTitle, color = CurbMeTheme.colors.textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(
                        text = formatDuration(totalTimeMs),
                        color = CurbMeTheme.colors.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (dateRangeLabel.isNotBlank()) {
                        Text(dateRangeLabel, color = CurbMeTheme.colors.textSecondary, fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 1)
                    }

                    Spacer(Modifier.height(2.dp))

                    if (subtitleText.isNotBlank()) {
                        Text(
                            text = subtitleText,
                            color = if (isLessVsPrevious) CurbMeTheme.colors.accentGreen else Color(0xFFF87171),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        // ── Right Side: Compact Legend ──────────────────────────────────
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            topApps.forEachIndexed { index, app ->
                LegendItem(app.appName, formatDurationShort(app.usageTimeMs), colors[index % colors.size], app.icon)
            }
            if (remainingMs > 0) {
                LegendItem("Others", formatDurationShort(remainingMs), Color.White.copy(alpha = 0.12f), null)
            }
        }
    }
}

@Composable
private fun LegendItem(name: String, duration: String, color: Color, icon: Any?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.5.dp, height = 28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )

        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            icon?.let {
                Image(
                    painter = rememberAsyncImagePainter(it),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } ?: Box(Modifier.size(20.dp).background(Color.White.copy(0.08f), CircleShape))
        }

        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                name,
                color = CurbMeTheme.colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                lineHeight = 16.sp
            )
            Text(
                text = duration,
                color = CurbMeTheme.colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun UsageRingLayout(topApps: List<AppUsageInfo>, totalMs: Float, colors: List<Color>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val ringRadius = (size.minDimension / 2f) * 0.9f
        val strokeWidth = 16.dp.toPx()
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // ── Circle 1: The Background Track (Subtle Outer Rim) ────────────────
        drawCircle(
            color = Color.White.copy(alpha = 0.05f),
            radius = ringRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = strokeWidth)
        )

        val topAppsTotal = topApps.sumOf { it.usageTimeMs }.toFloat()

        // ── 1. Draw "Others" segment FIRST (so it is at the bottom of the stack) ──
        if (topAppsTotal < totalMs) {
            val topAppsSweep = (topAppsTotal / totalMs) * 360f
            val remainingSweep = 360f - topAppsSweep
            drawArc(
                color = Color.White.copy(alpha = 0.15f),
                startAngle = -90f + topAppsSweep,
                sweepAngle = remainingSweep,
                useCenter = false,
                topLeft = Offset(centerX - ringRadius, centerY - ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // ── 2. Draw Apps (on top of the Others segment) ──
        var startAngle = -90f
        topApps.forEachIndexed { index, app ->
            val sweepAngle = (app.usageTimeMs.toFloat() / totalMs) * 360f
            val color = colors[index % colors.size]

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle * 0.98f, // Slight gap for clarity
                useCenter = false,
                topLeft = Offset(centerX - ringRadius, centerY - ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            startAngle += sweepAngle
        }
    }
}


@Composable
private fun InsightCard(comparison: Int) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 10.dp),
        colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.glassBg),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CurbMeTheme.colors.glassBorder)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Brush.linearGradient(listOf(CurbMeTheme.colors.accentBlue.copy(0.5f), CurbMeTheme.colors.accentViolet.copy(0.5f))), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFeaf1ff), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            val isLess = comparison <= 0
            Text(
                text = buildAnnotatedString {
                    append("Your screen time is ")
                    withStyle(SpanStyle(color = CurbMeTheme.colors.accentGreen, fontWeight = FontWeight.Bold)) {
                        append("${abs(comparison)}% ${if (isLess) "less" else "more"}")
                    }
                    append(" than yesterday. ${if (isLess) "Keep it up!" else "Try to focus!"}")
                },
                color = CurbMeTheme.colors.textSecondary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Rounded.ChevronRight, null, tint = CurbMeTheme.colors.textMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MostUsedAppsSection(stats: List<AppUsageInfo>, onShowAll: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Most used apps", color = CurbMeTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            Text(
                "Show all",
                color = Color(0xFF7fb4ff),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onShowAll() }
            )
        }

        Spacer(Modifier.height(14.dp))

        val topApps = stats.take(3)
        val maxUsage = topApps.firstOrNull()?.usageTimeMs?.toFloat() ?: 1f

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            topApps.forEachIndexed { index, app ->
                AppRow(app, app.usageTimeMs.toFloat() / maxUsage, index)
            }
        }
    }
}

@Composable
private fun AppRow(app: AppUsageInfo, fraction: Float, index: Int) {
    val colors = listOf(CurbMeTheme.colors.accentBlue, CurbMeTheme.colors.accentViolet, CurbMeTheme.colors.accentGreen)
    Row(
        modifier = Modifier.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        app.icon?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(app.appName, color = CurbMeTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(formatDurationShort(app.usageTimeMs), color = CurbMeTheme.colors.textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(colors[index % colors.size], colors[index % colors.size].copy(alpha = 0.5f))),
                            CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = CurbMeTheme.colors.textMuted, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun PermissionRequestCard(onGrant: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.bgElevated),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Usage Access Required", color = CurbMeTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            Text(
                "Enable insights to see your dashboard data.",
                color = CurbMeTheme.colors.textSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = CurbMeTheme.colors.accentBlue)) {
                Text("Grant Permission")
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    return com.curbme.app.core.utils.TimeUtils.formatDuration(ms)
}

private fun formatDurationShort(ms: Long): String {
    return com.curbme.app.core.utils.TimeUtils.formatDurationShort(ms)
}

@Preview(showBackground = true, backgroundColor = 0xFF04040c)
@Composable
fun UsageStatsPreview() {
    val mockUsageData = listOf(
        AppUsageInfo("com.google.android.youtube", "YouTube", null, 120 * 60 * 1000L, 10),
        AppUsageInfo("com.instagram.android", "Instagram", null, 45 * 60 * 1000L, 5),
        AppUsageInfo("com.zhiliaoapp.musically", "TikTok", null, 30 * 60 * 1000L, 8),
        AppUsageInfo("com.whatsapp", "WhatsApp", null, 15 * 60 * 1000L, 20),
        AppUsageInfo("com.android.chrome", "Chrome", null, 10 * 60 * 1000L, 3)
    )

    Box(modifier = Modifier.background(Color(0xFF04040c)).padding(16.dp)) {
        UsageStatsSection(
            usageStats = mockUsageData,
            comparisonPercent = -15,
            isPermissionGranted = true,
            onOpenAllStats = {},
            onGrantPermission = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF04040c)
@Composable
fun UsageStatsPermissionPreview() {
    Box(modifier = Modifier.background(Color(0xFF04040c)).padding(16.dp)) {
        UsageStatsSection(
            usageStats = emptyList(),
            comparisonPercent = 0,
            isPermissionGranted = false,
            onOpenAllStats = {},
            onGrantPermission = {}
        )
    }
}
