package com.curbme.app.ui.components.dialogs

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.curbme.app.ui.theme.CurbMeTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// Dimensions matching WheelPicker specifications (dimens.xml & picker_action_sheet_content.xml)
private val WHEEL_ITEM_HEIGHT: Dp = 36.dp
private val WHEEL_PICKER_HEIGHT: Dp = 216.dp // 6 visible item slots height
private val WHEEL_HIGHLIGHT_RADIUS: Dp = 8.dp
private val ACTION_SHEET_BG_RADIUS: Dp = 16.dp

@Composable
fun FocusTimePickerDialog(
    onDismissRequest: () -> Unit,
    onDurationSelected: (hours: Int, minutes: Int) -> Unit,
    initialHours: Int = 0,
    initialMinutes: Int = 15
) {
    Dialog(onDismissRequest = onDismissRequest) {
        FocusTimePickerDialogContent(
            onDismissRequest = onDismissRequest,
            onDurationSelected = onDurationSelected,
            initialHours = initialHours,
            initialMinutes = initialMinutes
        )
    }
}

@Composable
fun FocusTimePickerDialogContent(
    onDismissRequest: () -> Unit,
    onDurationSelected: (hours: Int, minutes: Int) -> Unit,
    initialHours: Int = 0,
    initialMinutes: Int = 15
) {
    var selectedHours by remember { mutableIntStateOf(initialHours) }
    var selectedMinutes by remember { mutableIntStateOf(initialMinutes) }

    Surface(
        shape = RoundedCornerShape(ACTION_SHEET_BG_RADIUS),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.widthIn(max = 340.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Set Focus Duration",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))

            val hoursText = if (selectedHours > 0) "$selectedHours hrs" else ""
            val minsText = if (selectedMinutes > 0) "$selectedMinutes mins" else ""
            val combinedText = listOf(hoursText, minsText).filter { it.isNotEmpty() }.joinToString(" and ")

            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (combinedText.isNotEmpty()) "Screen will be blocked for $combinedText" else "Select a duration",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Outer Wheel Container with Center Highlight View (matching WheelPicker layout)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WHEEL_PICKER_HEIGHT),
                contentAlignment = Alignment.Center
            ) {
                // Highlight Strip (text_wheel_highlight_bg.xml)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ITEM_HEIGHT)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(WHEEL_HIGHLIGHT_RADIUS)
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours Picker Wheel
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularDurationWheel(
                            range = 0..23,
                            initialValue = initialHours,
                            label = "hrs",
                            onValueChange = { selectedHours = it }
                        )
                    }

                    Text(
                        text = ":",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // Minutes Picker Wheel
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularDurationWheel(
                            range = 0..59,
                            initialValue = initialMinutes,
                            label = "mins",
                            onValueChange = { selectedMinutes = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel", color = MaterialTheme.colorScheme.outline)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        onDurationSelected(selectedHours, selectedMinutes)
                        onDismissRequest()
                    }
                ) {
                    Text(
                        text = "Start Focus",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CircularDurationWheel(
    range: IntRange,
    initialValue: Int,
    label: String,
    onValueChange: (Int) -> Unit
) {
    val items = range.toList()
    val halfPadding = (WHEEL_PICKER_HEIGHT - WHEEL_ITEM_HEIGHT) / 2

    val initialIndex = items.indexOf(initialValue).coerceIn(0, items.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(initialValue) {
        val targetIndex = items.indexOf(initialValue)
        if (targetIndex >= 0 && listState.firstVisibleItemIndex != targetIndex) {
            listState.scrollToItem(targetIndex)
        }
    }

    val currentSnappedIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val center = layoutInfo.viewportSize.height / 2
            var closestIndex = listState.firstVisibleItemIndex.coerceIn(0, items.lastIndex)
            var closestDistance = Int.MAX_VALUE
            for (itemInfo in layoutInfo.visibleItemsInfo) {
                val itemCenter = itemInfo.offset + itemInfo.size / 2
                val distance = abs(itemCenter - center)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestIndex = itemInfo.index
                }
            }
            closestIndex
        }
    }

    val view = LocalView.current

    LaunchedEffect(listState) {
        snapshotFlow { currentSnappedIndex }.collect { index ->
            val safeIndex = index.coerceIn(0, items.lastIndex)
            onValueChange(items[safeIndex])
            if (!view.isInEditMode) {
                try {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                } catch (_: Exception) {}
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = halfPadding),
            modifier = Modifier
                .width(64.dp)
                .height(WHEEL_PICKER_HEIGHT),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(items.size) { index ->
                val isSelected = currentSnappedIndex == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ITEM_HEIGHT)
                        .graphicsLayer {
                            val layoutInfo = listState.layoutInfo
                            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                            if (itemInfo != null) {
                                // Mathematical formulas directly from WheelPickerRecyclerView.kt drawChild
                                val centerY = layoutInfo.viewportSize.height / 2f
                                val childCenterY = itemInfo.offset + (itemInfo.size / 2f)
                                val deltaY = centerY - childCenterY
                                val factor = (deltaY / centerY).coerceIn(-1f, 1f)
                                val absFactor = abs(factor)

                                val alphaFactor = (1f - 0.7f * absFactor).coerceIn(0f, 1f)
                                alpha = alphaFactor * alphaFactor * alphaFactor

                                val scaleFactor = (1f - 0.3f * absFactor).coerceIn(0f, 1f)
                                scaleX = scaleFactor
                                scaleY = scaleFactor

                                val rotateRadius = (2.0f * centerY / PI.toFloat())
                                val rad = deltaY / rotateRadius
                                val offsetY = deltaY - rotateRadius * sin(rad) * 1.3f
                                translationY = offsetY

                                rotationX = -rad * (180f / PI.toFloat())
                                cameraDistance = 12f * density
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index].toString().padStart(2, '0'),
                        fontSize = 22.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(name = "Focus Time Picker Dialog", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun FocusTimePickerDialogPreview() {
    CurbMeTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            FocusTimePickerDialogContent(
                onDismissRequest = {},
                onDurationSelected = { _, _ -> },
                initialHours = 0,
                initialMinutes = 15
            )
        }
    }
}
