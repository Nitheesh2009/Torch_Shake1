package com.example.torchshake

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

enum class TorchMode { NORMAL, BLINK }

enum class BlinkSpeed(val intervalMs: Long) {
    FAST(150),
    MEDIUM(500),
    SLOW(1000)
}

/**
 * One place that owns the torch. Both the screen (MainActivity) and the
 * background service (TorchService) talk to this object, so they never fight
 * over the flash. Everything runs on the main thread.
 */
object TorchManager {

    private const val PREFS = "torch_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_SPEED = "speed"
    private const val KEY_SENSITIVITY = "sensitivity"

    /** Ignore torch callbacks this soon after we changed the torch ourselves. */
    private const val CALLBACK_GUARD_MS = 600L

    private lateinit var prefs: SharedPreferences
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var initialized = false

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(Boolean) -> Unit>()

    private var blinkOn = false
    private var lastSetTime = 0L

    /** True while the user wants the torch running (steady or blinking). */
    var isActive = false
        private set

    val hasFlash: Boolean get() = cameraId != null

    var mode: TorchMode = TorchMode.NORMAL
        set(value) {
            field = value
            if (initialized) prefs.edit().putString(KEY_MODE, value.name).apply()
            if (isActive) applyState()
        }

    var speed: BlinkSpeed = BlinkSpeed.MEDIUM
        set(value) {
            field = value
            if (initialized) prefs.edit().putString(KEY_SPEED, value.name).apply()
            if (isActive && mode == TorchMode.BLINK) applyState()
        }

    /** 0 (hard shake needed) .. 100 (light shake is enough). */
    var shakeSensitivity: Int = 60
        set(value) {
            field = value.coerceIn(0, 100)
            if (initialized) prefs.edit().putInt(KEY_SENSITIVITY, field).apply()
        }

    /** Acceleration (in g) a spike must reach to count as one "chop". */
    val shakeThresholdG: Float
        get() = 3.5f - shakeSensitivity / 100f * 1.7f

    private val blinkRunnable = object : Runnable {
        override fun run() {
            if (!isActive || mode != TorchMode.BLINK) return
            blinkOn = !blinkOn
            setTorch(blinkOn)
            handler.postDelayed(this, speed.intervalMs)
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id != cameraId) return
            if (SystemClock.elapsedRealtime() - lastSetTime < CALLBACK_GUARD_MS) return
            // Someone else (e.g. Quick Settings) turned the steady torch off.
            if (isActive && mode == TorchMode.NORMAL && !enabled) {
                isActive = false
                notifyListeners()
            }
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id != cameraId) return
            if (SystemClock.elapsedRealtime() - lastSetTime < CALLBACK_GUARD_MS) return
            // Another app grabbed the camera.
            if (isActive) {
                isActive = false
                handler.removeCallbacks(blinkRunnable)
                notifyListeners()
            }
        }
    }

    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cameraManager = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        cameraId = try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val c = cameraManager.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: CameraAccessException) {
            null
        }

        // Load saved settings before flipping `initialized`, so the setters
        // above don't write them straight back.
        mode = runCatching {
            TorchMode.valueOf(prefs.getString(KEY_MODE, null) ?: "")
        }.getOrDefault(TorchMode.NORMAL)
        speed = runCatching {
            BlinkSpeed.valueOf(prefs.getString(KEY_SPEED, null) ?: "")
        }.getOrDefault(BlinkSpeed.MEDIUM)
        shakeSensitivity = prefs.getInt(KEY_SENSITIVITY, 60)

        initialized = true
        cameraManager.registerTorchCallback(torchCallback, handler)
    }

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }

    fun toggle() {
        if (isActive) stop() else start()
    }

    fun start() {
        if (!hasFlash || isActive) return
        isActive = true
        applyState()
        notifyListeners()
    }

    fun stop() {
        if (!isActive) return
        isActive = false
        applyState()
        notifyListeners()
    }

    /** Make the real torch match isActive / mode / speed. */
    private fun applyState() {
        handler.removeCallbacks(blinkRunnable)
        when {
            !isActive -> setTorch(false)
            mode == TorchMode.NORMAL -> setTorch(true)
            else -> {
                blinkOn = false
                handler.post(blinkRunnable)
            }
        }
    }

    private fun setTorch(on: Boolean) {
        val id = cameraId ?: return
        try {
            lastSetTime = SystemClock.elapsedRealtime()
            cameraManager.setTorchMode(id, on)
        } catch (e: CameraAccessException) {
            fail()
        } catch (e: IllegalArgumentException) {
            fail()
        }
    }

    private fun fail() {
        handler.removeCallbacks(blinkRunnable)
        if (isActive) {
            isActive = false
            notifyListeners()
        }
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it(isActive) }
    }
}
