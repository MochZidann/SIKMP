package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
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
import com.example.myapplication.databinding.DialogCategoryFormBinding
import com.example.myapplication.databinding.DialogProductFormBinding
import com.example.myapplication.ui.adapters.KasirProductGridAdapter
import com.example.myapplication.ui.adapters.TwoLineAdapter
import com.example.myapplication.ui.adapters.TwoLineRow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) {
                AppDatabase.get(this@ProductManagementActivity).categoryDao().getAll().map { it.name }.sorted()
            }
            showProductFormInternal(existing, categories)
        }
    }

    private fun showProductFormInternal(existing: ProductEntity?, categories: List<String>) {
        val currentCategories = categories.toMutableSet()
        val b = DialogProductFormBinding.inflate(layoutInflater)
        b.txtTitle.text = if (existing == null) "Tambah Produk" else "Edit Produk"
        b.txtSubtitle.text = if (existing == null) "Masukkan data produk baru." else "Perbarui data produk."
        b.etName.setText(existing?.name.orEmpty())
        b.inputCategory.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, categories))
        b.inputCategory.setText(existing?.category.orEmpty(), false)
        b.etPrice.setText(existing?.price?.toString().orEmpty())
        b.etStock.setText(existing?.stock?.toString().orEmpty())
        b.etStock.isEnabled = existing == null
        b.stockLayout.isEnabled = existing == null

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(b.root)
            .setCancelable(true)
            .show()

        b.btnQuickAddCategory.setOnClickListener {
            showQuickAddCategory { newName ->
                currentCategories.add(newName)
                val next = currentCategories.toList().sorted()
                b.inputCategory.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, next))
                b.inputCategory.setText(newName, false)
                b.categoryLayout.error = null
            }
        }

        b.btnCancel.setOnClickListener { dialog.dismiss() }
        b.btnSave.setOnClickListener {
            val name = b.etName.text?.toString()?.trim().orEmpty()
            val category = b.inputCategory.text?.toString()?.trim().orEmpty()
            val price = b.etPrice.text?.toString()?.trim()?.toLongOrNull()
            val stock = b.etStock.text?.toString()?.trim()?.toLongOrNull()

            b.nameLayout.error = null
            b.categoryLayout.error = null
            b.priceLayout.error = null
            b.stockLayout.error = null

            var ok = true
            if (name.isBlank()) {
                b.nameLayout.error = "Wajib diisi"
                ok = false
            }
            if (category.isBlank()) {
                b.categoryLayout.error = "Wajib diisi"
                ok = false
            } else if (currentCategories.isNotEmpty() && !currentCategories.contains(category)) {
                b.categoryLayout.error = "Pilih dari daftar atau tambah kategori"
                ok = false
            }
            if (price == null || price < 0L) {
                b.priceLayout.error = "Harga tidak valid"
                ok = false
            }
            if (existing == null && (stock == null || stock < 0L)) {
                b.stockLayout.error = "Stok awal tidak valid"
                ok = false
            }
            if (!ok) return@setOnClickListener

            dialog.dismiss()
            save(existing, name, category, price ?: 0L, (stock ?: 0L).coerceAtLeast(0))
        }
    }

    private fun showQuickAddCategory(onAdded: (String) -> Unit) {
        val b = DialogCategoryFormBinding.inflate(layoutInflater)
        b.txtTitle.text = "Tambah Kategori"
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(b.root)
            .setCancelable(true)
            .show()
        b.btnCancel.setOnClickListener { dialog.dismiss() }
        b.btnSave.setOnClickListener {
            val name = b.etName.text?.toString()?.trim().orEmpty()
            b.nameLayout.error = null
            if (name.isBlank()) {
                b.nameLayout.error = "Wajib diisi"
                return@setOnClickListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.get(this@ProductManagementActivity)
                val id = try {
                    db.categoryDao().insert(com.example.myapplication.data.db.CategoryEntity(name = name))
                } catch (_: Exception) {
                    -1L
                }
                withContext(Dispatchers.Main) {
                    if (id <= 0L) {
                        Toast.makeText(this@ProductManagementActivity, "Kategori sudah ada", Toast.LENGTH_SHORT).show()
                    } else {
                        AuditLogger.log(
                            context = this@ProductManagementActivity,
                            userId = session.userId(),
                            action = "CREATE",
                            entity = "category",
                            entityId = id,
                            detail = "Tambah kategori: $name"
                        )
                        onAdded(name)
                        dialog.dismiss()
                    }
                }
            }
        }
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
