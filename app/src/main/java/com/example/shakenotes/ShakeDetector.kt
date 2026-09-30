package com.example.shakenotes

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/** Fires onShake after 2 strong jolts within 1 second. Tune THRESHOLD_G if too sensitive. */
class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
    private var firstJolt = 0L
    private var lastJolt = 0L
    private var lastTrigger = 0L
    private var count = 0

    override fun onSensorChanged(e: SensorEvent) {
        val x = e.values[0] / SensorManager.GRAVITY_EARTH
        val y = e.values[1] / SensorManager.GRAVITY_EARTH
        val z = e.values[2] / SensorManager.GRAVITY_EARTH
        if (sqrt(x * x + y * y + z * z) < THRESHOLD_G) return

        val now = System.currentTimeMillis()
        if (now - lastJolt < 200 || now - lastTrigger < 2000) return
        if (now - firstJolt > 1000) { count = 0; firstJolt = now }
        lastJolt = now
        if (++count >= 2) { count = 0; lastTrigger = now; onShake() }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object { const val THRESHOLD_G = 2.7f }
}
