package com.example.smartupiannotation.ui.setup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.smartupiannotation.MainActivity
import com.example.smartupiannotation.databinding.AcitivitySetupBinding
import com.example.smartupiannotation.utils.PermissionUtils

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: AcitivitySetupBinding

    companion object {
        private const val CONTACTS_PERMISSION_CODE = 100
    }

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

        binding.btnContactsPermission.setOnClickListener {
            PermissionUtils.requestContactsPermission(this, CONTACTS_PERMISSION_CODE)
        }

        binding.btnContinue.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun updatePermissionStatus() {
        val hasNotification = PermissionUtils.isNotificationListenerEnabled(this)
        val hasOverlay = PermissionUtils.hasOverlayPermission(this)
        val hasUsage = PermissionUtils.hasUsageStatsPermission(this)
        val hasContacts = PermissionUtils.hasContactsPermission(this)

        binding.btnNotificationPermission.isEnabled = !hasNotification
        binding.btnOverlayPermission.isEnabled = !hasOverlay
        binding.btnUsagePermission.isEnabled = !hasUsage
        binding.btnContactsPermission.isEnabled = !hasContacts

        binding.btnContinue.isEnabled = hasNotification && hasOverlay && hasUsage && hasContacts
    }
}
