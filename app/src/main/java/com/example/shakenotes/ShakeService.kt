package com.example.shakenotes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Runs in the background, listens to the accelerometer and opens the app on shake. */
class ShakeService : Service() {
    private lateinit var sensorManager: SensorManager
    private val detector = ShakeDetector { openApp() }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Shake listener", NotificationManager.IMPORTANCE_MIN)
        )
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Shake to open is on")
            .setContentText("Shake your phone to open Expenses & Notes")
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, 1, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(detector, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        sensorManager.unregisterListener(detector)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "shake"
        fun start(ctx: Context) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, ShakeService::class.java))
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, ShakeService::class.java)) }
    }
}
