package com.rr.numio.boost.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rr.numio.boost.engine.BoostPrefs
import com.rr.numio.boost.engine.BoostService
import kotlin.math.roundToInt

private const val SOURCE_URL = "https://github.com/rag-creation/numio-boost"

@Composable
fun BoostScreen() {
    val context = LocalContext.current
    val prefs = remember { BoostPrefs(context) }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    val running by BoostService.running.collectAsState()
    val globalOk by BoostService.globalAvailable.collectAsState()

    var volume by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var boost by remember { mutableFloatStateOf(prefs.boostPercent.toFloat()) }
    var high by remember { mutableStateOf(prefs.highBoost) }
    var askHigh by remember { mutableStateOf(false) }

    // Keep the volume slider in step with the phone's volume buttons.
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { BoostService.start(context) }

    fun toggle() {
        if (running) {
            BoostService.stop(context)
            return
        }
        val needsAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else BoostService.start(context)
    }

    val maxMb = if (high) BoostPrefs.HIGH_MAX_MB else BoostPrefs.NORMAL_MAX_MB
    val db = boost.roundToInt() * maxMb / 100 / 100f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NumioBg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Boost", color = NumioText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("by Numio", color = NumioYellow, fontSize = 14.sp, modifier = Modifier.padding(bottom = 5.dp))
        }

        Spacer(Modifier.height(36.dp))
        PowerButton(on = running, label = if (running) "+${"%.1f".format(db)} dB" else "OFF") { toggle() }
        Spacer(Modifier.height(12.dp))
        Text(
            if (running) "Tap to stop" else "Tap to start",
            color = NumioMuted, fontSize = 13.sp
        )

        if (running && !globalOk) {
            Spacer(Modifier.height(12.dp))
            Text(
                "This phone doesn't allow boosting all sound at once. Boost will still work in music " +
                    "and video players that support audio effects.",
                color = NumioYellow, fontSize = 13.sp, textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(32.dp))
        Card {
            SliderRow(
                title = "Volume",
                value = "${if (maxVol > 0) volume * 100 / maxVol else 0}%",
                slider = {
                    Slider(
                        value = volume.toFloat(),
                        onValueChange = {
                            volume = it.roundToInt()
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                        },
                        valueRange = 0f..maxVol.toFloat(),
                        steps = (maxVol - 1).coerceAtLeast(0),
                        colors = sliderColors(NumioText)
                    )
                }
            )
            Spacer(Modifier.height(8.dp))
            SliderRow(
                title = "Boost",
                value = "${boost.roundToInt()}%",
                slider = {
                    Slider(
                        value = boost,
                        onValueChange = { boost = it },
                        onValueChangeFinished = { prefs.boostPercent = boost.roundToInt() },
                        valueRange = 0f..100f,
                        colors = sliderColors(if (high && boost > 50f) NumioRed else NumioYellow)
                    )
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Higher boost", color = NumioText, fontSize = 16.sp)
                    Text(
                        if (high) "Up to +24 dB" else "Up to +12 dB",
                        color = NumioMuted, fontSize = 13.sp
                    )
                }
                Switch(
                    checked = high,
                    onCheckedChange = {
                        if (it) askHigh = true else {
                            high = false
                            prefs.highBoost = false
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NumioYellow
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Loud sound can damage speakers and hearing. Turn the boost down if you hear crackling, " +
                "and keep it low with headphones.",
            color = NumioMuted, fontSize = 13.sp, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))
        Text("No ads. No internet. Open source.", color = NumioMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Source code",
            color = NumioYellow,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                    } catch (_: Exception) {
                    }
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text("Made by a Keralite 💛", color = NumioMuted, fontSize = 12.sp)
    }

    if (askHigh) {
        AlertDialog(
            onDismissRequest = { askHigh = false },
            containerColor = NumioCard,
            title = { Text("Turn on higher boost?", color = NumioText) },
            text = {
                Text(
                    "This doubles the maximum boost. Small phone speakers can distort or get " +
                        "damaged, and it can hurt your ears with headphones. Use it for quiet " +
                        "videos and calls, not loud music.",
                    color = NumioMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    high = true
                    prefs.highBoost = true
                    askHigh = false
                }) { Text("Turn on", color = NumioRed) }
            },
            dismissButton = {
                TextButton(onClick = { askHigh = false }) { Text("Cancel", color = NumioText) }
            }
        )
    }
}

@Composable
private fun PowerButton(on: Boolean, label: String, onClick: () -> Unit) {
    val ring by animateColorAsState(if (on) NumioYellow else NumioKey, label = "ring")
    val glow by animateDpAsState(if (on) 28.dp else 0.dp, label = "glow")
    Box(
        modifier = Modifier
            .size(190.dp)
            .shadow(glow, CircleShape, ambientColor = NumioYellow, spotColor = NumioYellow)
            .clip(CircleShape)
            .background(NumioCard)
            .border(6.dp, ring, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (on) "ON" else "",
                color = NumioYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
            Text(
                label,
                color = if (on) NumioText else NumioMuted,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NumioCard)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) { content() }
}

@Composable
private fun SliderRow(title: String, value: String, slider: @Composable () -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = NumioText, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(value, color = NumioMuted, fontSize = 15.sp)
        }
        slider()
    }
}

@Composable
private fun sliderColors(active: Color) = SliderDefaults.colors(
    thumbColor = active,
    activeTrackColor = active,
    inactiveTrackColor = NumioKey,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent
)
