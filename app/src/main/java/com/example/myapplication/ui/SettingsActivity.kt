package com.example.myapplication.ui

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.SettingsEntity
import com.example.myapplication.data.model.Role
import com.example.myapplication.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : BaseAuthedActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun allowedRoles(): Set<Role> = setOf(Role.ADMIN_SISTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnSave.setOnClickListener { save() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun load() {
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = AppDatabase.get(this@SettingsActivity).settingsDao().get() ?: SettingsEntity()
            withContext(Dispatchers.Main) {
                binding.tax.setText(settings.taxPercent.toString())
                binding.discount.setText(settings.discountPercent.toString())
                binding.shu.setText(settings.shuParameter.toString())
            }
        }
    }

    private fun save() {
        val tax = binding.tax.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
        val discount = binding.discount.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
        val shu = binding.shu.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@SettingsActivity)
            db.settingsDao().upsert(SettingsEntity(taxPercent = tax, discountPercent = discount, shuParameter = shu))
            AuditLogger.log(
                context = this@SettingsActivity,
                userId = session.userId(),
                action = "UPDATE",
                entity = "settings",
                entityId = 1,
                detail = "tax=$tax discount=$discount shu=$shu"
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SettingsActivity, "Tersimpan", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
