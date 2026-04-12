package com.example.myapplication.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.MemberEntity
import com.example.myapplication.data.model.Role
import com.example.myapplication.databinding.ActivitySimpleListBinding
import com.example.myapplication.ui.adapters.TwoLineAdapter
import com.example.myapplication.ui.adapters.TwoLineRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemberManagementActivity : BaseAuthedActivity() {
    private lateinit var binding: ActivitySimpleListBinding
    private val adapter = TwoLineAdapter { row -> onMemberClicked(row.id) }

    override fun allowedRoles(): Set<Role> = setOf(Role.ADMIN_SISTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.title = "Data Anggota"
        binding.recycler.adapter = adapter
        binding.fab.setOnClickListener { showMemberForm(null) }
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
            val members = AppDatabase.get(this@MemberManagementActivity).memberDao().getAll()
            val rows = members.map {
                TwoLineRow(
                    id = it.id,
                    title = "${it.name} (${it.memberNo})",
                    subtitle = listOfNotNull(it.phone, it.address).joinToString(" • ")
                )
            }
            withContext(Dispatchers.Main) {
                adapter.submit(rows)
            }
        }
    }

    private fun onMemberClicked(memberId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@MemberManagementActivity)
            val member = db.memberDao().findById(memberId) ?: return@launch
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@MemberManagementActivity)
                    .setTitle(member.name)
                    .setItems(arrayOf("Edit", "Hapus")) { _, which ->
                        when (which) {
                            0 -> showMemberForm(member)
                            1 -> confirmDelete(member)
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
    }

    private fun confirmDelete(member: MemberEntity) {
        AlertDialog.Builder(this)
            .setTitle("Hapus anggota?")
            .setMessage("${member.name} (${member.memberNo})")
            .setPositiveButton("Hapus") { _, _ -> deleteMember(member) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteMember(member: MemberEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@MemberManagementActivity)
            db.memberDao().delete(member)
            AuditLogger.log(
                context = this@MemberManagementActivity,
                userId = session.userId(),
                action = "DELETE",
                entity = "member",
                entityId = member.id,
                detail = "memberNo=${member.memberNo}"
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MemberManagementActivity, "Terhapus", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    private fun showMemberForm(existing: MemberEntity?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val memberNoInput = EditText(this).apply {
            hint = "No Anggota"
            setText(existing?.memberNo.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val nameInput = EditText(this).apply {
            hint = "Nama"
            setText(existing?.name.orEmpty())
        }
        val phoneInput = EditText(this).apply {
            hint = "Telepon (opsional)"
            setText(existing?.phone.orEmpty())
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val addressInput = EditText(this).apply {
            hint = "Alamat (opsional)"
            setText(existing?.address.orEmpty())
        }

        container.addView(memberNoInput)
        container.addView(nameInput)
        container.addView(phoneInput)
        container.addView(addressInput)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Tambah Anggota" else "Edit Anggota")
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                val memberNo = memberNoInput.text?.toString()?.trim().orEmpty()
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val phone = phoneInput.text?.toString()?.trim().orEmpty().ifBlank { null }
                val address = addressInput.text?.toString()?.trim().orEmpty().ifBlank { null }
                if (memberNo.isBlank() || name.isBlank()) {
                    Toast.makeText(this, "Data belum lengkap", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveMember(existing, memberNo, name, phone, address)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun saveMember(existing: MemberEntity?, memberNo: String, name: String, phone: String?, address: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@MemberManagementActivity)
            if (existing == null) {
                val id = db.memberDao().insert(
                    MemberEntity(
                        memberNo = memberNo,
                        name = name,
                        phone = phone,
                        address = address
                    )
                )
                AuditLogger.log(
                    context = this@MemberManagementActivity,
                    userId = session.userId(),
                    action = "CREATE",
                    entity = "member",
                    entityId = id,
                    detail = "memberNo=$memberNo"
                )
            } else {
                db.memberDao().update(existing.copy(memberNo = memberNo, name = name, phone = phone, address = address))
                AuditLogger.log(
                    context = this@MemberManagementActivity,
                    userId = session.userId(),
                    action = "UPDATE",
                    entity = "member",
                    entityId = existing.id,
                    detail = "memberNo=$memberNo"
                )
            }
            withContext(Dispatchers.Main) {
                refresh()
            }
        }
    }
}
