package com.example.smartupiannotation.ui.setup

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartupiannotation.MainActivity
import com.example.smartupiannotation.data.remote.AuthManager
import com.example.smartupiannotation.databinding.ActivityAuthBinding
import com.example.smartupiannotation.utils.PermissionUtils
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val authManager by lazy { AuthManager() }
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (authManager.isUserLoggedIn()) {
            checkPermissionsAndNavigate()
            return
        }

        binding.btnPrimary.setOnClickListener {
            handleAuth()
        }

        binding.tvToggleAuth.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUi()
        }
    }

    private fun handleAuth() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        binding.pbLoading.visibility = View.VISIBLE
        binding.btnPrimary.isEnabled = false

        lifecycleScope.launch {
            val result = if (isLoginMode) {
                authManager.login(email, password)
            } else {
                authManager.signUp(email, password)
            }

            binding.pbLoading.visibility = View.GONE
            binding.btnPrimary.isEnabled = true

            result.onSuccess {
                Toast.makeText(this@AuthActivity, "Success!", Toast.LENGTH_SHORT).show()
                checkPermissionsAndNavigate()
            }.onFailure {
                Toast.makeText(this@AuthActivity, "Error: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUi() {
        if (isLoginMode) {
            binding.tvAuthTitle.text = "Login"
            binding.btnPrimary.text = "Login"
            binding.tvToggleAuth.text = "Don't have an account? Sign Up"
        } else {
            binding.tvAuthTitle.text = "Sign Up"
            binding.btnPrimary.text = "Sign Up"
            binding.tvToggleAuth.text = "Already have an account? Login"
        }
    }

    private fun checkPermissionsAndNavigate() {
        if (PermissionUtils.allPermissionsGranted(this)) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        finish()
    }
}
