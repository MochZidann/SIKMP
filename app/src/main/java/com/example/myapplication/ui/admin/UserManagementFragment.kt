package com.example.myapplication.ui.admin

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.UserEntity
import com.example.myapplication.data.model.Role
import com.example.myapplication.data.security.PasswordHasher
import com.example.myapplication.databinding.DialogUserFormSimpleBinding
import com.example.myapplication.databinding.FragmentUserManagementBinding
import com.example.myapplication.databinding.ItemUserRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

class UserManagementFragment : Fragment() {
    private var _binding: FragmentUserManagementBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    private var allUsers = listOf<UserEntity>()
    private var currentTab = 0 // 0: All, 1: Reset Requests

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importFromExcel(it) }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri: Uri? ->
        uri?.let { exportToExcel(it) }
    }

    private val pdfExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        uri?.let { performPdfExport(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        
        binding.recyclerUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.btnAddUser.setOnClickListener { showUserForm(null) }
        binding.btnImportExcel.setOnClickListener { importLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }
        binding.btnExportExcel.setOnClickListener { exportLauncher.launch("Data_Pengguna.xlsx") }
        binding.btnExportPdf.setOnClickListener { pdfExportLauncher.launch("Data_Pengguna_${System.currentTimeMillis()}.pdf") }
        
        binding.etSearch.addTextChangedListener { 
            performSearch(it?.toString().orEmpty())
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                binding.txtTableTitle.text = if (currentTab == 0) "Daftar Pengguna Sistem" else "Permintaan Reset Password"
                performSearch(binding.etSearch.text?.toString().orEmpty())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        refreshData()
    }

    private fun refreshData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            allUsers = db.userDao().getAll()
            withContext(Dispatchers.Main) {
                performSearch(binding.etSearch.text?.toString().orEmpty())
            }
        }
    }

    private fun performSearch(query: String) {
        var filtered = if (query.isBlank()) {
            allUsers
        } else {
            allUsers.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.username.contains(query, ignoreCase = true) 
            }
        }

        if (currentTab == 1) {
            filtered = filtered.filter { it.needsPasswordReset }
        }

        binding.recyclerUsers.adapter = UserAdapter(filtered, 
            onEdit = { showUserForm(it) },
            onDelete = { confirmDelete(it) },
            onApproveReset = { approveReset(it) }
        )
    }

    private fun approveReset(user: UserEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Setujui Reset Password")
            .setMessage("Password user '${user.username}' akan direset menjadi default '123456'. Lanjutkan?")
            .setPositiveButton("Ya, Reset") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.get(requireContext())
                    val salt = PasswordHasher.generateSalt()
                    val hash = PasswordHasher.hash("123456", salt)
                    db.userDao().update(user.copy(
                        passwordHash = hash,
                        salt = salt,
                        needsPasswordReset = false
                    ))
                    AuditLogger.log(requireContext(), session.userId(), "RESET_APPROVE", "user", user.id, "Admin reset password for user ${user.username}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Password berhasil direset", Toast.LENGTH_SHORT).show()
                        refreshData()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
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

                for (i in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    val name = row.getCell(0)?.toString() ?: ""
                    val username = row.getCell(1)?.toString() ?: ""
                    val roleStr = row.getCell(2)?.toString() ?: "KASIR"
                    
                    if (name.isNotBlank() && username.isNotBlank()) {
                        if (db.userDao().findByUsername(username) == null) {
                            val role = try { Role.valueOf(roleStr.uppercase()) } catch (e: Exception) { Role.KASIR }
                            val salt = PasswordHasher.generateSalt()
                            val hash = PasswordHasher.hash("123456", salt)
                            
                            db.userDao().insert(UserEntity(
                                name = name,
                                username = username,
                                passwordHash = hash,
                                salt = salt,
                                role = role,
                                isActive = true
                            ))
                            importedCount++
                        }
                    }
                }
                
                AuditLogger.log(requireContext(), session.userId(), "IMPORT", "user", null, "count=$importedCount")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Berhasil mengimpor $importedCount pengguna", Toast.LENGTH_LONG).show()
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
                val sheet = workbook.createSheet("Pengguna")
                val header = sheet.createRow(0)
                header.createCell(0).setCellValue("Nama")
                header.createCell(1).setCellValue("Username")
                header.createCell(2).setCellValue("Role")
                header.createCell(3).setCellValue("Status")

                allUsers.forEachIndexed { index, user ->
                    val row = sheet.createRow(index + 1)
                    row.createCell(0).setCellValue(user.name)
                    row.createCell(1).setCellValue(user.username)
                    row.createCell(2).setCellValue(user.role.name)
                    row.createCell(3).setCellValue(if (user.isActive) "Aktif" else "Nonaktif")
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

    private fun performPdfExport(uri: Uri) {
        val users = allUsers
        if (users.isEmpty()) {
            Toast.makeText(requireContext(), "Tidak ada data untuk diexport", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pdfDocument = android.graphics.pdf.PdfDocument()
                val paint = android.graphics.Paint()
                val titlePaint = android.graphics.Paint().apply {
                    textSize = 18f
                    isFakeBoldText = true
                    color = android.graphics.Color.BLACK
                }
                val headerPaint = android.graphics.Paint().apply {
                    textSize = 12f
                    isFakeBoldText = true
                    color = android.graphics.Color.BLACK
                }
                val textPaint = android.graphics.Paint().apply {
                    textSize = 10f
                    color = android.graphics.Color.DKGRAY
                }

                val pageWidth = 595
                val pageHeight = 842
                var pageNumber = 1
                
                var myPageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                var myPage = pdfDocument.startPage(myPageInfo)
                var canvas = myPage.canvas
                
                var y = 50f
                canvas.drawText("Laporan Data Pengguna", 40f, y, titlePaint)
                y += 20f
                canvas.drawText("Dicetak pada: ${com.example.myapplication.ui.UiFormat.dateTime(System.currentTimeMillis())}", 40f, y, textPaint)
                y += 40f

                canvas.drawText("NAMA", 40f, y, headerPaint)
                canvas.drawText("USERNAME", 200f, y, headerPaint)
                canvas.drawText("ROLE", 350f, y, headerPaint)
                canvas.drawText("STATUS", 450f, y, headerPaint)
                y += 10f
                canvas.drawLine(40f, y, 555f, y, paint)
                y += 20f

                users.forEach { user ->
                    if (y > pageHeight - 50) {
                        pdfDocument.finishPage(myPage)
                        pageNumber++
                        myPageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        myPage = pdfDocument.startPage(myPageInfo)
                        canvas = myPage.canvas
                        y = 50f
                        
                        canvas.drawText("NAMA", 40f, y, headerPaint)
                        canvas.drawText("USERNAME", 200f, y, headerPaint)
                        canvas.drawText("ROLE", 350f, y, headerPaint)
                        canvas.drawText("STATUS", 450f, y, headerPaint)
                        y += 10f
                        canvas.drawLine(40f, y, 555f, y, paint)
                        y += 20f
                    }

                    canvas.drawText(user.name, 40f, y, textPaint)
                    canvas.drawText(user.username, 200f, y, textPaint)
                    canvas.drawText(user.role.name, 350f, y, textPaint)
                    canvas.drawText(if (user.isActive) "Aktif" else "Nonaktif", 450f, y, textPaint)
                    
                    y += 20f
                }

                pdfDocument.finishPage(myPage)

                val outputStream = requireContext().contentResolver.openOutputStream(uri)
                outputStream?.use { pdfDocument.writeTo(it) }
                pdfDocument.close()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Data Pengguna berhasil diekspor ke PDF", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal export PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUserForm(existing: UserEntity?) {
        val dbBinding = DialogUserFormSimpleBinding.inflate(layoutInflater)
        val roles = Role.entries.toTypedArray()
        dbBinding.spinnerRole.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, roles.map { it.name })

        existing?.let {
            dbBinding.etName.setText(it.name)
            dbBinding.etUsername.setText(it.username)
            dbBinding.etUsername.isEnabled = false
            dbBinding.etPassword.hint = "Password baru (opsional)"
            dbBinding.spinnerRole.setSelection(roles.indexOf(it.role))
            if (it.isActive) dbBinding.rbActive.isChecked = true else dbBinding.rbInactive.isChecked = true
            dbBinding.cbAgreements.isChecked = true
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "Tambah User Baru" else "Update User")
            .setView(dbBinding.root)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (!dbBinding.cbAgreements.isChecked) {
                    Toast.makeText(context, "Harap konfirmasi perubahan", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val name = dbBinding.etName.text.toString().trim()
                val username = dbBinding.etUsername.text.toString().trim()
                val password = dbBinding.etPassword.text.toString()
                val role = roles[dbBinding.spinnerRole.selectedItemPosition]
                val isActive = dbBinding.rbActive.isChecked

                if (name.isBlank()) {
                    dbBinding.etName.error = "Nama wajib diisi"
                    return@setOnClickListener
                }
                if (username.isBlank()) {
                    dbBinding.etUsername.error = "Username wajib diisi"
                    return@setOnClickListener
                }
                if (existing == null && password.isBlank()) {
                    dbBinding.etPassword.error = "Password wajib diisi untuk user baru"
                    return@setOnClickListener
                }

                saveUser(existing, name, username, password, role, isActive)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun saveUser(existing: UserEntity?, name: String, username: String, password: String, role: Role, isActive: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.get(requireContext())
                if (existing == null) {
                    val salt = PasswordHasher.generateSalt()
                    val hash = PasswordHasher.hash(password, salt)
                    db.userDao().insert(UserEntity(name = name, username = username, passwordHash = hash, salt = salt, role = role, isActive = isActive))
                } else {
                    val updated = if (password.isBlank()) {
                        existing.copy(name = name, role = role, isActive = isActive)
                    } else {
                        val salt = PasswordHasher.generateSalt()
                        val hash = PasswordHasher.hash(password, salt)
                        existing.copy(name = name, role = role, salt = salt, passwordHash = hash, isActive = isActive)
                    }
                    db.userDao().update(updated)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Data pengguna berhasil disimpan", Toast.LENGTH_SHORT).show()
                    refreshData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmDelete(user: UserEntity) {
        if (user.id == session.userId()) {
            Toast.makeText(requireContext(), "Tidak bisa menghapus akun sendiri!", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Pengguna")
            .setMessage("Apakah Anda yakin ingin menghapus '${user.name}'?")
            .setPositiveButton("Hapus") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(requireContext()).userDao().delete(user)
                    withContext(Dispatchers.Main) { refreshData() }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class UserAdapter(
        private val items: List<UserEntity>, 
        val onEdit: (UserEntity) -> Unit,
        val onDelete: (UserEntity) -> Unit,
        val onApproveReset: (UserEntity) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {
        inner class ViewHolder(val b: ItemUserRowBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            ItemUserRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.b.txtName.text = item.name
            holder.b.txtUsername.text = item.username
            holder.b.txtRole.text = item.role.name
            
            if (item.needsPasswordReset) {
                holder.b.chipStatus.text = "RESET REQUEST"
                holder.b.chipStatus.setChipBackgroundColorResource(R.color.primary_red)
                holder.b.chipStatus.setTextColor(requireContext().getColor(R.color.white))
                
                holder.b.btnDelete.setIconResource(android.R.drawable.ic_menu_edit) // Reuse for reset
                holder.b.btnDelete.setOnClickListener { onApproveReset(item) }
            } else {
                holder.b.chipStatus.text = if (item.isActive) "Aktif" else "Nonaktif"
                holder.b.chipStatus.setChipBackgroundColorResource(if (item.isActive) R.color.accent_teal_light else R.color.gray_200)
                holder.b.chipStatus.setTextColor(requireContext().getColor(R.color.gray_800))
                
                holder.b.btnDelete.setIconResource(android.R.drawable.ic_menu_delete)
                holder.b.btnDelete.setOnClickListener { onDelete(item) }
            }
            
            holder.itemView.setOnClickListener { onEdit(item) }
            holder.b.btnEdit.setOnClickListener { onEdit(item) }
        }
        override fun getItemCount() = items.size
    }
}
