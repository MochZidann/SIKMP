package com.example.myapplication.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.AuditLogEntity
import com.example.myapplication.data.db.UserEntity
import com.example.myapplication.data.model.Role
import com.example.myapplication.data.security.PasswordHasher
import com.example.myapplication.databinding.ActivitySimpleListBinding
import com.example.myapplication.ui.adapters.TwoLineAdapter
import com.example.myapplication.ui.adapters.TwoLineRow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserManagementActivity : BaseAuthedActivity() {
    private lateinit var binding: ActivitySimpleListBinding
    private val adapter = TwoLineAdapter { row -> onUserClicked(row.id) }

    override fun allowedRoles(): Set<Role> = setOf(Role.ADMIN_SISTEM, Role.OWNER_PENGAWAS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.title = "Kelola Pengguna"

        binding.recycler.adapter = adapter
        binding.fab.setOnClickListener { showUserForm(null) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            val users = AppDatabase.get(this@UserManagementActivity).userDao().getAll()
            val rows = users.map {
                TwoLineRow(
                    id = it.id,
                    title = "${it.name} (${it.username})",
                    subtitle = "Peran: ${it.role.name} • ${if (it.isActive) "Aktif" else "Nonaktif"}"
                )
            }
            withContext(Dispatchers.Main) {
                adapter.submit(rows)
            }
        }
    }

    private fun onUserClicked(userId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@UserManagementActivity)
            val user = db.userDao().findById(userId) ?: return@launch
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@UserManagementActivity)
                    .setTitle(user.name)
                    .setItems(arrayOf("Edit", if (user.isActive) "Nonaktifkan" else "Aktifkan", "Hapus")) { _, which ->
                        when (which) {
                            0 -> showUserForm(user)
                            1 -> toggleActive(user)
                            2 -> confirmDelete(user)
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
    }

    private fun toggleActive(user: UserEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@UserManagementActivity)
            db.userDao().update(user.copy(isActive = !user.isActive))
            AuditLogger.log(
                context = this@UserManagementActivity,
                userId = session.userId(),
                action = "UPDATE",
                entity = "user",
                entityId = user.id,
                detail = "toggleActive=${!user.isActive}"
            )
            withContext(Dispatchers.Main) {
                refresh()
            }
        }
    }

    private fun confirmDelete(user: UserEntity) {
        AlertDialog.Builder(this)
            .setTitle("Hapus pengguna?")
            .setMessage("${user.name} (${user.username})")
            .setPositiveButton("Hapus") { _, _ -> deleteUser(user) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteUser(user: UserEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@UserManagementActivity)
            db.userDao().delete(user)
            AuditLogger.log(
                context = this@UserManagementActivity,
                userId = session.userId(),
                action = "DELETE",
                entity = "user",
                entityId = user.id,
                detail = "username=${user.username}"
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(this@UserManagementActivity, "Terhapus", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    private fun showUserForm(existing: UserEntity?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(this).apply {
            hint = "Nama"
            setText(existing?.name.orEmpty())
        }
        val usernameInput = EditText(this).apply {
            hint = "Username"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(existing?.username.orEmpty())
            isEnabled = existing == null
        }
        val passwordInput = EditText(this).apply {
            hint = if (existing == null) "Password" else "Password baru (opsional)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val roleSpinner = Spinner(this)
        val roles = Role.entries.toTypedArray()
        roleSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles.map { it.name })
        if (existing != null) {
            val idx = roles.indexOf(existing.role).coerceAtLeast(0)
            roleSpinner.setSelection(idx)
        }

        container.addView(nameInput)
        container.addView(usernameInput)
        container.addView(passwordInput)
        container.addView(roleSpinner)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Tambah Pengguna" else "Edit Pengguna")
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val username = usernameInput.text?.toString()?.trim().orEmpty()
                val password = passwordInput.text?.toString().orEmpty()
                val role = roles.getOrNull(roleSpinner.selectedItemPosition) ?: Role.ADMIN_SISTEM
                if (name.isBlank() || username.isBlank() || (existing == null && password.isBlank())) {
                    Toast.makeText(this, "Data belum lengkap", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (existing != null && existing.role != role) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Konfirmasi Perubahan Hak Akses")
                        .setMessage("Apakah Anda yakin ingin mengubah hak akses ${existing.name} menjadi ${role.name}?")
                        .setPositiveButton("Ya") { _, _ ->
                            saveUser(existing, name, username, password, role)
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                } else {
                    saveUser(existing, name, username, password, role)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun saveUser(existing: UserEntity?, name: String, username: String, password: String, role: Role) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@UserManagementActivity)
            if (existing == null) {
                val salt = PasswordHasher.generateSalt()
                val hash = PasswordHasher.hash(password, salt)
                val id = db.userDao().insert(
                    UserEntity(
                        name = name,
                        username = username,
                        passwordHash = hash,
                        salt = salt,
                        role = role
                    )
                )
                AuditLogger.log(
                    context = this@UserManagementActivity,
                    userId = session.userId(),
                    action = "CREATE",
                    entity = "user",
                    entityId = id,
                    detail = "username=$username role=${role.name}"
                )
            } else {
                val roleChanged = existing.role != role
                val updated = if (password.isBlank()) {
                    existing.copy(name = name, role = role)
                } else {
                    val salt = PasswordHasher.generateSalt()
                    val hash = PasswordHasher.hash(password, salt)
                    existing.copy(name = name, role = role, salt = salt, passwordHash = hash)
                }
                db.withTransaction {
                    db.userDao().update(updated)
                    if (roleChanged) {
                        val adminUsername = session.username().orEmpty()
                        db.auditLogDao().insert(
                            AuditLogEntity(
                                userId = session.userId(),
                                action = "UPDATE_ROLE",
                                entity = "user",
                                entityId = existing.id,
                                detail = "Role diubah: $adminUsername mengubah akses ${existing.username} menjadi ${role.name}"
                            )
                        )
                    } else {
                        db.auditLogDao().insert(
                            AuditLogEntity(
                                userId = session.userId(),
                                action = "UPDATE",
                                entity = "user",
                                entityId = existing.id,
                                detail = "username=${existing.username}"
                            )
                        )
                    }
                }
            }
            withContext(Dispatchers.Main) {
                refresh()
            }
        }
    }
}
