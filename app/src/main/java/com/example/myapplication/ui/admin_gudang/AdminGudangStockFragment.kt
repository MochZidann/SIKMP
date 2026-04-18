package com.example.myapplication.ui.admin_gudang

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.data.db.StockMovementEntity
import com.example.myapplication.databinding.FragmentAdminGudangStockBinding
import com.example.myapplication.ui.adapters.TwoLineAdapter
import com.example.myapplication.ui.adapters.TwoLineRow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminGudangStockFragment : Fragment() {
    private var _binding: FragmentAdminGudangStockBinding? = null
    private val binding get() = _binding!!

    private val adapter = TwoLineAdapter { row -> onProductClicked(row.id) }
    private var allProducts: List<ProductEntity> = emptyList()
    private var query: String = ""
    private var filter: StockFilter = StockFilter.ALL
    private val lowStockThreshold = 5L

    private enum class StockFilter { ALL, LOW, OK }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangStockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recycler.adapter = adapter

        binding.etSearch.addTextChangedListener {
            query = it?.toString().orEmpty()
            render()
        }

        binding.chipAll.setOnClickListener {
            filter = StockFilter.ALL
            render()
        }
        binding.chipLow.setOnClickListener {
            filter = StockFilter.LOW
            render()
        }
        binding.chipOk.setOnClickListener {
            filter = StockFilter.OK
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentDialog?.dismiss()
        currentDialog = null
        _binding = null
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { setLoading(true) }
            val products = AppDatabase.get(requireContext()).productDao().getAll()
            allProducts = products
            withContext(Dispatchers.Main) {
                render()
                setLoading(false)
            }
        }
    }

    private fun onProductClicked(productId: Long) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
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
        currentDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(v)
            .setCancelable(true)
            .show()
    }

    private var currentDialog: android.app.Dialog? = null

    private fun showStockInDialog(product: ProductEntity) {
        val v = layoutInflater.inflate(com.example.myapplication.R.layout.bottomsheet_stock_in, null)
        v.findViewById<TextView>(com.example.myapplication.R.id.title)?.text = "Stok Masuk â€¢ ${product.name}"
        v.findViewById<TextView>(com.example.myapplication.R.id.subtitle)?.text = "Stok saat ini: ${product.stock}"
        val qtyLayout = v.findViewById<com.google.android.material.textfield.TextInputLayout>(com.example.myapplication.R.id.qtyLayout)
        val etQty = v.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.myapplication.R.id.etQty)
        val etNote = v.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.myapplication.R.id.etNote)
        v.findViewById<View>(com.example.myapplication.R.id.btnSave)?.setOnClickListener {
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
        currentDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(v)
            .setCancelable(true)
            .show()
    }

    private fun showAdjustDialog(product: ProductEntity) {
        val v = layoutInflater.inflate(com.example.myapplication.R.layout.bottomsheet_stock_adjust, null)
        v.findViewById<TextView>(com.example.myapplication.R.id.title)?.text = "Penyesuaian Stok â€¢ ${product.name}"
        v.findViewById<TextView>(com.example.myapplication.R.id.txtInfo)?.text = "Stok saat ini: ${product.stock}"
        val newStockLayout = v.findViewById<com.google.android.material.textfield.TextInputLayout>(com.example.myapplication.R.id.newStockLayout)
        val etNewStock = v.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.myapplication.R.id.etNewStock)
        v.findViewById<View>(com.example.myapplication.R.id.btnSave)?.setOnClickListener {
            val newStock = etNewStock?.text?.toString()?.trim()?.toLongOrNull()
            if (newStock == null || newStock < 0L) {
                newStockLayout?.error = "Masukkan angka 0 atau lebih"
                return@setOnClickListener
            }
            newStockLayout?.error = null
            currentDialog?.dismiss()
            applyStockOverwrite(productId = product.id, newStock = newStock)
        }
        currentDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(v)
            .setCancelable(true)
            .show()
    }

    private fun applyStockIn(productId: Long, qty: Long, note: String?) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { setLoading(true) }
            val db = AppDatabase.get(requireContext())
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
                    userId = SessionManager(requireContext()).userId(),
                    type = "STOK_MASUK",
                    quantityDelta = qty,
                    note = note
                )
            )
            AuditLogger.log(
                context = requireContext(),
                userId = SessionManager(requireContext()).userId(),
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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { setLoading(true) }
            val db = AppDatabase.get(requireContext())
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
                    userId = SessionManager(requireContext()).userId(),
                    type = "PENYESUAIAN",
                    quantityDelta = delta,
                    note = null
                )
            )
            AuditLogger.log(
                context = requireContext(),
                userId = SessionManager(requireContext()).userId(),
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
        val filtered = allProducts
            .asSequence()
            .filter { p ->
                q.isBlank() || p.name.lowercase().contains(q) || p.category.lowercase().contains(q)
            }
            .filter { p ->
                when (filter) {
                    StockFilter.ALL -> true
                    StockFilter.LOW -> p.stock <= lowStockThreshold
                    StockFilter.OK -> p.stock > lowStockThreshold
                }
            }
            .sortedBy { it.name }
            .toList()

        val rows = filtered.map { p ->
            val status = if (p.stock <= lowStockThreshold) "Menipis" else "Aman"
            TwoLineRow(
                id = p.id,
                title = p.name,
                subtitle = "Stok: ${p.stock} ($status) â€¢ ${p.category}"
            )
        }

        adapter.submit(rows)
        val empty = rows.isEmpty()
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

