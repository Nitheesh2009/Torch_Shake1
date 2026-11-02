package com.example.torchshake

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var btnTorch: MaterialButton
    private lateinit var groupMode: MaterialButtonToggleGroup
    private lateinit var groupSpeed: MaterialButtonToggleGroup
    private lateinit var speedLabel: TextView
    private lateinit var switchShake: MaterialSwitch
    private lateinit var seekSensitivity: SeekBar
    private lateinit var sensitivityLabel: TextView

    private val torchListener: (Boolean) -> Unit = { updateTorchButton() }

    private val shakeSwitchListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        if (checked) enableShake() else disableShake()
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The service works either way; the permission only controls
            // whether the notification is visible.
            startShakeService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        TorchManager.init(this)

        bindViews()
        applyWindowInsets()
        setupTorchButton()
        setupModeAndSpeed()
        setupShakeControls()
    }

    override fun onStart() {
        super.onStart()
        TorchManager.addListener(torchListener)
        updateTorchButton()
    }

    override fun onResume() {
        super.onResume()
        syncShakeSwitch()
    }

    override fun onStop() {
        TorchManager.removeListener(torchListener)
        super.onStop()
    }

    // ---------------------------------------------------------------- setup

    private fun bindViews() {
        btnTorch = findViewById(R.id.btnTorch)
        groupMode = findViewById(R.id.groupMode)
        groupSpeed = findViewById(R.id.groupSpeed)
        speedLabel = findViewById(R.id.speedLabel)
        switchShake = findViewById(R.id.switchShake)
        seekSensitivity = findViewById(R.id.seekSensitivity)
        sensitivityLabel = findViewById(R.id.sensitivityLabel)
    }

    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.root)
        val l = root.paddingLeft
        val t = root.paddingTop
        val r = root.paddingRight
        val b = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
            insets
        }
    }

    private fun setupTorchButton() {
        btnTorch.setOnClickListener {
            if (!TorchManager.hasFlash) {
                Toast.makeText(this, R.string.no_flash, Toast.LENGTH_SHORT).show()
            } else {
                TorchManager.toggle()
            }
        }
    }

    private fun setupModeAndSpeed() {
        // Set the saved selection first, then attach listeners.
        groupMode.check(if (TorchManager.mode == TorchMode.BLINK) R.id.btnBlink else R.id.btnNormal)
        groupSpeed.check(
            when (TorchManager.speed) {
                BlinkSpeed.FAST -> R.id.btnFast
                BlinkSpeed.MEDIUM -> R.id.btnMedium
                BlinkSpeed.SLOW -> R.id.btnSlow
            }
        )
        updateSpeedVisibility()

        groupMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            TorchManager.mode = if (checkedId == R.id.btnBlink) TorchMode.BLINK else TorchMode.NORMAL
            updateSpeedVisibility()
        }

        groupSpeed.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            TorchManager.speed = when (checkedId) {
                R.id.btnFast -> BlinkSpeed.FAST
                R.id.btnSlow -> BlinkSpeed.SLOW
                else -> BlinkSpeed.MEDIUM
            }
        }
    }

    private fun setupShakeControls() {
        seekSensitivity.progress = TorchManager.shakeSensitivity
        updateSensitivityLabel()
        seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                TorchManager.shakeSensitivity = progress
                updateSensitivityLabel()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        findViewById<View>(R.id.btnBattery).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    // ------------------------------------------------------------- shake service

    private fun enableShake() {
        if (!TorchManager.hasFlash) {
            Toast.makeText(this, R.string.no_flash, Toast.LENGTH_SHORT).show()
            syncShakeSwitch()
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startShakeService()
        }
    }

    private fun startShakeService() {
        ContextCompat.startForegroundService(this, Intent(this, TorchService::class.java))
        // The service flips `running` a moment later; show the switch as on now.
        switchShake.setOnCheckedChangeListener(null)
        switchShake.isChecked = true
        switchShake.setOnCheckedChangeListener(shakeSwitchListener)
    }

    private fun disableShake() {
        stopService(Intent(this, TorchService::class.java))
    }

    private fun syncShakeSwitch() {
        switchShake.setOnCheckedChangeListener(null)
        switchShake.isChecked = TorchService.running
        switchShake.setOnCheckedChangeListener(shakeSwitchListener)
    }

    // ------------------------------------------------------------------ UI

    private fun updateTorchButton() {
        btnTorch.setText(if (TorchManager.isActive) R.string.torch_off else R.string.torch_on)
    }

    private fun updateSpeedVisibility() {
        val visibility = if (TorchManager.mode == TorchMode.BLINK) View.VISIBLE else View.GONE
        speedLabel.visibility = visibility
        groupSpeed.visibility = visibility
    }

    private fun updateSensitivityLabel() {
        sensitivityLabel.text = getString(R.string.sensitivity_label, TorchManager.shakeSensitivity)
    }
}
