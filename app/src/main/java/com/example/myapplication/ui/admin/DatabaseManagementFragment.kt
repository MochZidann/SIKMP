package com.example.myapplication.ui.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.databinding.FragmentDatabaseManagementBinding
import com.example.myapplication.ui.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream

class DatabaseManagementFragment : Fragment() {
    private var _binding: FragmentDatabaseManagementBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) backupToUri(uri)
    }

    private val openRestore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreFromUri(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDatabaseManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())

        // Backup Logic (Mapping Download SQL button to Backup)
        binding.btnDownloadSql.setOnClickListener {
            createBackup.launch("sikmp_backup_${System.currentTimeMillis()}.db")
        }

        // Restore Logic (Mapping Restore Data button to Restore)
        binding.btnRestoreData.setOnClickListener {
            openRestore.launch(arrayOf("*/*"))
        }

        // btnUpload placeholder
        binding.btnUpload.setOnClickListener {
            Toast.makeText(requireContext(), "Gunakan 'Restore Data' untuk mengunggah file backup", Toast.LENGTH_SHORT).show()
        }
    }

    private fun backupToUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val dbFile = requireContext().getDatabasePath("koperasi_merah_putih.db")
            if (!dbFile.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Database belum ada", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(dbFile).use { input ->
                        input.copyTo(out)
                    }
                }
                AuditLogger.log(requireContext(), session.userId(), "BACKUP", "database", null, uri.toString())
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Backup database berhasil disimpan", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal Backup: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun restoreFromUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.closeInstance()
                val dbFile = requireContext().getDatabasePath("koperasi_merah_putih.db")
                dbFile.parentFile?.mkdirs()
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dbFile, false).use { output ->
                        input.copyTo(output)
                    }
                }
                AuditLogger.log(requireContext(), session.userId(), "RESTORE", "database", null, uri.toString())
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Restore selesai. Silakan login ulang.", Toast.LENGTH_LONG).show()
                    session.clear()
                    requireActivity().finishAffinity()
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal Restore: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
