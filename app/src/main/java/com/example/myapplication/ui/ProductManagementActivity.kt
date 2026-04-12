package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.data.model.Role
import com.example.myapplication.databinding.ActivityProductsKasirBinding
import com.example.myapplication.databinding.ActivitySimpleListBinding
import com.example.myapplication.ui.adapters.KasirProductGridAdapter
import com.example.myapplication.ui.adapters.TwoLineAdapter
import com.example.myapplication.ui.adapters.TwoLineRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductManagementActivity : BaseAuthedActivity() {
    private lateinit var binding: ActivitySimpleListBinding
    private var kasirBinding: ActivityProductsKasirBinding? = null
    private val adapter = TwoLineAdapter { row -> onProductClicked(row.id) }
    private var kasirAdapter: KasirProductGridAdapter? = null
    private var kasirProducts: List<ProductEntity> = emptyList()
    private var kasirToggle: ActionBarDrawerToggle? = null

    override fun allowedRoles(): Set<Role> = setOf(Role.ADMIN_GUDANG, Role.KASIR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (session.role() == Role.KASIR) {
            val kb = ActivityProductsKasirBinding.inflate(layoutInflater)
            kasirBinding = kb
            setContentView(kb.root)
            setSupportActionBar(kb.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            setupKasirDrawer(kb)
            kasirAdapter = KasirProductGridAdapter { }
            kb.recycler.adapter = kasirAdapter
            kb.tabs.addTab(kb.tabs.newTab().setText("Semua Produk"))
            kb.tabs.addTab(kb.tabs.newTab().setText("Sembako"))
            kb.tabs.addTab(kb.tabs.newTab().setText("Pupuk"))
            kb.tabs.addTab(kb.tabs.newTab().setText("Lainnya"))
            kb.tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                    applyKasirFilter()
                }

                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                }

                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                }
            })
            kb.search.doAfterTextChanged { applyKasirFilter() }
            return
        }
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.title = "Kelola Produk"

        binding.recycler.adapter = adapter
        binding.fab.setOnClickListener { showProductForm(null) }
    }

    override fun onResume() {
        super.onResume()
        if (kasirBinding != null) {
            refreshKasir()
        } else {
            refresh()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val kb = kasirBinding
        if (kb != null) {
            if (kb.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                kb.drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
            return super.onSupportNavigateUp()
        }
        finish()
        return true
    }

    private fun setupKasirDrawer(kb: ActivityProductsKasirBinding) {
        val header = kb.navigationView.getHeaderView(0)
        header.findViewById<TextView>(R.id.title)?.text = getString(R.string.app_name)
        header.findViewById<TextView>(R.id.subtitle)?.text =
            listOfNotNull(session.name(), session.username()).joinToString(" • ").ifBlank { "Kasir" }

        kasirToggle = ActionBarDrawerToggle(
            this,
            kb.drawerLayout,
            kb.toolbar,
            R.string.app_name,
            R.string.app_name
        )
        kb.drawerLayout.addDrawerListener(kasirToggle!!)
        kasirToggle!!.syncState()
        kasirToggle!!.drawerArrowDrawable.color = ContextCompat.getColor(this, android.R.color.white)

        kb.navigationView.setCheckedItem(R.id.nav_kasir_products)
        kb.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_kasir_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_kasir_pos -> {
                    startActivity(Intent(this, PosActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_kasir_reports -> {
                    startActivity(Intent(this, ReportsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_kasir_products -> true
                R.id.nav_kasir_logout -> {
                    session.clear()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }.also {
                kb.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (kb.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        kb.drawerLayout.closeDrawer(GravityCompat.START)
                        return
                    }
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        )
    }

    private fun refresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            val products = AppDatabase.get(this@ProductManagementActivity).productDao().getAll()
            val rows = products.map {
                TwoLineRow(
                    id = it.id,
                    title = it.name,
                    subtitle = "${it.category} • ${UiFormat.money(it.price)} • Stok: ${it.stock}"
                )
            }
            withContext(Dispatchers.Main) { adapter.submit(rows) }
        }
    }

    private fun refreshKasir() {
        lifecycleScope.launch(Dispatchers.IO) {
            val products = AppDatabase.get(this@ProductManagementActivity).productDao().getAll()
            withContext(Dispatchers.Main) {
                kasirProducts = products
                applyKasirFilter()
            }
        }
    }

    private fun applyKasirFilter() {
        val kb = kasirBinding ?: return
        val query = kb.search.text?.toString()?.trim().orEmpty().lowercase()
        val selected = kb.tabs.getTabAt(kb.tabs.selectedTabPosition)?.text?.toString().orEmpty()
        val filtered = kasirProducts.filter { p ->
            val matchText = query.isBlank() || p.name.lowercase().contains(query) || p.category.lowercase().contains(query)
            val matchCategory = selected == "Semua Produk" || selected.isBlank() || p.category.equals(selected, ignoreCase = true)
            matchText && matchCategory
        }.sortedBy { it.name }
        kasirAdapter?.submit(filtered)
    }

    private fun onProductClicked(productId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@ProductManagementActivity)
            val product = db.productDao().findById(productId) ?: return@launch
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@ProductManagementActivity)
                    .setTitle(product.name)
                    .setItems(arrayOf("Edit", "Hapus")) { _, which ->
                        when (which) {
                            0 -> showProductForm(product)
                            1 -> confirmDelete(product)
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
    }

    private fun confirmDelete(product: ProductEntity) {
        AlertDialog.Builder(this)
            .setTitle("Hapus produk?")
            .setMessage(product.name)
            .setPositiveButton("Hapus") { _, _ -> delete(product) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun delete(product: ProductEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@ProductManagementActivity)
            db.productDao().delete(product)
            AuditLogger.log(
                context = this@ProductManagementActivity,
                userId = session.userId(),
                action = "DELETE",
                entity = "product",
                entityId = product.id,
                detail = product.name
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ProductManagementActivity, "Terhapus", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    private fun showProductForm(existing: ProductEntity?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(this).apply {
            hint = "Nama Produk"
            setText(existing?.name.orEmpty())
        }
        val categoryInput = EditText(this).apply {
            hint = "Kategori"
            setText(existing?.category.orEmpty())
        }
        val priceInput = EditText(this).apply {
            hint = "Harga (Rp)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(existing?.price?.toString().orEmpty())
        }
        val stockInput = EditText(this).apply {
            hint = "Stok awal"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(existing?.stock?.toString().orEmpty())
            isEnabled = existing == null
        }

        container.addView(nameInput)
        container.addView(categoryInput)
        container.addView(priceInput)
        container.addView(stockInput)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Tambah Produk" else "Edit Produk")
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val category = categoryInput.text?.toString()?.trim().orEmpty()
                val price = priceInput.text?.toString()?.trim()?.toLongOrNull() ?: -1
                val stock = stockInput.text?.toString()?.trim()?.toLongOrNull() ?: -1
                if (name.isBlank() || category.isBlank() || price < 0 || (existing == null && stock < 0)) {
                    Toast.makeText(this, "Data belum lengkap", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                save(existing, name, category, price, stock.coerceAtLeast(0))
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun save(existing: ProductEntity?, name: String, category: String, price: Long, stock: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@ProductManagementActivity)
            if (existing == null) {
                val id = db.productDao().insert(ProductEntity(name = name, category = category, price = price, stock = stock))
                AuditLogger.log(
                    context = this@ProductManagementActivity,
                    userId = session.userId(),
                    action = "CREATE",
                    entity = "product",
                    entityId = id,
                    detail = name
                )
            } else {
                db.productDao().update(existing.copy(name = name, category = category, price = price))
                AuditLogger.log(
                    context = this@ProductManagementActivity,
                    userId = session.userId(),
                    action = "UPDATE",
                    entity = "product",
                    entityId = existing.id,
                    detail = name
                )
            }
            withContext(Dispatchers.Main) { refresh() }
        }
    }
}
