package com.example.myapplication.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.data.db.ProductLastMovement
import com.example.myapplication.data.db.StockMovementEntity
import com.example.myapplication.data.model.Role
import com.example.myapplication.databinding.ActivityStockManagementBinding
import com.example.myapplication.ui.adapters.StockProductAdapter
import com.example.myapplication.ui.adapters.StockProductRow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StockManagementActivity : BaseAuthedActivity() {
    private lateinit var binding: ActivityStockManagementBinding
    private val adapter = StockProductAdapter { row -> onProductClicked(row.id) }
    private var allRows: List<StockProductRow> = emptyList()
    private var query: String = ""
    private var filter: StockFilter = StockFilter.ALL
    private var currentDialog: android.app.Dialog? = null

    private enum class StockFilter { ALL, OK, LOW, OUT }

    override fun allowedRoles(): Set<Role> = setOf(Role.ADMIN_GUDANG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.title = "Kelola Stok"
        binding.recycler.adapter = adapter

        binding.etSearch.addTextChangedListener {
            query = it?.toString().orEmpty()
            render()
        }
        binding.chipAll.setOnClickListener { filter = StockFilter.ALL; render() }
        binding.chipOk.setOnClickListener { filter = StockFilter.OK; render() }
        binding.chipLow.setOnClickListener { filter = StockFilter.LOW; render() }
        binding.chipOut.setOnClickListener { filter = StockFilter.OUT; render() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentDialog?.dismiss()
        currentDialog = null
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { setLoading(true) }
            val db = AppDatabase.get(this@StockManagementActivity)
            val products = db.productDao().getAll()
            val lastMovements = db.stockMovementDao()
                .latestByProductIds(products.map { it.id })
                .associateBy({ it.productId }, { it.lastCreatedAtEpochMs })
            allRows = products.map { p ->
                StockProductRow(
                    id = p.id,
                    name = p.name,
                    category = p.category,
                    stock = p.stock,
                    lastUpdateEpochMs = lastMovements[p.id]
                )
            }
            withContext(Dispatchers.Main) {
                render()
                setLoading(false)
            }
        }
    }

    private fun onProductClicked(productId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@StockManagementActivity)
            val product = db.productDao().findById(productId) ?: return@launch
            withContext(Dispatchers.Main) {
                showActionsDialog(product)
            }
        }
    }

    private fun showActionsDialog(product: ProductEntity) {
        val v = layoutInflater.inflate(com.example.myapplication.R.layout.bottomsheet_stock_actions, null)
        v.findViewById<TextView>(com.example.myapplication.R.id.title)?.text = product.name
        v.findViewById<View>(com.example.myapplication.R.id.actionStockIn)?.setOnClickListener {
            currentDialog?.dismiss()
            showStockInDialog(product)
        }
        v.findViewById<View>(com.example.myapplication.R.id.actionAdjust)?.setOnClickListener {
            currentDialog?.dismiss()
            showAdjustDialog(product)
        }
        currentDialog = MaterialAlertDialogBuilder(this)
            .setView(v)
            .setCancelable(true)
            .show()
    }

    private fun showStockInDialog(product: ProductEntity) {
        val dialogView = layoutInflater.inflate(com.example.myapplication.R.layout.bottomsheet_stock_in, null)
        dialogView.findViewById<TextView>(com.example.myapplication.R.id.title)?.text = "Stok Masuk • ${product.name}"
        dialogView.findViewById<TextView>(com.example.myapplication.R.id.subtitle)?.text = "Stok saat ini: ${product.stock}"
        val qtyLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(com.example.myapplication.R.id.qtyLayout)
        val etQty = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.myapplication.R.id.etQty)
        val etNote = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.myapplication.R.id.etNote)
        dialogView.findViewById<View>(com.example.myapplication.R.id.btnSave)?.setOnClickListener {
            val qty = etQty?.text?.toString()?.trim()?.toLongOrNull()
            if (qty == null || qty <= 0L) {
                qtyLayout?.error = "Masukkan angka > 0"
                return@setOnClickListener
            }
            qtyLayout?.error = null
            currentDialog?.dismiss()
            val note = etNote?.text?.toString()?.trim().orEmpty().ifBlank { null }
            applyStockIn(productId = product.id, qty = qty, note = note)
        }
        currentDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .show()
    }

    private fun showAdjustDialog(product: ProductEntity) {
        val dialogView = layoutInflater.inflate(com.example.myapplication.R.layout.bottomsheet_stock_adjust, null)
        dialogView.findViewById<TextView>(com.example.myapplication.R.id.title)?.text = "Penyesuaian Stok • ${product.name}"
        dialogView.findViewById<TextView>(com.example.myapplication.R.id.txtInfo)?.text = "Stok saat ini: ${product.stock}"
        val newStockLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(com.example.myapplication.R.id.newStockLayout)
        val etNewStock = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.myapplication.R.id.etNewStock)
        dialogView.findViewById<View>(com.example.myapplication.R.id.btnSave)?.setOnClickListener {
            val newStock = etNewStock?.text?.toString()?.trim()?.toLongOrNull()
            if (newStock == null || newStock < 0L) {
                newStockLayout?.error = "Masukkan angka 0 atau lebih"
                return@setOnClickListener
            }
            newStockLayout?.error = null
            currentDialog?.dismiss()
            applyStockOverwrite(productId = product.id, newStock = newStock)
        }
        currentDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .show()
    }

    private fun applyStockIn(productId: Long, qty: Long, note: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { setLoading(true) }
            val db = AppDatabase.get(this@StockManagementActivity)
            val product = db.productDao().findById(productId)
            if (product == null) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Snackbar.make(binding.root, "Produk tidak ditemukan", Snackbar.LENGTH_SHORT).show()
                }
                return@launch
            }
            val newStock = product.stock + qty
            db.productDao().update(product.copy(stock = newStock))
            val movementId = db.stockMovementDao().insert(
                StockMovementEntity(
                    productId = productId,
                    userId = session.userId(),
                    type = "STOK_MASUK",
                    quantityDelta = qty,
                    note = note
                )
            )
            AuditLogger.log(
                context = this@StockManagementActivity,
                userId = session.userId(),
                action = "UPDATE",
                entity = "stock",
                entityId = movementId,
                detail = "Stok masuk untuk produk ${product.name}: +$qty (dari ${product.stock} menjadi $newStock)"
            )
            withContext(Dispatchers.Main) {
                setLoading(false)
                Snackbar.make(binding.root, "Stok diperbarui", Snackbar.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    private fun applyStockOverwrite(productId: Long, newStock: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { setLoading(true) }
            val db = AppDatabase.get(this@StockManagementActivity)
            val product = db.productDao().findById(productId)
            if (product == null) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Snackbar.make(binding.root, "Produk tidak ditemukan", Snackbar.LENGTH_SHORT).show()
                }
                return@launch
            }
            val oldStock = product.stock
            if (newStock == oldStock) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Snackbar.make(binding.root, "Tidak ada perubahan stok", Snackbar.LENGTH_SHORT).show()
                }
                return@launch
            }
            val delta = newStock - oldStock
            db.productDao().updateStockAbsolute(productId = productId, stock = newStock)
            db.stockMovementDao().insert(
                StockMovementEntity(
                    productId = productId,
                    userId = session.userId(),
                    type = "PENYESUAIAN",
                    quantityDelta = delta,
                    note = null
                )
            )
            AuditLogger.log(
                context = this@StockManagementActivity,
                userId = session.userId(),
                action = "UPDATE",
                entity = "product_stock",
                entityId = productId,
                detail = "Penyesuaian stok manual untuk produk ${product.name}: dari $oldStock menjadi $newStock"
            )
            withContext(Dispatchers.Main) {
                setLoading(false)
                Snackbar.make(binding.root, "Stok diperbarui", Snackbar.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    private fun render() {
        val q = query.trim().lowercase()
        val filtered = allRows.asSequence()
            .filter { r ->
                q.isBlank() || r.name.lowercase().contains(q) || r.category.lowercase().contains(q)
            }
            .filter { r ->
                when (filter) {
                    StockFilter.ALL -> true
                    StockFilter.OK -> r.stock > 5L
                    StockFilter.LOW -> r.stock in 1L..5L
                    StockFilter.OUT -> r.stock <= 0L
                }
            }
            .sortedBy { it.name }
            .toList()

        adapter.submit(filtered)
        val empty = filtered.isEmpty()
        binding.recycler.visibility = if (empty) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.txtEmpty.text = if (q.isBlank()) "Tidak ada data" else "Tidak ditemukan"
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progress.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.recycler.isEnabled = !isLoading
        binding.searchLayout.isEnabled = !isLoading
        binding.chipGroup.isEnabled = !isLoading
    }
}
