package com.example.myapplication.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.SettingsEntity
import com.example.myapplication.databinding.FragmentKoperasiProfileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KoperasiProfileFragment : Fragment() {
    private var _binding: FragmentKoperasiProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKoperasiProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        
        loadData()
        
        binding.btnSaveProfile.setOnClickListener {
            saveData()
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val settings = db.settingsDao().get() ?: SettingsEntity()
            withContext(Dispatchers.Main) {
                binding.inputKoperasiName.setText(settings.koperasiName)
                binding.inputKoperasiAddress.setText(settings.koperasiAddress)
            }
        }
    }

    private fun saveData() {
        val name = binding.inputKoperasiName.text.toString().trim()
        val address = binding.inputKoperasiAddress.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Nama koperasi tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.get(requireContext())
                val existing = db.settingsDao().get() ?: SettingsEntity()
                val newSettings = existing.copy(
                    koperasiName = name,
                    koperasiAddress = address,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                db.settingsDao().upsert(newSettings)
                AuditLogger.log(requireContext(), session.userId(), "UPDATE", "settings", 1, "profile update: $name")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Profil Koperasi diperbarui", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


