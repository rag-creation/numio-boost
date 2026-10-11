package com.rr.numio.boost.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.rr.numio.boost.BoostTileService
import com.rr.numio.boost.MainActivity
import com.rr.numio.boost.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Foreground service that keeps the boost alive while the app is closed. */
class BoostService : Service() {

    private val engine = BoostEngine { _status.value = it }
    private lateinit var prefs: BoostPrefs

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        engine.setGain(prefs.gainMb())
        updateNotification()
    }

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> engine.attachSession(id)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> engine.detachSession(id)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = BoostPrefs(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBoost()
            return START_NOT_STICKY
        }
        // Every startForegroundService() call must be answered with startForeground(),
        // even when the boost is already running.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            )
        } catch (e: Exception) {
            // Android refused (e.g. a background restart); stay off rather than crash.
            stopSelf()
            return START_NOT_STICKY
        }
        if (_running.value) return START_STICKY

        engine.start(prefs.gainMb())
        prefs.prefs.registerOnSharedPreferenceChangeListener(prefListener)
        ContextCompat.registerReceiver(
            this,
            sessionReceiver,
            IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            },
            ContextCompat.RECEIVER_EXPORTED
        )
        _running.value = true
        refreshTile()
        return START_STICKY
    }

    private fun stopBoost() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (_running.value) {
            prefs.prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            try {
                unregisterReceiver(sessionReceiver)
            } catch (_: IllegalArgumentException) {
            }
        }
        engine.release()
        _status.value = BoostStatus()
        _running.value = false
        refreshTile()
        super.onDestroy()
    }

    private fun refreshTile() {
        TileService.requestListeningState(this, ComponentName(this, BoostTileService::class.java))
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BoostService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val db = prefs.gainMb() / 100f
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_boost)
            .setContentTitle("Boost is on")
            .setContentText("Boost ${prefs.boostPercent}%  (+${"%.1f".format(db)} dB)")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification() {
        if (!_running.value) return
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification())
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val CHANNEL_ID = "boost"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.rr.numio.boost.STOP"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        private val _status = MutableStateFlow(BoostStatus())
        val status: StateFlow<BoostStatus> = _status

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BoostService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BoostService::class.java).setAction(ACTION_STOP))
        }
    }
}
