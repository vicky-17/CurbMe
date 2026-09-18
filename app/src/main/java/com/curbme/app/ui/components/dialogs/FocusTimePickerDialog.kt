package com.curbme.app.ui.components.dialogs

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FocusTimePickerDialog(
    onDismissRequest: () -> Unit,
    onDurationSelected: (hours: Int, minutes: Int) -> Unit,
    initialHours: Int = 0,
    initialMinutes: Int = 15
) {
    var selectedHours by remember { mutableIntStateOf(initialHours) }
    var selectedMinutes by remember { mutableIntStateOf(initialMinutes) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
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
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
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
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                // Highlighting background strip for selected items
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours Column
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularDurationWheel(
                            range = 0..23,
                            initialValue = initialHours,
                            label = "hrs",
                            onValueChange = { selectedHours = it }
                        )
                    }

                    // Separation Colon
                    Text(
                        text = ":",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Minutes Column
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularDurationWheel(
                            range = 0..59,
                            initialValue = initialMinutes,
                            label = "mins",
                            onValueChange = { selectedMinutes = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDurationSelected(selectedHours, selectedMinutes)
                    onDismissRequest()
                }
            ) {
                Text("Start Focus", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", color = MaterialTheme.colorScheme.outline)
            }
        }
    )
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
    val itemHeight = 56.dp
    val visibleItems = 5
    val pickerHeight = itemHeight * visibleItems
    val halfPadding = itemHeight * (visibleItems / 2)
    
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = items.indexOf(initialValue))
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Derived state to find the exact center item for precise scaling
    val currentSnappedIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val center = layoutInfo.viewportSize.height / 2
            var closestIndex = 0
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
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            view.playSoundEffect(SoundEffectConstants.CLICK)
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
                .width(72.dp)
                .height(pickerHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(items.size) { index ->
                val isSelected = currentSnappedIndex == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            val layoutInfo = listState.layoutInfo
                            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                            if (itemInfo != null) {
                                val centerY = layoutInfo.viewportSize.height / 2f
                                val childCenterY = itemInfo.offset + (itemInfo.size / 2f)
                                val factor = (centerY - childCenterY) / centerY
                                
                                val alphaFactor = 1f - 0.7f * abs(factor)
                                alpha = (alphaFactor * alphaFactor * alphaFactor).coerceIn(0f, 1f)
                                
                                val scaleFactor = 1f - 0.3f * abs(factor)
                                scaleX = scaleFactor
                                scaleY = scaleFactor
                                
                                val rotateRadius = (2f * centerY / Math.PI).toFloat()
                                val rad = (centerY - childCenterY) / rotateRadius
                                rotationX = (rad * 180f / Math.PI).toFloat()
                                // Removed translationY mapping to ensure standard alignment is preserved
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index].toString().padStart(2, '0'),
                        fontSize = if (isSelected) 40.sp else 32.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}
