package com.example.myapplication.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.MemberEntity
import com.example.myapplication.databinding.DialogMemberFormBinding
import com.example.myapplication.databinding.FragmentMemberManagementBinding
import com.example.myapplication.databinding.ItemMemberRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

class MemberManagementFragment : Fragment() {
    private var _binding: FragmentMemberManagementBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    private var memberList = listOf<MemberEntity>()

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importFromExcel(it) }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri: Uri? ->
        uri?.let { exportToExcel(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMemberManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        
        binding.recyclerMembers.layoutManager = LinearLayoutManager(requireContext())
        binding.btnAddMember.setOnClickListener { showMemberForm(null) }
        binding.btnImportExcel.setOnClickListener { importLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }
        binding.btnExportExcel.setOnClickListener { exportLauncher.launch("Data_Anggota_Koperasi.xlsx") }
        
        refreshData()
    }

    private fun refreshData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            memberList = db.memberDao().getAll()
            withContext(Dispatchers.Main) {
                binding.recyclerMembers.adapter = MemberAdapter(memberList) { showMemberForm(it) }
            }
        }
    }

    private fun importFromExcel(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
                if (inputStream == null) return@launch
                
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)
                val db = AppDatabase.get(requireContext())
                var importedCount = 0

                // Skip header (row 0)
                for (i in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    val name = row.getCell(0)?.toString() ?: ""
                    val memberNo = row.getCell(1)?.let { 
                        if (it.cellType == CellType.NUMERIC) it.numericCellValue.toLong().toString() else it.toString()
                    } ?: ""
                    val phone = row.getCell(2)?.toString()
                    val address = row.getCell(3)?.toString()

                    if (name.isNotBlank() && memberNo.isNotBlank()) {
                        db.memberDao().insert(MemberEntity(
                            memberNo = memberNo,
                            name = name,
                            phone = phone,
                            address = address
                        ))
                        importedCount++
                    }
                }
                
                AuditLogger.log(requireContext(), session.userId(), "IMPORT", "member", null, "count=$importedCount")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Berhasil mengimpor $importedCount anggota", Toast.LENGTH_LONG).show()
                    refreshData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal impor: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportToExcel(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val workbook = XSSFWorkbook()
                val sheet = workbook.createSheet("Anggota")
                
                // Header
                val header = sheet.createRow(0)
                header.createCell(0).setCellValue("Nama")
                header.createCell(1).setCellValue("Nomor Anggota")
                header.createCell(2).setCellValue("Telepon")
                header.createCell(3).setCellValue("Alamat")
                header.createCell(4).setCellValue("Status")

                // Data
                memberList.forEachIndexed { index, member ->
                    val row = sheet.createRow(index + 1)
                    row.createCell(0).setCellValue(member.name)
                    row.createCell(1).setCellValue(member.memberNo)
                    row.createCell(2).setCellValue(member.phone ?: "-")
                    row.createCell(3).setCellValue(member.address ?: "-")
                    row.createCell(4).setCellValue(if (member.isActive) "Aktif" else "Non-Aktif")
                }

                val outputStream: OutputStream? = requireContext().contentResolver.openOutputStream(uri)
                outputStream?.use { workbook.write(it) }
                workbook.close()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Data berhasil diekspor", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal ekspor: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showMemberForm(existing: MemberEntity?) {
        val dbBinding = DialogMemberFormBinding.inflate(layoutInflater)
        
        existing?.let {
            dbBinding.etMemberNo.setText(it.memberNo)
            dbBinding.etMemberNo.isEnabled = false
            dbBinding.etName.setText(it.name)
            dbBinding.etPhone.setText(it.phone)
            dbBinding.etAddress.setText(it.address)
            if (it.isActive) dbBinding.rbActive.isChecked = true else dbBinding.rbInactive.isChecked = true
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "Tambah Anggota Baru" else "Update Anggota")
            .setView(dbBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                if (!dbBinding.cbAgreements.isChecked) {
                    Toast.makeText(context, "Harap konfirmasi perubahan", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val memberNo = dbBinding.etMemberNo.text.toString().trim()
                val name = dbBinding.etName.text.toString().trim()
                val phone = dbBinding.etPhone.text.toString().trim()
                val address = dbBinding.etAddress.text.toString().trim()
                val isActive = dbBinding.rbActive.isChecked
                
                if (memberNo.isBlank() || name.isBlank()) {
                    Toast.makeText(context, "Nomor dan Nama wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveMember(existing, memberNo, name, phone, address, isActive)
            }
            .setNegativeButton("Batal", null)
            .let { if (existing != null) it.setNeutralButton("Hapus") { _, _ -> confirmDelete(existing) } else it }
            .show()
    }

    private fun confirmDelete(member: MemberEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Anggota")
            .setMessage("Apakah Anda yakin ingin menghapus '${member.name}'?")
            .setPositiveButton("Hapus") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.get(requireContext())
                    db.memberDao().delete(member)
                    AuditLogger.log(requireContext(), session.userId(), "DELETE", "member", member.id, "memberNo=${member.memberNo}")
                    withContext(Dispatchers.Main) {
                        refreshData()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun saveMember(existing: MemberEntity?, memberNo: String, name: String, phone: String, address: String, isActive: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            if (existing == null) {
                val id = db.memberDao().insert(MemberEntity(memberNo = memberNo, name = name, phone = phone, address = address, isActive = isActive))
                AuditLogger.log(requireContext(), session.userId(), "CREATE", "member", id, "memberNo=$memberNo")
            } else {
                db.memberDao().update(existing.copy(memberNo = memberNo, name = name, phone = phone, address = address, isActive = isActive))
                AuditLogger.log(requireContext(), session.userId(), "UPDATE", "member", existing.id, "memberNo=$memberNo")
            }
            withContext(Dispatchers.Main) {
                refreshData()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class MemberAdapter(private val items: List<MemberEntity>, val onEdit: (MemberEntity) -> Unit) : RecyclerView.Adapter<MemberAdapter.ViewHolder>() {
        inner class ViewHolder(val b: ItemMemberRowBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            ItemMemberRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.b.txtName.text = item.name
            holder.b.txtMemberNo.text = item.memberNo
            holder.b.txtPhone.text = item.phone ?: "-"
            
            // Added Status column support in binding
            holder.b.root.findViewById<android.widget.TextView>(R.id.txtStatus)?.apply {
                text = if (item.isActive) "Aktif" else "Non-Aktif"
                setTextColor(ContextCompat.getColor(context, if (item.isActive) R.color.accent_teal else R.color.gray_500))
            }

            holder.b.btnEdit.setOnClickListener { onEdit(item) }
        }
        override fun getItemCount() = items.size
    }
}
