package com.example.myapplication.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.DatabaseSeeder
import com.example.myapplication.data.security.PasswordHasher
import com.example.myapplication.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class LoginActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val session by lazy { SessionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force Landscape orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdgeInsets(binding.root)

        if (savedInstanceState == null) {
            binding.logo.alpha = 0f
            binding.logo.animate().alpha(1f).setDuration(1500).start()
        }

        if (session.isLoggedIn()) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            DatabaseSeeder.ensureSeeded(this@LoginActivity)
        }

        binding.loginButton.setOnClickListener {
            val username = binding.username.text?.toString()?.trim().orEmpty()
            val password = binding.password.text?.toString().orEmpty()
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Username dan password wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.get(this@LoginActivity)
                val user = db.userDao().findByUsername(username)
                
                if (user == null || !user.isActive || !PasswordHasher.verify(password, user.salt, user.passwordHash)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginActivity, "Login gagal", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        session.setSession(user.id, user.role, user.username, user.name)
                        lifecycleScope.launch(Dispatchers.IO) {
                            AuditLogger.log(
                                context = this@LoginActivity,
                                userId = user.id,
                                action = "LOGIN",
                                entity = "session",
                                entityId = null,
                                detail = "username=${user.username}"
                            )
                        }
                        startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                        finish()
                    }
                }
            }
        }
    }

    private fun applyEdgeToEdgeInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = max(bars.bottom, ime.bottom)
            val original = (v.getTag(R.id.edge_to_edge_original_paddings) as? IntArray) ?: intArrayOf(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                v.paddingBottom
            ).also { v.setTag(R.id.edge_to_edge_original_paddings, it) }
            v.setPadding(
                original[0] + bars.left,
                original[1] + bars.top,
                original[2] + bars.right,
                original[3] + bottom
            )
            insets
        }
    }
}

