package com.example.myapplication.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.model.Role
import com.example.myapplication.databinding.ActivityBackupRestoreBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream

class BackupRestoreActivity : BaseAuthedActivity() {
    private lateinit var binding: ActivityBackupRestoreBinding

    override fun allowedRoles(): Set<Role> = setOf(Role.ADMIN_SISTEM)

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) backupToUri(uri)
    }

    private val openRestore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupRestoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnBackupNow.setOnClickListener {
            createBackup.launch("koperasi_merah_putih_backup.db")
        }
        binding.btnRestore.setOnClickListener {
            openRestore.launch(arrayOf("*/*"))
        }
        binding.info.text = "Backup akan menyimpan file database. Restore akan mengganti database aplikasi."
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun backupToUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dbFile = getDatabasePath("koperasi_merah_putih.db")
            if (!dbFile.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BackupRestoreActivity, "Database belum ada", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(dbFile).use { input ->
                    input.copyTo(out)
                }
            }
            AuditLogger.log(
                context = this@BackupRestoreActivity,
                userId = session.userId(),
                action = "BACKUP",
                entity = "database",
                entityId = null,
                detail = uri.toString()
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(this@BackupRestoreActivity, "Backup selesai", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restoreFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.closeInstance()
            val dbFile = getDatabasePath("koperasi_merah_putih.db")
            dbFile.parentFile?.mkdirs()
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dbFile, false).use { output ->
                    input.copyTo(output)
                }
            }
            AuditLogger.log(
                context = this@BackupRestoreActivity,
                userId = session.userId(),
                action = "RESTORE",
                entity = "database",
                entityId = null,
                detail = uri.toString()
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(this@BackupRestoreActivity, "Restore selesai. Silakan login ulang.", Toast.LENGTH_LONG).show()
                session.clear()
                finishAffinity()
                startActivity(android.content.Intent(this@BackupRestoreActivity, LoginActivity::class.java))
            }
        }
    }
}
