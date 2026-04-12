package com.example.myapplication.ui.admin_gudang

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.model.Role
import com.example.myapplication.databinding.ActivityAdminGudangBinding
import com.example.myapplication.ui.BaseAuthedActivity
import com.example.myapplication.ui.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdminGudangActivity : BaseAuthedActivity() {
    private lateinit var binding: ActivityAdminGudangBinding
    private lateinit var toggle: ActionBarDrawerToggle

    override fun allowedRoles(): Set<Role> = setOf(Role.ADMIN_GUDANG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminGudangBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.app_name,
            R.string.app_name
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        toggle.drawerArrowDrawable.color = ContextCompat.getColor(this, android.R.color.white)

        val header = binding.navigationView.getHeaderView(0)
        header.findViewById<TextView>(R.id.title)?.text = getString(R.string.app_name)
        header.findViewById<TextView>(R.id.subtitle)?.text =
            listOfNotNull(session.name(), session.username()).joinToString(" • ").ifBlank { "Admin Gudang" }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                        return
                    }
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        )

        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_gudang_dashboard -> {
                    showDashboard()
                    true
                }
                R.id.nav_gudang_products -> {
                    showProducts()
                    true
                }
                R.id.nav_gudang_stock -> {
                    showStock()
                    true
                }
                R.id.nav_gudang_reports -> {
                    showReports()
                    true
                }
                R.id.nav_gudang_logout -> {
                    logout()
                    true
                }
                else -> false
            }.also {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        if (savedInstanceState == null) {
            binding.navigationView.setCheckedItem(R.id.nav_gudang_dashboard)
            showDashboard()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return true
        }
        return super.onSupportNavigateUp()
    }

    private fun showDashboard() {
        supportActionBar?.title = "Dashboard Admin Gudang"
        supportFragmentManager.beginTransaction()
            .replace(binding.content.id, AdminGudangDashboardFragment())
            .commit()
    }

    private fun showProducts() {
        supportActionBar?.title = "Kelola Produk"
        supportFragmentManager.beginTransaction()
            .replace(binding.content.id, AdminGudangProductsFragment())
            .commit()
    }

    private fun showStock() {
        supportActionBar?.title = "Kelola Stok"
        supportFragmentManager.beginTransaction()
            .replace(binding.content.id, AdminGudangStockFragment())
            .commit()
    }

    private fun showReports() {
        supportActionBar?.title = "Laporan"
        supportFragmentManager.beginTransaction()
            .replace(binding.content.id, AdminGudangReportsFragment())
            .commit()
    }

    private fun logout() {
        val userId = session.userId()
        session.clear()
        lifecycleScope.launch(Dispatchers.IO) {
            AuditLogger.log(
                context = this@AdminGudangActivity,
                userId = userId,
                action = "LOGOUT",
                entity = "session",
                entityId = null
            )
        }
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
