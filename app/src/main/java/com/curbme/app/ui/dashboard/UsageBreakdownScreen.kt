package com.curbme.app.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.curbme.app.core.utils.TimeUtils
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.entity.WebsiteStatsEntity
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.data.models.AppUsageInfo
import com.curbme.app.ui.contentfilter.WebsiteUsageCard
import com.curbme.app.ui.theme.CurbMeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageBreakdownScreen(
    viewModel: UsageViewModel,
    onBack: () -> Unit
) {
    val weeklyData by viewModel.weeklyData.collectAsState()
    val selectedIndex by viewModel.selectedDayIndex.collectAsState()
    val usageStats by viewModel.usageStats.collectAsState()
    val weekOffset by viewModel.weekOffset.collectAsState()

    UsageBreakdownContent(
        weeklyData = weeklyData,
        selectedIndex = selectedIndex,
        usageStats = usageStats,
        weekOffset = weekOffset,
        onChangeWeek = { viewModel.changeWeek(it) },
        onSelectDay = { viewModel.selectDay(it) },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageBreakdownContent(
    weeklyData: List<DayUsageData>,
    selectedIndex: Int,
    usageStats: List<AppUsageInfo>,
    weekOffset: Int,
    onChangeWeek: (Int) -> Unit,
    onSelectDay: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var trackedWebsites by remember { mutableStateOf<List<WebsiteStatsEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedIndex, weekOffset) {
        try {
            val db = AppDatabase.getDatabase(context)
            val today = TimeUtils.todayKey()
            trackedWebsites = db.websiteStatsDao().getForDate(today)
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        CurbMeTheme.colors.bgDeep,
                        Color(0xFF080B1A), // Deep subtle tint
                        CurbMeTheme.colors.bgDeep
                    )
                )
            )
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Usage Statistics", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ── Week Selector ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { onChangeWeek(-1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = CurbMeTheme.colors.accentCyan)
                    }
                    
                    Text(
                        text = getWeekRangeLabel(weekOffset),
                        color = CurbMeTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    IconButton(
                        onClick = { onChangeWeek(1) },
                        enabled = weekOffset < 0
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight, 
                            null, 
                            tint = if (weekOffset < 0) CurbMeTheme.colors.accentCyan else CurbMeTheme.colors.textSecondary.copy(alpha = 0.3f)
                        )
                    }
                }

                // ── Weekly Activity Chart ───────────────────────────────────────
                var dragOffsetX by remember { mutableFloatStateOf(0f) }
                val animatableOffset = remember { Animatable(0f) }
                var isDragging by remember { mutableStateOf(false) }

                val displayOffset = if (isDragging) dragOffsetX else animatableOffset.value

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                        .pointerInput(weekOffset) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    dragOffsetX = 0f
                                },
                                onDragEnd = {
                                    isDragging = false
                                    val currentDrag = dragOffsetX
                                    scope.launch {
                                        animatableOffset.snapTo(currentDrag)
                                        val threshold = 90f
                                        if (currentDrag > threshold) {
                                            onChangeWeek(-1)
                                        } else if (currentDrag < -threshold && weekOffset < 0) {
                                            onChangeWeek(1)
                                        }
                                        animatableOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    val currentDrag = dragOffsetX
                                    scope.launch {
                                        animatableOffset.snapTo(currentDrag)
                                        animatableOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val candidate = dragOffsetX + dragAmount
                                    dragOffsetX = if (weekOffset >= 0 && candidate < 0) {
                                        dragOffsetX + dragAmount * 0.2f
                                    } else {
                                        candidate
                                    }
                                }
                            )
                        },
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "WEEKLY ACTIVITY",
                                color = CurbMeTheme.colors.accentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "Swipe ◄ ►",
                                color = CurbMeTheme.colors.textSecondary.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(24.dp))

                        AnimatedContent(
                            targetState = Pair(weekOffset, weeklyData),
                            transitionSpec = {
                                if (targetState.first < initialState.first) {
                                    (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it / 2 } + fadeIn()) togetherWith
                                    (slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeOut())
                                } else {
                                    (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn()) togetherWith
                                    (slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it / 2 } + fadeOut())
                                }
                            },
                            label = "WeeklyChartAnimation",
                            modifier = Modifier.graphicsLayer {
                                translationX = displayOffset
                            }
                        ) { (_, currentWeeklyData) ->
                            WeeklyBarChart(
                                data = currentWeeklyData,
                                selectedIndex = selectedIndex,
                                onDaySelected = onSelectDay
                            )
                        }
                    }
                }

                // ── Selected Day Summary ────────────────────────────────────────
                val totalTime = usageStats.sumOf { it.usageTimeMs }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = formatDuration(totalTime),
                        color = CurbMeTheme.colors.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (weekOffset == 0 && selectedIndex == 6) "TOTAL TODAY" else "DAILY TOTAL",
                        color = CurbMeTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // ── App List ────────────────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(usageStats) { app ->
                        DetailedAppUsageRow(app)
                    }

                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        WebsiteUsageCard(
                            websites = trackedWebsites,
                            onBlockDomain = { domain ->
                                val dataStore = DataStoreManager(context)
                                scope.launch(Dispatchers.IO) {
                                    dataStore.updateSettings { it.copy(blockedWebsites = it.blockedWebsites + domain) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyBarChart(
    data: List<DayUsageData>,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit
) {
    val barAnimProgress = remember { Animatable(0f) }
    val accentCyan = CurbMeTheme.colors.accentCyan

    LaunchedEffect(data) {
        barAnimProgress.snapTo(0f)
        barAnimProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        
        val barAreaWidth = width * 0.9f
        val startOffset = (width - barAreaWidth) / 2f
        val gap = barAreaWidth / 7f
        val barWidth = gap * 0.4f
        
        val maxValue = data.maxOfOrNull { it.valueHours } ?: 1f
        val effectiveMax = if (maxValue == 0f) 1f else maxValue

        Canvas(modifier = Modifier.fillMaxSize()) {
            data.forEachIndexed { i, day ->
                val x = startOffset + gap * i + gap / 2f
                val ratio = day.valueHours / effectiveMax
                val targetBarHeight = (height * 0.7f * ratio).coerceAtLeast(4.dp.toPx())
                val animatedBarHeight = targetBarHeight * barAnimProgress.value

                // Draw Bar
                drawRoundRect(
                    color = if (i == selectedIndex) accentCyan else accentCyan.copy(alpha = 0.3f),
                    topLeft = Offset(x - barWidth / 2f, height * 0.8f - animatedBarHeight),
                    size = Size(barWidth, animatedBarHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
        
        val density = LocalDensity.current
        val paddingHorizontal = with(density) { startOffset.toDp() }

        // Touch layer & Labels
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = paddingHorizontal),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEachIndexed { i, day ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onDaySelected(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        day.label,
                        color = if (i == selectedIndex) CurbMeTheme.colors.accentCyan else CurbMeTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (i == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailedAppUsageRow(app: AppUsageInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CurbMeTheme.colors.glassBorder, CurbMeTheme.shapes.cardLarge),
        colors = CardDefaults.cardColors(containerColor = CurbMeTheme.colors.glassBg),
        shape = CurbMeTheme.shapes.cardLarge
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = rememberAsyncImagePainter(app.icon),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, color = CurbMeTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.category.uppercase(), color = CurbMeTheme.colors.accentCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "• ${app.launchCount} launches",
                        color = CurbMeTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                formatDuration(app.usageTimeMs),
                color = CurbMeTheme.colors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp
            )
        }
    }
}

private fun getWeekRangeLabel(offset: Int): String {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    calendar.add(Calendar.WEEK_OF_YEAR, offset)
    val start = calendar.time
    
    calendar.add(Calendar.DAY_OF_YEAR, 6)
    val end = calendar.time
    
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    return "${sdf.format(start)} – ${sdf.format(end)}"
}

private fun formatDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Preview(showBackground = true)
@Composable
fun UsageBreakdownScreenPreview() {
    val sampleWeeklyData = listOf(
        DayUsageData("M", 2.5f, Calendar.getInstance()),
        DayUsageData("T", 4.0f, Calendar.getInstance()),
        DayUsageData("W", 1.8f, Calendar.getInstance()),
        DayUsageData("T", 3.2f, Calendar.getInstance()),
        DayUsageData("F", 5.0f, Calendar.getInstance()),
        DayUsageData("S", 6.1f, Calendar.getInstance()),
        DayUsageData("S", 2.0f, Calendar.getInstance())
    )

    val sampleUsageStats = listOf(
        AppUsageInfo("com.google.android.youtube", "YouTube", null, 120 * 60 * 1000L, 10, "Entertainment"),
        AppUsageInfo("com.instagram.android", "Instagram", null, 45 * 60 * 1000L, 5, "Social"),
        AppUsageInfo("com.zhiliaoapp.musically", "TikTok", null, 30 * 60 * 1000L, 8, "Social"),
        AppUsageInfo("com.whatsapp", "WhatsApp", null, 15 * 60 * 1000L, 20, "Communication")
    )

    CurbMeTheme {
        UsageBreakdownContent(
            weeklyData = sampleWeeklyData,
            selectedIndex = 4,
            usageStats = sampleUsageStats,
            weekOffset = 0,
            onChangeWeek = {},
            onSelectDay = {},
            onBack = {}
        )
    }
}
