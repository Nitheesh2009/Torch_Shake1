package com.example.torchshake

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Detects a Motorola-style "chop": two sharp acceleration spikes in quick
 * succession. A single bump or a slow wave won't trigger it.
 */
class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {

    private companion object {
        const val REQUIRED_PEAKS = 2
        const val WINDOW_MS = 700L      // both spikes must happen within this time
        const val MIN_PEAK_GAP_MS = 150L // ignore the same spike seen twice
        const val COOLDOWN_MS = 1000L   // pause after a successful shake
    }

    private var peakCount = 0
    private var firstPeakTime = 0L
    private var lastPeakTime = 0L
    private var lastShakeTime = 0L

    override fun onSensorChanged(event: SensorEvent) {
        val gx = event.values[0] / SensorManager.GRAVITY_EARTH
        val gy = event.values[1] / SensorManager.GRAVITY_EARTH
        val gz = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gx * gx + gy * gy + gz * gz)

        if (gForce < TorchManager.shakeThresholdG) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastShakeTime < COOLDOWN_MS) return
        if (now - lastPeakTime < MIN_PEAK_GAP_MS) return
        lastPeakTime = now

        if (peakCount == 0 || now - firstPeakTime > WINDOW_MS) {
            firstPeakTime = now
            peakCount = 1
        } else {
            peakCount++
        }

        if (peakCount >= REQUIRED_PEAKS) {
            peakCount = 0
            lastShakeTime = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
