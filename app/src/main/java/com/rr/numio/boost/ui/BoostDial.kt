package com.rr.numio.boost.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START_DEG = 135f
private const val SWEEP_DEG = 270f
private const val TICKS = 28

/**
 * Boost dial: a 270° ring you drag to set the boost (0..100), with an on/off button in the middle.
 */
@Composable
fun BoostDial(
    percent: Int,
    on: Boolean,
    dbText: String,
    accent: Color,
    onPercentChange: (Int) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val currentPercent by rememberUpdatedState(percent)
    val currentChange by rememberUpdatedState(onPercentChange)
    val currentToggle by rememberUpdatedState(onToggle)

    val ring by animateColorAsState(if (on) accent else NumioMuted, label = "ring")
    val glow by animateDpAsState(if (on) 30.dp else 0.dp, label = "glow")

    Box(
        modifier = modifier
            .size(300.dp)
            .semantics { contentDescription = "Boost $percent percent. Drag the ring to change it." },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Turn a touch point into a dial value; null if it's in the centre button.
                    fun valueAt(p: Offset): Int? {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val dx = p.x - c.x
                        val dy = p.y - c.y
                        if (hypot(dx, dy) < size.width * 0.30f) return null
                        var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        a = ((a - START_DEG) % 360f + 360f) % 360f
                        if (a > SWEEP_DEG) a = if (a > SWEEP_DEG + (360f - SWEEP_DEG) / 2f) 0f else SWEEP_DEG
                        return (a / SWEEP_DEG * 100f).roundToInt()
                    }
                    var dragging = false
                    detectDragGestures(
                        onDragStart = { p ->
                            val v = valueAt(p)
                            dragging = v != null
                            if (v != null) currentChange(v)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        if (!dragging) return@detectDragGestures
                        val v = valueAt(change.position) ?: return@detectDragGestures
                        // Don't let a drag jump straight across the gap from 100 to 0.
                        if (kotlin.math.abs(v - currentPercent) > 40) return@detectDragGestures
                        if (v / 5 != currentPercent / 5) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        currentChange(v)
                        change.consume()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        if (hypot(p.x - c.x, p.y - c.y) < size.width * 0.30f) currentToggle()
                    }
                }
        ) {
            val w = size.width
            val c = Offset(w / 2f, w / 2f)
            val trackR = w * 0.40f
            val stroke = w * 0.047f
            val frac = percent / 100f

            // Tick marks around the outside.
            for (i in 0 until TICKS) {
                val t = i / (TICKS - 1f)
                val ang = Math.toRadians((START_DEG + SWEEP_DEG * t).toDouble())
                val major = i % 3 == 0
                val r1 = w * (if (major) 0.447f else 0.457f)
                val r2 = w * (if (major) 0.480f else 0.477f)
                drawLine(
                    color = if (t <= frac && on) accent else Color(0xFF3A3A3A),
                    start = Offset(c.x + r1 * cos(ang).toFloat(), c.y + r1 * sin(ang).toFloat()),
                    end = Offset(c.x + r2 * cos(ang).toFloat(), c.y + r2 * sin(ang).toFloat()),
                    strokeWidth = if (major) 2.5.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val topLeft = Offset(c.x - trackR, c.y - trackR)
            val arcSize = Size(trackR * 2, trackR * 2)
            drawArc(
                color = Color(0xFF232323),
                startAngle = START_DEG, sweepAngle = SWEEP_DEG, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (frac > 0f) {
                drawArc(
                    color = ring,
                    startAngle = START_DEG, sweepAngle = SWEEP_DEG * frac, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            // Knob.
            val kAng = Math.toRadians((START_DEG + SWEEP_DEG * frac).toDouble())
            val k = Offset(c.x + trackR * cos(kAng).toFloat(), c.y + trackR * sin(kAng).toFloat())
            drawCircle(color = NumioBg, radius = stroke * 0.95f, center = k)
            drawCircle(color = ring, radius = stroke * 0.95f, center = k, style = Stroke(width = 4.dp.toPx()))
        }

        // Centre on/off button.
        Box(
            modifier = Modifier
                .size(180.dp)
                .shadow(glow, CircleShape, ambientColor = accent, spotColor = accent)
                .clip(CircleShape)
                .background(Color(0xFF151515))
                .border(1.dp, Color(0xFF2A2A2A), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (on) "ON" else "OFF",
                    color = if (on) accent else NumioMuted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
                Text(
                    if (on) dbText else "Tap",
                    color = if (on) NumioText else NumioMuted,
                    fontSize = if (on) 50.sp else 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.5).sp
                )
                Text(
                    if (on) "dB · $percent%" else "to start",
                    color = NumioMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }
    }
}
