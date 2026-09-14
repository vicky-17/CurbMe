package com.curbme.app.ui.locks

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.sp
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.data.local.db.AppDatabase // Add this
import com.curbme.app.core.utils.AppIconManager
import com.curbme.app.data.local.db.entity.AppBlockRule // Add this
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ── Color palette ─────────────────────────────────────────────────────────────
private val ScreenBg   = Color(0xFF080E1A)
private val CardBg     = Color(0xFF111827)
private val AccentCyan = Color(0xFF06B6D4)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecond  = Color(0xFF64748B)
private val AccentRed   = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocksScreen(prefs: PrefsManager) {
    val context = LocalContext.current
    val viewModel: LocksViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = AppDatabase.getDatabase(context.applicationContext) // Get DB
                return LocksViewModel(context.applicationContext, prefs, db.appBlockDao()) as T
            }
        },
    )

    val activeRules by viewModel.activeRules.collectAsState()
    val wizardState by viewModel.wizardState.collectAsState()
    val websites by viewModel.blockedWebsites.collectAsState()
    val visitedWebsites by viewModel.visitedWebsites.collectAsState()

    val groupedRules by remember(activeRules) {
        derivedStateOf { activeRules.groupBy { it.planName.ifBlank { "Unnamed Plan" } } }
    }

    var showWizard by remember { mutableStateOf(value = false) }
    var showAddWebsiteDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { newValue ->
            // Return false to prevent the sheet from being hidden via gestures
            newValue != SheetValue.Hidden
        })
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Apps", "Websites")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .padding(bottom = 24.dp),
    ) {
        // Removed LocksHeader() to use global Digital Monk header

        Spacer(Modifier.height(14.dp))

        // ── Apps/Websites Tabs (Liquid Glass) ─────────────────────────────────
        GlassTabs(
            tabs = tabs,
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        Spacer(Modifier.height(20.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 0) {
                // ── Apps Tab (Active Plans) ──────────────────────────────────
                if (groupedRules.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = TextSecond, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No active app plans", color = TextSecond)
                            Text("Click + to add your first plan", color = TextSecond.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
                    ) {
                        items(groupedRules.keys.toList(), key = { it }) { planName ->
                            val rules = groupedRules[planName] ?: emptyList()
                            val isEnforced = rules.any { it.stopChallengeType == "ENFORCED" && it.expiryTimestamp > System.currentTimeMillis() }
                            ActivePlanCard(
                                planName = planName,
                                rules = rules,
                                isEnforced = isEnforced,
                                onDelete = { viewModel.removePlan(planName) }
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { showWizard = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                    containerColor = AccentCyan,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Plan")
                }

            } else {
                // ── Websites Tab ──────────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Section 1: Blocked Websites
                    item {
                        Text(
                            text = "Blocked Websites (${websites.size})",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }

                    if (websites.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No blocked websites yet", color = TextSecond, fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(websites.toList()) { domain ->
                            WebsiteLockItem(domain = domain) {
                                viewModel.removeWebsite(domain)
                            }
                        }
                    }

                    // Section 2: Web History & Instant Lock
                    item {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Web History (${visitedWebsites.size})",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val recentSites = visitedWebsites.distinctBy { it.domain.lowercase() }
                    if (recentSites.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No web history recorded today", color = TextSecond, fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(recentSites) { site ->
                            val isAlreadyBlocked = websites.contains(site.domain.lowercase())
                            WebsiteHistoryItem(
                                domain = site.domain,
                                isBlocked = isAlreadyBlocked,
                                onLock = { viewModel.lockVisitedWebsite(site.domain) }
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { showAddWebsiteDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                    containerColor = AccentCyan,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Website")
                }
            }
        }

        if (showWizard) {
            ModalBottomSheet(
                onDismissRequest = { showWizard = false },
                sheetState = sheetState,
                containerColor = ScreenBg,
                dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecond.copy(alpha = 0.5f)) }
            ) {
                AppBlockWizard(
                    state = wizardState,
                    viewModel = viewModel
                ) {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        showWizard = false
                    }
                }
            }
        }

        if (showAddWebsiteDialog) {
            AddWebsiteDialog(
                onDismiss = { showAddWebsiteDialog = false },
                onConfirm = { domain ->
                    viewModel.addWebsite(domain)
                    showAddWebsiteDialog = false
                }
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivePlanCard(planName: String, rules: List<AppBlockRule>, isEnforced: Boolean, onDelete: () -> Unit) {
    val firstRule = rules.firstOrNull() ?: return
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(AccentCyan.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when(firstRule.planType) {
                            "STAY_FOCUSED" -> Icons.Rounded.Bolt
                            "TIME_LIMIT" -> Icons.Rounded.Timer
                            "HABIT_TRAINING" -> Icons.Rounded.Psychology
                            else -> Icons.Rounded.Coffee
                        },
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(planName, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    val strategyLabel = firstRule.planType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                    Text(strategyLabel, color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onDelete,
                    enabled = !isEnforced,
                    modifier = Modifier.background(
                        if (isEnforced) TextSecond.copy(alpha = 0.1f) else AccentRed.copy(alpha = 0.1f),
                        CircleShape
                    ).size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isEnforced) Icons.Rounded.Lock else Icons.Rounded.Delete,
                        contentDescription = if (isEnforced) "Delete" else "Enforced",
                        tint = if (isEnforced) TextSecond else AccentRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Apps Preview (Stacked Icons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.padding(start = 8.dp)) {
                    val displayCount = 4
                    rules.take(displayCount).forEachIndexed { index, rule ->
                        Box(
                            modifier = Modifier
                                .offset(x = (index * 20).dp)
                                .size(32.dp)
                                .background(CardBg, CircleShape)
                                .border(2.dp, CardBg, CircleShape)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                        ) {
                            val icon = remember(rule.packageName, rule.iconPath) {
                                val savedFile = rule.iconPath?.let { File(it) }
                                    ?: AppIconManager.getSavedIconFile(context, rule.packageName)
                                if (savedFile?.exists() == true) {
                                    savedFile
                                } else {
                                    try {
                                        context.packageManager.getApplicationIcon(rule.packageName)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                            }
                            Image(
                                painter = rememberAsyncImagePainter(icon),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    
                    if (rules.size > displayCount) {
                        Box(
                            modifier = Modifier
                                .offset(x = (displayCount * 20).dp)
                                .size(32.dp)
                                .background(AccentCyan, CircleShape)
                                .border(2.dp, CardBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+${rules.size - displayCount}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = if (isExpanded) "Hide details" else "View ${rules.size} apps",
                    color = AccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            // Expanded Apps List
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rules.forEach { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = remember(rule.packageName, rule.iconPath) {
                                val savedFile = rule.iconPath?.let { File(it) }
                                    ?: AppIconManager.getSavedIconFile(context, rule.packageName)
                                if (savedFile?.exists() == true) {
                                    savedFile
                                } else {
                                    try {
                                        context.packageManager.getApplicationIcon(rule.packageName)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                            }
                            Image(
                                painter = rememberAsyncImagePainter(icon),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(rule.appName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            Text(rule.packageName.split(".").lastOrNull() ?: "", color = TextSecond, fontSize = 10.sp)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Timing Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ScreenBg.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.History, null, tint = TextSecond, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                val timingText = when(firstRule.timingMode) {
                    "MULTI_DAY" -> {
                        val dateStr = remember(firstRule.expiryTimestamp) {
                            SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(firstRule.expiryTimestamp))
                        }
                        "Ends $dateStr"
                    }
                    "WEEKLY" -> "Recurring weekly"
                    else -> "Active on-demand"
                }
                Text(timingText, color = TextSecond, fontSize = 11.sp)
            }
        }
    }
}



@Composable
fun AppBlockWizard(
    state: WizardState,
    viewModel: LocksViewModel,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f) // Take up ~3/4 of the screen
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    ) {
        // Header with Progress
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Discard Action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { 
                            viewModel.resetWizard()
                            onDismiss()
                        }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Discard",
                        tint = AccentRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Text("Discard", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Center: Title & Progress
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val stepTitle = when(state.currentStep) {
                        1 -> "Choose Strategy"
                        2 -> "Target Apps"
                        3 -> "Trigger Mode"
                        4 -> "Security"
                        5 -> "Final Review"
                        else -> "Set up Plan"
                    }
                    Text(
                        text = stepTitle,
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Step ${state.currentStep} of 5",
                        color = TextSecond,
                        fontSize = 11.sp
                    )
                }

                // Right: Clear Action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { viewModel.clearCurrentStep() }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Clear",
                        tint = AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Text("Clear", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { state.currentStep / 5f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = AccentCyan,
                trackColor = CardBg,
            )
        }

        // Step Content
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                    }.using(SizeTransform(clip = false))
                },
                label = "StepTransition"
            ) { step ->
                when (step) {
                    1 -> Step1PlanType(state, viewModel)
                    2 -> Step2Applications(viewModel)
                    3 -> Step3TimingMode(state, viewModel)
                    4 -> Step5Protection(state, viewModel)
                    5 -> Step6Review(state, viewModel)
                }
            }
        }

        // Bottom Navigation
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardBg,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { if (state.currentStep > 1) viewModel.previousStep() else onDismiss() },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = if (state.currentStep > 1) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = TextSecond
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.currentStep > 1) "Back" else "Cancel", color = TextSecond, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = { if (state.currentStep < 5) viewModel.nextStep() else { viewModel.saveWizardPlan(); onDismiss() } },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp).padding(horizontal = 4.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(if (state.currentStep < 5) "Next" else "Finish", fontWeight = FontWeight.Bold)
                    if (state.currentStep < 5) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Step1PlanType(state: WizardState, viewModel: LocksViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Select how you want to manage these apps.", fontSize = 14.sp, color = TextSecond)

        Spacer(Modifier.height(24.dp))

        val plans = listOf(
            PlanItem("Stay Focused", "Total block for maximum productivity.", "STAY_FOCUSED", Icons.Rounded.Bolt),
            PlanItem("Time Limit", "Set a daily or hourly time budget.", "TIME_LIMIT", Icons.Rounded.Timer),
            PlanItem("Train Habits", "Limit how many times you launch.", "HABIT_TRAINING", Icons.Rounded.Psychology),
            PlanItem("Screen Breaks", "Enforce breaks away from the screen.", "SCREEN_BREAK", Icons.Rounded.Coffee)
        )

        plans.forEach { plan ->
            val isSelected = state.planType == plan.type
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { viewModel.updateWizard { it.copy(planType = plan.type) } },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) AccentCyan.copy(alpha = 0.12f) else CardBg
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) AccentCyan else TextSecond.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (isSelected) AccentCyan else TextSecond.copy(alpha = 0.1f),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = plan.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else TextSecond,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = plan.name,
                                color = if (isSelected) AccentCyan else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = plan.desc,
                                color = TextSecond,
                                fontSize = 12.sp
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = AccentCyan
                            )
                        }
                    }

                    // Configuration expansion for specific types
                    AnimatedVisibility(
                        visible = isSelected && (plan.type == "TIME_LIMIT" || plan.type == "HABIT_TRAINING"),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                        ) {
                            HorizontalDivider(
                                color = TextSecond.copy(alpha = 0.1f),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            if (plan.type == "TIME_LIMIT") {
                                val currentHours = state.allowedMinutes / 60
                                val currentMins = state.allowedMinutes % 60
                                
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Daily Limit", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            text = if (currentHours > 0) "${currentHours}h ${currentMins}m" else "${currentMins}m",
                                            color = AccentCyan,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp
                                        )
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        WizardStepper(
                                            label = "Hours",
                                            value = currentHours,
                                            unit = "h",
                                            modifier = Modifier.weight(1f),
                                            onValueChange = { delta ->
                                                viewModel.updateWizard { it.copy(allowedMinutes = (it.allowedMinutes + delta * 60).coerceAtLeast(0)) }
                                            }
                                        )
                                        WizardStepper(
                                            label = "Minutes",
                                            value = currentMins,
                                            unit = "m",
                                            modifier = Modifier.weight(1f),
                                            onValueChange = { delta ->
                                                viewModel.updateWizard { it.copy(allowedMinutes = (it.allowedMinutes + delta).coerceAtLeast(0)) }
                                            }
                                        )
                                    }
                                }
                            } else if (plan.type == "HABIT_TRAINING") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Max Launches",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    WizardStepper(
                                        label = "",
                                        value = state.maxLaunches,
                                        unit = "x",
                                        modifier = Modifier.width(110.dp),
                                        onValueChange = { delta ->
                                            viewModel.updateWizard { it.copy(maxLaunches = (it.maxLaunches + delta).coerceAtLeast(0)) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PlanItem(val name: String, val desc: String, val type: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun Step2Applications(viewModel: LocksViewModel) {
    val apps by viewModel.selectableApps.collectAsState()
    val wizardState by viewModel.wizardState.collectAsState()
    val isDataLoading by viewModel.isAppsLoading.collectAsState()

    // Local state to force at least 2 seconds of loading animation when entering this step
    var isAnimationRunning by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000.milliseconds)
        isAnimationRunning = false
    }

    val isLoading = isDataLoading || isAnimationRunning

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text("Select the apps you want to restrict.", fontSize = 14.sp, color = TextSecond)

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentCyan)
                    Spacer(Modifier.height(16.dp))
                    Text("Scanning apps...", color = TextSecond, fontSize = 13.sp)
                }
            }
        } else {
            val selectedApps = apps.filter { wizardState.selectedPackages.contains(it.packageName) }
            val socialApps = apps.filter { it.isSocial && !wizardState.selectedPackages.contains(it.packageName) }
            val otherApps = apps.filter { !it.isSocial && !wizardState.selectedPackages.contains(it.packageName) }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // ── Selected Apps Section ──────────────────────────────────
                if (selectedApps.isNotEmpty()) {
                    item { AppSectionHeader("Selected Apps", selectedApps.size) }
                    items(selectedApps, key = { "selected_${it.packageName}" }) { app ->
                        AppSelectorItem(app, true, viewModel)
                    }
                }

                // ── Social Media Recommendations ────────────────────────────
                if (socialApps.isNotEmpty()) {
                    item { AppSectionHeader("Recommended (Social Media)", socialApps.size) }
                    items(socialApps, key = { "social_${it.packageName}" }) { app ->
                        AppSelectorItem(app, false, viewModel)
                    }
                }

                // ── All Other Apps ──────────────────────────────────────────
                if (otherApps.isNotEmpty()) {
                    item { AppSectionHeader("All Apps", otherApps.size) }
                    items(otherApps, key = { "other_${it.packageName}" }) { app ->
                        AppSelectorItem(app, false, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            color = AccentCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(AccentCyan.copy(alpha = 0.1f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(count.toString(), color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AppSelectorItem(app: com.curbme.app.ui.locks.AppItem, isSelected: Boolean, viewModel: LocksViewModel) {
    val wizardState by viewModel.wizardState.collectAsState()
    val isAssignedElsewhere = app.assignedPlanName != null && app.assignedPlanName != wizardState.planName
    
    val alpha = if (isAssignedElsewhere) 0.4f else 1.0f

    Card(
        onClick = { if (!isAssignedElsewhere) viewModel.toggleAppSelection(app.packageName) },
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AccentCyan.copy(alpha = 0.08f) else Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
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
                Text(app.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (isAssignedElsewhere) {
                    Text("In Plan: ${app.assignedPlanName}", color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                } else {
                    Text(app.packageName, color = TextSecond, fontSize = 11.sp)
                }
            }

            if (!isAssignedElsewhere) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { viewModel.toggleAppSelection(app.packageName) },
                    colors = CheckboxDefaults.colors(checkedColor = AccentCyan)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Locked",
                    tint = TextSecond,
                    modifier = Modifier.size(20.dp).padding(end = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun Step3TimingMode(state: WizardState, viewModel: LocksViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
        Text("When should this restriction be active?", fontSize = 14.sp, color = TextSecond)

        Spacer(Modifier.height(24.dp))

        val modes = listOf(
            TimingModeItem("On-demand", "Start a session manually.", "ON_DEMAND", Icons.Rounded.TouchApp),
            TimingModeItem("Weekly Schedule", "Set recurring weekly time slots.", "WEEKLY", Icons.Rounded.Event),
            TimingModeItem("Pomodoro", "Structured focus sprints.", "POMODORO", Icons.Rounded.AvTimer),
            TimingModeItem("Multi-Day Plan", "Custom plan across multiple days.", "MULTI_DAY", Icons.Rounded.CalendarMonth)
        )

        modes.forEach { mode ->
            val isSelected = state.timingMode == mode.type
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { viewModel.updateWizard { it.copy(timingMode = mode.type) } },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) AccentCyan.copy(alpha = 0.12f) else CardBg
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) AccentCyan else TextSecond.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (isSelected) AccentCyan else TextSecond.copy(alpha = 0.1f),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else TextSecond,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.title, color = if (isSelected) AccentCyan else TextPrimary, fontWeight = FontWeight.Bold)
                            Text(mode.desc, color = TextSecond, fontSize = 12.sp)
                        }

                        if (isSelected) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = AccentCyan)
                        }
                    }

                    // Expand if Weekly Schedule is selected
                    AnimatedVisibility(
                        visible = isSelected && mode.type == "WEEKLY",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                        ) {
                            HorizontalDivider(color = TextSecond.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val days = listOf("M", "T", "W", "T", "F", "S", "S")
                                days.forEachIndexed { index, day ->
                                    val dayNum = index + 1
                                    val isDaySelected = state.selectedDays.contains(dayNum)
                                    
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = day,
                                            color = if (isDaySelected) AccentCyan else TextSecond,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Checkbox(
                                            checked = isDaySelected,
                                            onCheckedChange = { viewModel.toggleDaySelection(dayNum) },
                                            colors = CheckboxDefaults.colors(checkedColor = AccentCyan)
                                        )
                                    }
                                }
                            }
                        }
                    }


                    // Expand if Multi-Day Plan is selected

                    AnimatedVisibility(
                        visible = isSelected && mode.type == "MULTI_DAY",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 0.dp, top = 10.dp, bottom = 10.dp)
                        ) {
                            HorizontalDivider(color = TextSecond.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Text(
                                    text = "Choose how many days to block from today",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )


                                // Day Counter on Right
                                Box(
                                    modifier = Modifier
                                        .width(50.dp) // Narrower for right side
                                        .height(90.dp), // Tighter height to show 3 numbers
                                    contentAlignment = Alignment.Center
                                ) {
                                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (state.multiDayCount - 1).coerceAtLeast(0))
                                    
                                    val centerIndex by remember {
                                        derivedStateOf {
                                            val layoutInfo = listState.layoutInfo
                                            val visibleItemsInfo = layoutInfo.visibleItemsInfo
                                            if (visibleItemsInfo.isEmpty()) 0
                                            else {
                                                val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                                                visibleItemsInfo.minByOrNull { 
                                                    kotlin.math.abs((it.offset + it.size / 2) - center) 
                                                }?.index ?: 0
                                            }
                                        }
                                    }

                                    LaunchedEffect(centerIndex) {
                                        viewModel.updateWizard { it.copy(multiDayCount = centerIndex + 1) }
                                    }

                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(vertical = 30.dp), // Adjusted for 3-item visibility
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
                                    ) {
                                        items(60) { index ->
                                            val day = index + 1
                                            val isCurrent = centerIndex == index
                                            
                                            Text(
                                                text = day.toString(),
                                                color = if (isCurrent) AccentCyan else TextPrimary.copy(alpha = 0.15f),
                                                fontSize = if (isCurrent) 28.sp else 18.sp,
                                                fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Medium,
                                                modifier = Modifier.padding(vertical = 1.dp) // Minimum padding
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(20.dp))
                            }
                        }
                    }

                    // Expand if Pomodoro is selected
                    AnimatedVisibility(
                        visible = isSelected && mode.type == "POMODORO",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                        ) {
                            HorizontalDivider(color = TextSecond.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 16.dp))
                            
                            Text("Cycle Configuration", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                WizardStepper(
                                    label = "Focus",
                                    value = state.pomodoroFocus,
                                    unit = "m",
                                    modifier = Modifier.weight(1f),
                                    onValueChange = { delta -> viewModel.updateWizard { it.copy(pomodoroFocus = it.pomodoroFocus + delta) } }
                                )
                                WizardStepper(
                                    label = "Break",
                                    value = state.pomodoroShortBreak,
                                    unit = "m",
                                    modifier = Modifier.weight(1f),
                                    onValueChange = { delta -> viewModel.updateWizard { it.copy(pomodoroShortBreak = it.pomodoroShortBreak + delta) } }
                                )
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                WizardStepper(
                                    label = "Long Break",
                                    value = state.pomodoroLongBreak,
                                    unit = "m",
                                    modifier = Modifier.weight(1f),
                                    onValueChange = { delta -> viewModel.updateWizard { it.copy(pomodoroLongBreak = it.pomodoroLongBreak + delta) } }
                                )
                                WizardStepper(
                                    label = "Sets",
                                    value = state.pomodoroCycles,
                                    unit = "x",
                                    modifier = Modifier.weight(1f),
                                    onValueChange = { delta -> viewModel.updateWizard { it.copy(pomodoroCycles = it.pomodoroCycles + delta) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardStepper(
    label: String,
    value: Int,
    unit: String,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(label, color = TextSecond, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBg.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { if (value > 0) onValueChange(-1) }, 
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Rounded.Remove, null, tint = TextSecond, modifier = Modifier.size(16.dp))
            }
            Text("$value$unit", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { onValueChange(1) }, 
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Rounded.Add, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private data class TimingModeItem(val title: String, val desc: String, val type: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)


@Composable
private fun Step5Protection(state: WizardState, viewModel: LocksViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
        Text("Prevent yourself from bypassing the lock.", fontSize = 14.sp, color = TextSecond)

        Spacer(Modifier.height(24.dp))

        Text("Challenge to Exit", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))

        val challenges = listOf(
            Triple("None", "Exit freely anytime.", "NONE"),
            Triple("Random Text", "Type random characters to exit.", "RANDOM_CHARS"),
            Triple("Fixed PIN", "Enter your security PIN.", "PIN"),
            Triple("Enforced", "Cannot stop plan or remove until it ends.", "ENFORCED")
        )

        challenges.forEach { (title, desc, challenge) ->
            val isSelected = state.stopChallenge == challenge
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { viewModel.updateWizard { it.copy(stopChallenge = challenge) } },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) AccentCyan.copy(alpha = 0.08f) else CardBg
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isSelected) AccentCyan else TextSecond.copy(alpha = 0.05f))
            ) {
                Column {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.updateWizard { it.copy(stopChallenge = challenge) } },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(desc, color = TextSecond, fontSize = 11.sp)
                        }
                    }

                    if (isSelected && challenge == "ENFORCED") {
                        var showDatePicker by remember { mutableStateOf(false) }
                        val datePickerState = rememberDatePickerState(
                            selectableDates = object : SelectableDates {
                                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                                    // Only allow selecting dates from tomorrow onwards
                                    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                                    calendar.set(Calendar.MINUTE, 0)
                                    calendar.set(Calendar.SECOND, 0)
                                    calendar.set(Calendar.MILLISECOND, 0)
                                    val startOfToday = calendar.timeInMillis
                                    return utcTimeMillis > startOfToday
                                }
                            }
                        )

                        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                            HorizontalDivider(color = TextSecond.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 12.dp))
                            
                            OutlinedCard(
                                onClick = { showDatePicker = true },
                                colors = CardDefaults.cardColors(containerColor = ScreenBg.copy(alpha = 0.5f)),
                                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.CalendarToday, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(12.dp))
                                    val dateText = state.enforcedEndDate?.let {
                                        SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault()).format(Date(it))
                                    } ?: "Select End Date"
                                    Text(dateText, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = TextSecond, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        if (showDatePicker) {
                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.updateWizard { it.copy(enforcedEndDate = datePickerState.selectedDateMillis) }
                                        showDatePicker = false
                                    }) { Text("Confirm", color = AccentCyan) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                                },
                                colors = DatePickerDefaults.colors(containerColor = CardBg)
                            ) {
                                DatePicker(state = datePickerState)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Step6Review(state: WizardState, viewModel: LocksViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
        Text("Give your plan a name and verify details.", fontSize = 14.sp, color = TextSecond)

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.planName,
            onValueChange = { name -> viewModel.updateWizard { it.copy(planName = name) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Plan Name", color = AccentCyan) },
            placeholder = { Text("e.g., Deep Work", color = TextSecond) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = TextSecond.copy(alpha = 0.2f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(Modifier.height(32.dp))

        // Premium Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AccentCyan),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text("Plan Overview", fontWeight = FontWeight.Black, color = Color.White, fontSize = 18.sp)
                }
                
                Spacer(Modifier.height(20.dp))
                
                SummaryRow("Strategy", state.planType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
                SummaryRow("Apps", "${state.selectedPackages.size} Selected")
                
                val timingValue = when(state.timingMode) {
                    "WEEKLY" -> "${state.selectedDays.size} days/week"
                    "MULTI_DAY" -> "Next ${state.multiDayCount} days"
                    else -> state.timingMode.lowercase().replaceFirstChar { it.uppercase() }
                }
                SummaryRow("Timing", timingValue)
                
                SummaryRow("Protection", state.stopChallenge.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })

                if (state.stopChallenge == "ENFORCED" && state.enforcedEndDate != null) {
                    val dateStr = remember(state.enforcedEndDate) {
                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(state.enforcedEndDate))
                    }
                    SummaryRow("Enforced Until", dateStr)
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun GlassTabs(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .padding(5.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val pillWidth = maxWidth / tabs.size
            val pillOffset by animateDpAsState(
                targetValue = pillWidth * selectedTab,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                label = "pill"
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(pillOffset.roundToPx(), 0) }
                    .width(pillWidth)
                    .fillMaxHeight()
                    .padding(1.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF6096FF).copy(alpha = 0.55f),
                                Color(0xFF6096FF).copy(alpha = 0.28f)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF96BEFF).copy(alpha = 0.55f), RoundedCornerShape(13.dp))
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color(0xFFeaf1ff) else TextSecond,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LocksHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
    ) {
        Column {
            Text("🔒 Locks", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Manage app and website restrictions.", fontSize = 13.sp, color = TextSecond)
        }
    }
}


@Composable
private fun WebsiteLockItem(domain: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = domain,
            color = TextPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Remove",
                tint = AccentRed.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun WebsiteHistoryItem(
    domain: String,
    isBlocked: Boolean,
    onLock: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Language,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = domain,
            color = TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        if (isBlocked) {
            Surface(
                color = AccentCyan.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Locked",
                    color = AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        } else {
            Button(
                onClick = onLock,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(4.dp))
                Text("+ Lock", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AddWebsiteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Text(
                text = "Add Blocked Website / Keyword",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter domain, wildcard (e.g. *.reddit.com), or URL path (e.g. /shorts):",
                    color = TextSecond,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("e.g. youtube.com/shorts or twitter.com", color = TextSecond.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = TextSecond.copy(alpha = 0.2f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Text("Add", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecond)
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
fun LocksScreenPreview() {
    com.curbme.app.ui.theme.CurbMeTheme {
        LocksScreen(prefs = PrefsManager(LocalContext.current))
    }
}
