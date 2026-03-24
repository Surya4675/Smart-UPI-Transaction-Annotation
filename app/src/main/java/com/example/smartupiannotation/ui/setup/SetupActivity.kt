package com.example.smartupiannotation.ui.setup

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.smartupiannotation.databinding.AcitivitySetupBinding
import com.example.smartupiannotation.utils.PermissionUtils

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: AcitivitySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AcitivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupClickListeners() {
        binding.btnNotificationPermission.setOnClickListener {
            PermissionUtils.openNotificationListenerSettings(this)
        }

        binding.btnOverlayPermission.setOnClickListener {
            PermissionUtils.openOverlaySettings(this)
        }

        binding.btnUsagePermission.setOnClickListener {
            PermissionUtils.openUsageStatsSettings(this)
        }

        binding.btnContinue.setOnClickListener {
            finish() // Return to MainActivity
        }
    }

    private fun updatePermissionStatus() {
        val hasNotification = PermissionUtils.isNotificationListenerEnabled(this)
        val hasOverlay = PermissionUtils.hasOverlayPermission(this)
        val hasUsage = PermissionUtils.hasUsageStatsPermission(this)

        binding.btnNotificationPermission.isEnabled = !hasNotification
        binding.btnOverlayPermission.isEnabled = !hasOverlay
        binding.btnUsagePermission.isEnabled = !hasUsage

        binding.btnContinue.isEnabled = hasNotification && hasOverlay && hasUsage
    }
}
