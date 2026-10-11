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
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rr.numio.boost.R
import com.rr.numio.boost.engine.BoostPrefs
import com.rr.numio.boost.engine.BoostService
import kotlin.math.roundToInt

private const val SOURCE_URL = "https://github.com/rag-creation/numio-boost"
private val Warm = Color(0xFFE5734B)

@Composable
fun BoostScreen() {
    val context = LocalContext.current
    val prefs = remember { BoostPrefs(context) }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    val running by BoostService.running.collectAsState()
    val status by BoostService.status.collectAsState()
    val power = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var keepsRunning by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    val batteryPrompt = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { keepsRunning = power.isIgnoringBatteryOptimizations(context.packageName) }

    var volume by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var boost by remember { mutableIntStateOf(prefs.boostPercent) }
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
    val db = boost * maxMb / 100 / 100f
    val accent = NumioYellow

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NumioBg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.boost_logo),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Boost", color = NumioText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("by Numio", color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // Status pill
        StatusPill(running = running, globalActive = status.globalActive,
            globalBlocked = status.globalBlocked, apps = status.appSessions, accent = accent)

        BoostDial(
            percent = boost,
            on = running,
            dbText = "+${"%.1f".format(db)}",
            accent = accent,
            onPercentChange = {
                boost = it
                prefs.boostPercent = it
            },
            onToggle = { toggle() }
        )

        Text(
            if (running) "Drag the ring to boost · tap the centre to stop"
            else "Drag the ring to set the boost · tap the centre to start",
            color = NumioMuted, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 0.dp)
        )

        // Phone volume
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Phone volume", color = NumioText, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(
                    "${if (maxVol > 0) volume * 100 / maxVol else 0}%",
                    color = NumioMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                )
            }
            Slider(
                value = volume.toFloat(),
                onValueChange = {
                    volume = it.roundToInt()
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                },
                valueRange = 0f..maxVol.toFloat(),
                steps = (maxVol - 1).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = NumioText,
                    activeTrackColor = NumioText,
                    inactiveTrackColor = NumioKey,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
        }

        // Normal / Higher
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ModeButton("Normal", "up to +12 dB", selected = !high, sub = accent) {
                high = false
                prefs.highBoost = false
            }
            ModeButton("Higher", "up to +24 dB", selected = high, sub = Warm) {
                if (!high) askHigh = true
            }
        }

        if (!keepsRunning) {
            Card(onClick = {
                try {
                    batteryPrompt.launch(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                } catch (_: Exception) {
                    batteryPrompt.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep Boost running", color = NumioText, fontSize = 16.sp)
                        Text(
                            "Some phones stop Boost in the background to save battery. Tap to allow it.",
                            color = NumioMuted, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Allow", color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            "Loud sound can damage speakers and hearing. Turn the boost down if you hear crackling, " +
                "and keep it low with headphones.",
            color = NumioMuted, fontSize = 12.sp, textAlign = TextAlign.Center
        )

        // Footer
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "noAds && noInternet && openSource",
                color = Color(0xFF7A7A7A), fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )
            Text(
                "Source code",
                color = accent,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                        } catch (_: Exception) {
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Text("Made by a Keralite 💛", color = NumioMuted, fontSize = 12.sp)
        }
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
private fun StatusPill(running: Boolean, globalActive: Boolean, globalBlocked: Boolean, apps: Int, accent: Color) {
    val (text, warn) = when {
        !running -> "Boost is off" to false
        globalActive -> "Boosting all apps" to false
        globalBlocked ->
            "Another app controls sound effects · boosting $apps player${if (apps == 1) "" else "s"}" to true
        else -> "Phone blocks global boost · boosting $apps player${if (apps == 1) "" else "s"}" to true
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (running) Color(0xFF17150D) else NumioCard)
            .border(1.dp, if (running) Color(0xFF3A3214) else Color(0xFF262626), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (!running) Color(0xFF3A3A3A) else if (warn) Warm else accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = if (running) Color(0xFFE8D9A8) else NumioMuted,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RowScope.ModeButton(title: String, subtitle: String, selected: Boolean, sub: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0xFF1C1808) else NumioCard)
            .border(1.dp, if (selected) NumioYellow else Color(0xFF262626), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Text(title, color = if (selected) NumioText else Color(0xFFBDBDBD), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, color = sub, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Card(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(NumioCard)
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(22.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) { content() }
}
