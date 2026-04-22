// utils/RestTimerService.kt
package com.kinetic.fitness.utils

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.kinetic.fitness.R
import com.kinetic.fitness.ui.MainActivity

class RestTimerService : Service() {

    companion object {
        const val CHANNEL_ID = "rest_timer_channel"
        const val NOTIF_ID = 101
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val EXTRA_SECONDS = "seconds"
        const val BROADCAST_TICK = "com.kinetic.TIMER_TICK"
        const val BROADCAST_DONE = "com.kinetic.TIMER_DONE"
        const val EXTRA_REMAINING = "remaining"
    }

    private var totalSeconds = 120
    private var remaining = 120
    private var handler = Handler(Looper.getMainLooper())
    private var running = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            remaining--
            updateNotification()
            sendBroadcast(Intent(BROADCAST_TICK).putExtra(EXTRA_REMAINING, remaining))
            if (remaining <= 0) {
                sendBroadcast(Intent(BROADCAST_DONE))
                vibrate()
                running = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                totalSeconds = intent.getIntExtra(EXTRA_SECONDS, 120)
                remaining = totalSeconds
                running = true

                handler.removeCallbacks(ticker)
                startForeground(NOTIF_ID, buildNotification(remaining))
                handler.post(ticker)
            }
            ACTION_STOP -> {
                running = false
                handler.removeCallbacks(ticker)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(remaining))
    }

    private fun buildNotification(secs: Int): Notification {
        val m = secs / 60
        val s = secs % 60
        val timeStr = String.format("%02d:%02d", m, s)

        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RestTimerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("⏱ Nghỉ giải lao – $timeStr")
            .setContentText("Còn lại $timeStr — nhấn để mở ứng dụng")
            .setProgress(totalSeconds, totalSeconds - remaining, false)
            .setContentIntent(tapIntent)
            .addAction(R.drawable.ic_stop, "Dừng", stopIntent)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bộ hẹn giờ nghỉ",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Hiển thị thời gian nghỉ giải lao" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VibratorManager::class.java)
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 300, 200, 300), -1)
        }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
