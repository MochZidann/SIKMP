package com.example.myapplication.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserManagementFragment : Fragment() {
    private var _binding: FragmentUserManagementBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        
        binding.recyclerUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.btnAddUser.setOnClickListener { showUserForm(null) }
        
        refreshData()
    }

    private fun refreshData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val users = db.userDao().getAll()
            withContext(Dispatchers.Main) {
                binding.recyclerUsers.adapter = UserAdapter(users, 
                    onEdit = { showUserForm(it) },
                    onDelete = { confirmDelete(it) }
                )
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
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "Tambah User Baru" else "Update User")
            .setView(dbBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                if (!dbBinding.cbAgreements.isChecked) {
                    Toast.makeText(context, "Harap konfirmasi perubahan", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val name = dbBinding.etName.text.toString().trim()
                val username = dbBinding.etUsername.text.toString().trim()
                val password = dbBinding.etPassword.text.toString()
                val role = roles[dbBinding.spinnerRole.selectedItemPosition]
                val isActive = dbBinding.rbActive.isChecked

                if (name.isBlank() || username.isBlank() || (existing == null && password.isBlank())) {
                    Toast.makeText(context, "Data belum lengkap", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                saveUser(existing, name, username, password, role, isActive)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun saveUser(existing: UserEntity?, name: String, username: String, password: String, role: Role, isActive: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            if (existing == null) {
                val salt = PasswordHasher.generateSalt()
                val hash = PasswordHasher.hash(password, salt)
                val id = db.userDao().insert(UserEntity(name = name, username = username, passwordHash = hash, salt = salt, role = role, isActive = isActive))
                AuditLogger.log(requireContext(), session.userId(), "CREATE", "user", id, "username=$username")
            } else {
                val updated = if (password.isBlank()) {
                    existing.copy(name = name, role = role, isActive = isActive)
                } else {
                    val salt = PasswordHasher.generateSalt()
                    val hash = PasswordHasher.hash(password, salt)
                    existing.copy(name = name, role = role, salt = salt, passwordHash = hash, isActive = isActive)
                }
                db.userDao().update(updated)
                AuditLogger.log(requireContext(), session.userId(), "UPDATE", "user", existing.id, "username=${existing.username}")
            }
            withContext(Dispatchers.Main) {
                refreshData()
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
            .setMessage("Apakah Anda yakin ingin menghapus pengguna '${user.name}'?")
            .setPositiveButton("Hapus") { _, _ ->
                deleteUser(user)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteUser(user: UserEntity) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            db.userDao().delete(user)
            AuditLogger.log(requireContext(), session.userId(), "DELETE", "user", user.id, "username=${user.username}")
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Pengguna berhasil dihapus", Toast.LENGTH_SHORT).show()
                refreshData()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class UserAdapter(
        private val items: List<UserEntity>, 
        val onEdit: (UserEntity) -> Unit,
        val onDelete: (UserEntity) -> Unit
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
            holder.b.chipStatus.text = if (item.isActive) "Aktif" else "Nonaktif"
            holder.b.chipStatus.setChipBackgroundColorResource(if (item.isActive) R.color.accent_teal_light else R.color.gray_200)
            holder.b.btnEdit.setOnClickListener { onEdit(item) }
            holder.b.btnDelete.setOnClickListener { onDelete(item) }
        }
        override fun getItemCount() = items.size
    }
}
