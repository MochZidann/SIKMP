package com.kopdes.kopdesjajar.ui

import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.kopdes.kopdesjajar.R
import com.kopdes.kopdesjajar.data.audit.AuditLogger
import com.kopdes.kopdesjajar.data.auth.SessionManager
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.db.DatabaseSeeder
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.SyncManager
import com.kopdes.kopdesjajar.data.network.VolleyHelper
import com.kopdes.kopdesjajar.data.network.UserSyncPayload
import com.kopdes.kopdesjajar.data.security.PasswordHasher
import com.kopdes.kopdesjajar.databinding.ActivityLoginBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.reflect.TypeToken
import com.android.volley.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class LoginActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val session by lazy { SessionManager(this) }

    override fun attachBaseContext(newBase: Context) {
        val sessionManager = SessionManager(newBase)
        val fontScale = sessionManager.getTextSizeScale()
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = fontScale
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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

        binding.btnForgotPassword.visibility = View.GONE

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

            performLogin(username, password)
        }

        binding.btnForgotPassword.setOnClickListener {
            val username = binding.username.text?.toString()?.trim().orEmpty()
            if (username.isBlank()) {
                Toast.makeText(this, "Masukkan username Anda terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset Password")
                .setMessage("Kirim permintaan reset password untuk user '$username' ke Admin?")
                .setPositiveButton("Ya, Kirim") { _, _ -> requestReset(username) }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun performLogin(username: String, password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                binding.loginButton.isEnabled = false
                binding.loginButton.text = "Mengecek Cloud..."
            }

            val db = AppDatabase.get(this@LoginActivity)
            val firestoreManager = FirestoreManager()
            
            try {
                if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
                    try {
                        com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously().await()
                    } catch (e: Exception) {
                        Log.e("SyncDebug", "Gagal login anonim: ${e.message}")
                    }
                }

                var remoteUser: com.kopdes.kopdesjajar.data.db.UserEntity? = null
                try {
                    Log.d("SyncDebug", "🔄 Volley: Mencari user '$username' di Laravel...")
                    val mysqlUsers = VolleyHelper.requestList(this@LoginActivity, Request.Method.GET, "sync/users", object : TypeToken<List<UserSyncPayload>>() {})
                    val match = mysqlUsers.find { it.username.equals(username, ignoreCase = true) }
                    if (match != null) {
                        val parsedRole = try {
                            com.kopdes.kopdesjajar.data.model.Role.valueOf(match.role.uppercase())
                        } catch (e: Exception) {
                            com.kopdes.kopdesjajar.data.model.Role.KASIR
                        }
                        remoteUser = com.kopdes.kopdesjajar.data.db.UserEntity(
                            id = match.id,
                            name = match.name,
                            username = match.username,
                            passwordHash = match.passwordHash,
                            salt = match.salt,
                            role = parsedRole,
                            isActive = match.isActive == 1,
                            needsPasswordReset = match.needsPasswordReset == 1,
                            isSynced = true,
                            createdAtEpochMs = match.createdAtEpochMs
                        )
                    }
                } catch (e: Exception) {
                    Log.e("SyncDebug", "Volley Login Search Error: ${e.message}")
                }

                if (remoteUser == null) {
                    remoteUser = firestoreManager.getUser(username)
                }
                
                var localUser = db.userDao().findByUsername(username)
                
                if (localUser == null && remoteUser != null) {
                    val newId = db.userDao().insert(remoteUser)
                    localUser = remoteUser.copy(id = newId)
                    db.userDao().update(localUser.copy(isSynced = true))
                }

                val user = localUser

                if (user == null || !user.isActive || !PasswordHasher.verify(password, user.salt, user.passwordHash)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginActivity, "Username atau password salah", Toast.LENGTH_SHORT).show()
                        binding.btnForgotPassword.visibility = View.VISIBLE
                        resetButton()
                    }
                } else if (user.needsPasswordReset) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginActivity, "Admin mewajibkan Anda ganti password.", Toast.LENGTH_LONG).show()
                        resetButton()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        session.setSession(user.id, user.role, user.username, user.name)
                        binding.loginButton.text = "Sinkronisasi..."
                        Toast.makeText(this@LoginActivity, "Menyiapkan data...", Toast.LENGTH_SHORT).show()
                    }
                    
                    val syncManager = SyncManager(this@LoginActivity)
                    syncManager.pullAllDataFromCloud()
                    
                    AuditLogger.log(this@LoginActivity, user.id, "LOGIN", "session", null, "Auth Success via Cloud")
                    
                    withContext(Dispatchers.Main) {
                        startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Gagal terhubung ke Cloud", Toast.LENGTH_SHORT).show()
                    resetButton()
                }
            }
        }
    }

    private fun resetButton() {
        binding.loginButton.isEnabled = true
        binding.loginButton.text = "MASUK"
    }

    private fun requestReset(username: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@LoginActivity)
            var user = db.userDao().findByUsername(username)

            if (user == null) {
                try {
                    val remoteUser = FirestoreManager().getUser(username)
                    if (remoteUser != null) {
                        val newId = db.userDao().insert(remoteUser)
                        user = remoteUser.copy(id = newId)
                    }
                } catch (e: Exception) {}
            }

            if (user != null) {
                val updated = user.copy(needsPasswordReset = true, isSynced = false)
                db.userDao().update(updated)
                try {
                    FirestoreManager().syncUser(updated)
                } catch (e: Exception) {}

                AuditLogger.log(this@LoginActivity, user.id, "RESET_REQUEST", "user", user.id)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Permintaan reset password terkirim.", Toast.LENGTH_LONG).show()
                }

                launch(Dispatchers.IO) {
                    try { SyncManager(this@LoginActivity).pushAllDataToServer() } catch (e: Exception) {}
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "User tidak ditemukan.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun applyEdgeToEdgeInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val original = (v.getTag(R.id.edge_to_edge_original_paddings) as? IntArray) ?: intArrayOf(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom).also { v.setTag(R.id.edge_to_edge_original_paddings, it) }
            v.setPadding(original[0] + bars.left, original[1] + bars.top, original[2] + bars.right, original[3] + bars.bottom)
            insets
        }
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is android.widget.EditText) {
                val outRect = android.graphics.Rect()
                focused.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    focused.clearFocus()
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                            as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(focused.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
