package com.example.myapplication.ui

import com.example.myapplication.ui.owner.OwnerDashboardFragment
import com.example.myapplication.ui.kasir.KasirReportsFragment
import com.example.myapplication.ui.kasir.KasirProductsFragment
import com.example.myapplication.ui.kasir.KasirPosFragment
import com.example.myapplication.ui.kasir.KasirDashboardFragment
import com.example.myapplication.ui.admin.UserManagementFragment
import com.example.myapplication.ui.admin.PromoConfigFragment
import com.example.myapplication.ui.admin.MemberManagementFragment
import com.example.myapplication.ui.admin.KoperasiProfileFragment
import com.example.myapplication.ui.admin.DatabaseManagementFragment
import com.example.myapplication.ui.admin.AuditLogFragment
import com.example.myapplication.ui.admin.AdminDashboardFragment
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.model.Role
import com.example.myapplication.databinding.ActivityDashboardBinding
import com.example.myapplication.ui.owner.OwnerStockReportFragment
import com.google.android.material.navigation.NavigationView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DashboardActivity : BaseAuthedActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupNavigation()
        val role = session.role()
        applyRoleBranding(role)
        setupRoleMenu(role)
        
        if (savedInstanceState == null) {
            val (defaultMenuId, defaultFragment) = defaultDestination(role)
            replaceFragment(defaultFragment, getString(R.string.nav_dashboard))
            binding.navigationView.setCheckedItem(defaultMenuId)
        }
    }

    private fun setupNavigation() {
        binding.txtUserName.text = session.name() ?: session.username() ?: "User"
        binding.chipRole.text = roleLabel(session.role())

        toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.btnHamburger.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navigationView.setNavigationItemSelectedListener(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupRoleMenu(role: Role?) {
        binding.navigationView.menu.clear()
        when (role) {
            Role.ADMIN_SISTEM -> binding.navigationView.inflateMenu(R.menu.drawer_admin)
            Role.ADMIN_GUDANG -> binding.navigationView.inflateMenu(R.menu.drawer_admin_gudang)
            Role.KASIR -> binding.navigationView.inflateMenu(R.menu.drawer_kasir)
            Role.OWNER_PENGAWAS -> binding.navigationView.inflateMenu(R.menu.drawer_owner)
            null -> binding.navigationView.inflateMenu(R.menu.drawer_admin)
        }
    }

    private fun defaultDestination(role: Role?): Pair<Int, Fragment> {
        return when (role) {
            Role.ADMIN_SISTEM -> R.id.nav_admin_dashboard to AdminDashboardFragment()
            Role.ADMIN_GUDANG -> R.id.nav_gudang_dashboard to com.example.myapplication.ui.admin_gudang.AdminGudangDashboardFragment()
            Role.KASIR -> R.id.nav_kasir_dashboard to KasirDashboardFragment()
            Role.OWNER_PENGAWAS -> R.id.nav_owner_dashboard to OwnerDashboardFragment()
            null -> R.id.nav_admin_dashboard to AdminDashboardFragment()
        }
    }

    fun navigateTo(menuId: Int) {
        val item = binding.navigationView.menu.findItem(menuId)
        if (item != null) {
            binding.navigationView.setCheckedItem(menuId)
            onNavigationItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val role = session.role()
        if (!isAllowedMenu(role, item.itemId)) {
            Toast.makeText(this, "Akses ditolak", Toast.LENGTH_SHORT).show()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return true
        }
        
        val title = item.title.toString()
        when (item.itemId) {
            R.id.nav_admin_dashboard -> replaceFragment(AdminDashboardFragment(), title)
            R.id.nav_admin_promo -> replaceFragment(PromoConfigFragment(), title)
            R.id.nav_admin_users -> replaceFragment(UserManagementFragment(), title)
            R.id.nav_admin_members -> replaceFragment(MemberManagementFragment(), title)
            R.id.nav_admin_profile -> replaceFragment(KoperasiProfileFragment(), title)
            R.id.nav_admin_logs -> replaceFragment(AuditLogFragment(), title)
            R.id.nav_admin_database -> replaceFragment(DatabaseManagementFragment(), title)
            R.id.nav_admin_logout -> logout()

            R.id.nav_gudang_dashboard -> replaceFragment(com.example.myapplication.ui.admin_gudang.AdminGudangDashboardFragment(), title)
            R.id.nav_gudang_products -> replaceFragment(com.example.myapplication.ui.admin_gudang.AdminGudangProductsFragment(), title)
            R.id.nav_gudang_categories -> replaceFragment(com.example.myapplication.ui.admin_gudang.AdminGudangCategoriesFragment(), title)
            R.id.nav_gudang_stock -> replaceFragment(com.example.myapplication.ui.admin_gudang.AdminGudangStockFragment(), title)
            R.id.nav_gudang_reports -> replaceFragment(com.example.myapplication.ui.admin_gudang.AdminGudangReportsFragment(), title)
            R.id.nav_gudang_logout -> logout()

            R.id.nav_kasir_dashboard -> replaceFragment(KasirDashboardFragment(), title)
            R.id.nav_kasir_pos -> replaceFragment(KasirPosFragment(), title)
            R.id.nav_kasir_reports -> replaceFragment(KasirReportsFragment(), title)
            R.id.nav_kasir_products -> replaceFragment(KasirProductsFragment(), title)
            R.id.nav_kasir_logout -> logout()

            R.id.nav_owner_dashboard -> replaceFragment(OwnerDashboardFragment(), title)
            R.id.nav_owner_stock_report -> replaceFragment(OwnerStockReportFragment(), title)
            R.id.nav_owner_sales_report -> replaceFragment(KasirReportsFragment(), title)
            R.id.nav_owner_logout -> logout()
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun isAllowedMenu(role: Role?, itemId: Int): Boolean {
        return when (role) {
            Role.ADMIN_SISTEM -> itemId == R.id.nav_admin_dashboard ||
                itemId == R.id.nav_admin_promo ||
                itemId == R.id.nav_admin_users ||
                itemId == R.id.nav_admin_members ||
                itemId == R.id.nav_admin_profile ||
                itemId == R.id.nav_admin_logs ||
                itemId == R.id.nav_admin_database ||
                itemId == R.id.nav_admin_logout

            Role.ADMIN_GUDANG -> itemId == R.id.nav_gudang_dashboard ||
                itemId == R.id.nav_gudang_products ||
                itemId == R.id.nav_gudang_categories ||
                itemId == R.id.nav_gudang_stock ||
                itemId == R.id.nav_gudang_reports ||
                itemId == R.id.nav_gudang_logout

            Role.KASIR -> itemId == R.id.nav_kasir_dashboard ||
                itemId == R.id.nav_kasir_pos ||
                itemId == R.id.nav_kasir_reports ||
                itemId == R.id.nav_kasir_products ||
                itemId == R.id.nav_kasir_logout

            Role.OWNER_PENGAWAS -> itemId == R.id.nav_owner_dashboard ||
                itemId == R.id.nav_owner_stock_report ||
                itemId == R.id.nav_owner_sales_report ||
                itemId == R.id.nav_owner_logout

            null -> false
        }
    }
    private fun replaceFragment(fragment: Fragment, title: String) {
        binding.txtToolbarTitle.text = title
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun logout() {
        val userId = session.userId()
        session.clear()
        val appContext = applicationContext
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            AuditLogger.log(appContext, userId, "LOGOUT", "session", null)
        }
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun roleLabel(role: Role?): String {
        return when (role) {
            Role.ADMIN_SISTEM -> "Admin Sistem"
            Role.ADMIN_GUDANG -> "Admin Gudang"
            Role.KASIR -> "Kasir"
            Role.OWNER_PENGAWAS -> "Owner/Pengawas"
            null -> "User"
        }
    }

    private fun applyRoleBranding(role: Role?) {
        val (accent, light) = roleColors(role)
        
        window.statusBarColor = accent
        
        binding.chipRole.text = roleLabel(role)
        binding.chipRole.chipBackgroundColor = ColorStateList.valueOf(accent)
        
        binding.imgLogo.setImageResource(R.drawable.sikmp_red)
        binding.headerAccent.setBackgroundColor(accent)
        
        val headerView = binding.navigationView.getHeaderView(0)
        headerView.findViewById<View>(R.id.navHeaderLayout)?.setBackgroundColor(accent)
        headerView.findViewById<android.widget.ImageView>(R.id.navLogo)?.setImageResource(R.drawable.sikmp_white)
        headerView.findViewById<android.widget.TextView>(R.id.navUserName)?.text = session.name() ?: session.username() ?: "User"
        headerView.findViewById<android.widget.TextView>(R.id.navUserRole)?.text = roleLabel(role)

        binding.navigationView.itemTextColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, ContextCompat.getColor(this, R.color.gray_800))
        )
        binding.navigationView.itemIconTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, ContextCompat.getColor(this, R.color.gray_600))
        )
        
        val shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(32f)
            .build()
        val materialShapeDrawable = MaterialShapeDrawable(shapeAppearanceModel).apply {
            fillColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(light, ContextCompat.getColor(this@DashboardActivity, android.R.color.transparent))
            )
        }
        
        val insetHorizontal = (12 * resources.displayMetrics.density).toInt()
        val insetVertical = (4 * resources.displayMetrics.density).toInt()
        binding.navigationView.itemBackground = InsetDrawable(materialShapeDrawable, insetHorizontal, insetVertical, insetHorizontal, insetVertical)
    }

    private fun roleColors(role: Role?): Pair<Int, Int> {
        return when (role) {
            Role.ADMIN_SISTEM -> ContextCompat.getColor(this, R.color.primary_red) to ContextCompat.getColor(this, R.color.primary_red_light)
            Role.ADMIN_GUDANG -> ContextCompat.getColor(this, R.color.accent_purple) to ContextCompat.getColor(this, R.color.accent_purple_light)
            Role.KASIR -> ContextCompat.getColor(this, R.color.accent_blue) to ContextCompat.getColor(this, R.color.accent_blue_light)
            Role.OWNER_PENGAWAS -> ContextCompat.getColor(this, R.color.primary_green) to ContextCompat.getColor(this, R.color.primary_green_light)
            null -> ContextCompat.getColor(this, R.color.primary_red) to ContextCompat.getColor(this, R.color.primary_red_light)
        }
    }
}
