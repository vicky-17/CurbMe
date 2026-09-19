package com.curbme.app.ui.components.dialogs

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.recyclerview.widget.RecyclerView
import com.curbme.app.ui.components.picker.WheelPickerRecyclerView
import com.curbme.app.ui.theme.CurbMeTheme

private val AccentStart = Color(0xFF7C4DFF)
private val AccentEnd = Color(0xFF00B0FF)

@Composable
fun FocusTimePickerDialog(
    onDismissRequest: () -> Unit,
    onDurationSelected: (hours: Int, minutes: Int) -> Unit,
    initialHours: Int = 0,
    initialMinutes: Int = 15,
    minimumDuration: Long = 60L,
    maximumDuration: Long = 24L * 60 * 60
) {
    Dialog(onDismissRequest = onDismissRequest) {
        FocusTimePickerDialogContent(
            onDismissRequest = onDismissRequest,
            onDurationSelected = onDurationSelected,
            initialHours = initialHours,
            initialMinutes = initialMinutes,
            minimumDuration = minimumDuration,
            maximumDuration = maximumDuration
        )
    }
}

@Composable
fun FocusTimePickerDialogContent(
    onDismissRequest: () -> Unit,
    onDurationSelected: (hours: Int, minutes: Int) -> Unit,
    initialHours: Int = 0,
    initialMinutes: Int = 15,
    minimumDuration: Long = 60L,
    maximumDuration: Long = 24L * 60 * 60
) {
    var hours by remember { mutableIntStateOf(initialHours.coerceIn(0, 23)) }
    var minutes by remember { mutableIntStateOf(initialMinutes.coerceIn(0, 59)) }

    val hourItems = remember { (0..23).map { it.toString().padStart(2, '0') } }
    val minuteItems = remember { (0..59).map { it.toString().padStart(2, '0') } }

    val totalSeconds = (hours * 3600L + minutes * 60L).coerceIn(minimumDuration, maximumDuration)
    val displayHours = (totalSeconds / 3600).toInt()
    val displayMinutes = ((totalSeconds % 3600) / 60).toInt()
    val progress = (totalSeconds.toFloat() / maximumDuration).coerceIn(0f, 1f)

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier.widthIn(max = 360.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // ---------- Hero header with graphic ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(AccentStart.copy(alpha = 0.95f), AccentEnd.copy(alpha = 0.9f))
                        )
                    )
                    .padding(top = 24.dp, bottom = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FocusRing(progress = progress, hours = displayHours, minutes = displayMinutes)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Focus Mode",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Screen will be locked until the timer ends",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ---------- Quick presets ----------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "15m" to (0 to 15),
                        "30m" to (0 to 30),
                        "1h" to (1 to 0),
                        "2h" to (2 to 0)
                    ).forEach { (label, hm) ->
                        val selected = displayHours == hm.first && displayMinutes == hm.second
                        PresetChip(
                            label = label,
                            selected = selected,
                            modifier = Modifier.weight(1f)
                        ) {
                            hours = hm.first
                            minutes = hm.second
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---------- Wheels ----------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Selection highlight
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        AccentStart.copy(alpha = 0.22f),
                                        AccentEnd.copy(alpha = 0.22f)
                                    )
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WheelColumn(
                            items = hourItems,
                            index = hours,
                            unit = "hrs",
                            onItemSelected = { hours = it },
                            modifier = Modifier.weight(1f)
                        )
                        WheelColumn(
                            items = minuteItems,
                            index = minutes,
                            unit = "mins",
                            onItemSelected = { minutes = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Fade top & bottom for depth
                    val surface = MaterialTheme.colorScheme.surface
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(Brush.verticalGradient(listOf(surface, Color.Transparent)))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, surface)))
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ---------- Actions ----------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(listOf(AccentStart, AccentEnd)))
                            .clickable {
                                onDurationSelected(displayHours, displayMinutes)
                                onDismissRequest()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Start Focus",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/** Animated circular graphic: glowing ring whose sweep reflects the chosen duration. */
@Composable
private fun FocusRing(progress: Float, hours: Int, minutes: Int) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceAtLeast(0.03f),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "ringProgress"
    )
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2 + 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)

            // soft pulsing glow
            drawCircle(color = Color.White.copy(alpha = pulse), radius = size.minDimension / 2)
            // track
            drawArc(
                color = Color.White.copy(alpha = 0.25f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            // progress
            drawArc(
                color = Color.White,
                startAngle = -90f, sweepAngle = 360f * animatedProgress, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%02d:%02d".format(hours, minutes),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "hr : min",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) AccentStart.copy(alpha = 0.16f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val fg = if (selected) AccentStart else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun WheelColumn(
    items: List<String>,
    index: Int,
    unit: String,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        WheelPickerView(
            items = items,
            index = index,
            onItemSelected = onItemSelected,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, end = 6.dp)
        )
    }
}

@Composable
private fun WheelPickerView(
    items: List<String>,
    index: Int,
    onItemSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 44.dp.roundToPx() }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    // Tracks what the wheel itself last reported, so presets can move it programmatically
    val lastReported = remember { intArrayOf(index) }
    val currentOnSelected by rememberUpdatedState(onItemSelected)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WheelPickerRecyclerView(context).apply {
                adapter = TextWheelAdapter(items, itemHeightPx, textColor)
                setWheelListener(object : WheelPickerRecyclerView.WheelPickerRecyclerViewListener {
                    override fun didSelectItem(position: Int) {
                        if (position in items.indices) {
                            lastReported[0] = position
                            currentOnSelected(position)
                        }
                    }
                })
                scrollToPosition(index)
            }
        },
        update = { view ->
            if (index != lastReported[0]) {
                lastReported[0] = index
                view.scrollToPosition(index)
            }
        }
    )
}

private class TextWheelAdapter(
    private val items: List<String>,
    private val itemHeightPx: Int,
    private val textColor: Int
) : RecyclerView.Adapter<TextWheelAdapter.ViewHolder>() {

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeightPx)
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(textColor)
            setTypeface(typeface, Typeface.BOLD)
        }
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = items[position]
    }

    override fun getItemCount(): Int = items.size
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