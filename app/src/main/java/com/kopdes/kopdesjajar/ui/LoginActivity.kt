package com.kopdes.kopdesjajar.ui

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
import com.kopdes.kopdesjajar.data.security.PasswordHasher
import com.kopdes.kopdesjajar.databinding.ActivityLoginBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class LoginActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val session by lazy { SessionManager(this) }

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
                // Pastikan User Anonymous Login di Firebase sudah selesai
                if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
                    try {
                        com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously().await()
                        Log.d("SyncDebug", "✅ Auth Anonim Berhasil di LoginActivity")
                    } catch (e: Exception) {
                        Log.e("SyncDebug", "💥 Gagal login anonim ke Firebase: ${e.message}")
                    }
                }

                // 1. Ambil data dari Remote (Kunci agar HP baru bisa login) - Coba Laravel MySQL Terlebih Dahulu
                var remoteUser: com.kopdes.kopdesjajar.data.db.UserEntity? = null
                try {
                    Log.d("SyncDebug", "🔄 Mencoba mencari user '$username' di Laravel MySQL...")
                    val response = com.kopdes.kopdesjajar.data.network.RetrofitClient.instance.pullUsers()
                    if (response.isSuccessful) {
                        val mysqlUsers = response.body() ?: emptyList()
                        val match = mysqlUsers.find { it.username.equals(username, ignoreCase = true) }
                        if (match != null) {
                            val parsedRole = try {
                                com.kopdes.kopdesjajar.data.model.Role.valueOf(match.role.uppercase())
                            } catch (e: Exception) {
                                if (match.role.equals("cashier", ignoreCase = true)) {
                                    com.kopdes.kopdesjajar.data.model.Role.KASIR
                                } else {
                                    com.kopdes.kopdesjajar.data.model.Role.KASIR
                                }
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
                            Log.d("SyncDebug", "✅ User '$username' ditemukan di Laravel MySQL!")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SyncDebug", "❌ Gagal mencari user di Laravel MySQL: ${e.message}")
                }

                // Fallback ke Firebase Firestore jika tidak ditemukan di Laravel MySQL
                if (remoteUser == null) {
                    try {
                        Log.d("SyncDebug", "🔄 Fallback: Mencoba mencari user '$username' di Firebase Firestore...")
                        remoteUser = firestoreManager.getUser(username)
                    } catch (e: Exception) {
                        Log.e("SyncDebug", "❌ Gagal mencari user di Firebase Firestore: ${e.message}")
                    }
                }
                
                // 2. Ambil data dari Lokal
                var localUser = db.userDao().findByUsername(username)
                
                // 3. LOGIKA RESTORE: Jika di lokal gak ada tapi di remote ada, simpan ke lokal
                if (localUser == null && remoteUser != null) {
                    val newId = db.userDao().insert(remoteUser)
                    localUser = remoteUser.copy(id = newId)
                    Log.d("SyncDebug", "✅ User $username direstore dari Remote ke Lokal")
                    val updated = localUser.copy(
                        name = remoteUser.name,
                        passwordHash = remoteUser.passwordHash,
                        salt = remoteUser.salt,
                        role = remoteUser.role,
                        isActive = remoteUser.isActive,
                        needsPasswordReset = remoteUser.needsPasswordReset,
                        isSynced = true
                    )
                    db.userDao().update(updated)
                    localUser = updated
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
                    // LOGIN BERHASIL
                    withContext(Dispatchers.Main) {
                        session.setSession(user.id, user.role, user.username, user.name)
                        binding.loginButton.text = "Sinkronisasi..."
                        Toast.makeText(this@LoginActivity, "Menyiapkan data...", Toast.LENGTH_SHORT).show()
                    }
                    
                    // 4. AUTO-RESTORE DATA: Tarik produk, member, dsb dari Cloud
                    val syncManager = SyncManager(this@LoginActivity)
                    syncManager.pullAllDataFromCloud()
                    
                    AuditLogger.log(this@LoginActivity, user.id, "LOGIN", "session", null, "Auth Success via Cloud")
                    
                    withContext(Dispatchers.Main) {
                        startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncDebug", "💥 Error login: ${e.message}")
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

            // Kalau tidak ada di lokal, cari di Firestore
            if (user == null) {
                try {
                    val remoteUser = FirestoreManager().getUser(username)
                    if (remoteUser != null) {
                        val newId = db.userDao().insert(remoteUser)
                        user = remoteUser.copy(id = newId)
                    }
                } catch (e: Exception) {
                    Log.e("SyncDebug", "❌ Gagal cari user di Firestore untuk reset: ${e.message}")
                }
            }

            if (user != null) {
                val updated = user.copy(needsPasswordReset = true, isSynced = false)
                db.userDao().update(updated)

                // Push langsung ke Firestore agar admin langsung tahu (tidak perlu tunggu 30s sync)
                try {
                    FirestoreManager().syncUser(updated)
                    Log.d("SyncDebug", "✅ RESET_REQUEST untuk '${username}' dipush ke Firestore")
                } catch (e: Exception) {
                    Log.e("SyncDebug", "❌ Gagal push reset request ke Firestore: ${e.message}")
                }

                AuditLogger.log(this@LoginActivity, user.id, "RESET_REQUEST", "user", user.id)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Permintaan reset password terkirim ke Admin.", Toast.LENGTH_LONG).show()
                }

                // Juga push ke Laravel di background
                launch(Dispatchers.IO) {
                    try {
                        SyncManager(this@LoginActivity).pushAllDataToServer()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Username '$username' tidak ditemukan di sistem.", Toast.LENGTH_LONG).show()
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
}
