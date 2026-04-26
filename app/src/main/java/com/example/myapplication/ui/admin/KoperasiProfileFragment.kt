package com.example.myapplication.ui.admin

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.SettingsEntity
import com.example.myapplication.databinding.FragmentKoperasiProfileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class KoperasiProfileFragment : Fragment() {
    private var _binding: FragmentKoperasiProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    private var selectedQrisUri: Uri? = null
    private var currentQrisPath: String? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedQrisUri = it
            binding.imgQrisPreview.load(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKoperasiProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        
        loadData()
        
        binding.btnUploadQris.setOnClickListener { pickImage.launch("image/*") }
        binding.btnRemoveQris.setOnClickListener {
            selectedQrisUri = null
            currentQrisPath = null
            binding.imgQrisPreview.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        binding.btnSaveProfile.setOnClickListener { saveData() }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val settings = db.settingsDao().get() ?: SettingsEntity()
            withContext(Dispatchers.Main) {
                binding.inputKoperasiName.setText(settings.koperasiName)
                binding.inputKoperasiAddress.setText(settings.koperasiAddress)
                currentQrisPath = settings.qrisImagePath
                if (currentQrisPath != null) {
                    binding.imgQrisPreview.load(File(currentQrisPath!!))
                }
            }
        }
    }

    private fun saveData() {
        val name = binding.inputKoperasiName.text.toString().trim()
        val address = binding.inputKoperasiAddress.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Nama koperasi wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Simpan gambar ke internal storage jika ada yang baru dipilih
                var finalPath = currentQrisPath
                selectedQrisUri?.let { uri ->
                    finalPath = saveImageToInternal(uri)
                }

                val db = AppDatabase.get(requireContext())
                val existing = db.settingsDao().get() ?: SettingsEntity()
                val newSettings = existing.copy(
                    koperasiName = name,
                    koperasiAddress = address,
                    qrisImagePath = finalPath,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                db.settingsDao().upsert(newSettings)
                
                AuditLogger.log(requireContext(), session.userId(), "UPDATE", "settings", 1, "Profile & QRIS updated")
                
                withContext(Dispatchers.Main) {
                    currentQrisPath = finalPath
                    selectedQrisUri = null
                    Toast.makeText(requireContext(), "Profil & QRIS berhasil disimpan", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveImageToInternal(uri: Uri): String {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val file = File(requireContext().filesDir, "qris_payment.jpg")
        val outputStream = FileOutputStream(file)
        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
