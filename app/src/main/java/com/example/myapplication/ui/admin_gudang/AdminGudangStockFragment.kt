package com.example.myapplication.ui.admin_gudang

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.data.db.StockMovementEntity
import com.example.myapplication.databinding.FragmentAdminGudangStockBinding
import com.example.myapplication.databinding.ItemStockRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminGudangStockFragment : Fragment() {
    private var _binding: FragmentAdminGudangStockBinding? = null
    private val binding get() = _binding!!

    private var allProducts = listOf<ProductEntity>()
    private var query = ""
    private var filter = StockFilter.ALL

    private enum class StockFilter { ALL, LOW, OK }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangStockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())

        binding.etSearch.addTextChangedListener {
            query = it?.toString().orEmpty()
            performFilter()
        }

        binding.chipAll.setOnClickListener { filter = StockFilter.ALL; performFilter() }
        binding.chipLow.setOnClickListener { filter = StockFilter.LOW; performFilter() }
        binding.chipOk.setOnClickListener { filter = StockFilter.OK; performFilter() }

        // Initial filter from arguments
        arguments?.getString("filter")?.let {
            when(it) {
                "LOW" -> {
                    filter = StockFilter.LOW
                    binding.chipLow.isChecked = true
                }
                "OK" -> {
                    filter = StockFilter.OK
                    binding.chipOk.isChecked = true
                }
            }
        }

        refresh()
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { if (_binding != null) binding.progress.visibility = View.VISIBLE }
            val db = AppDatabase.get(requireContext())
            allProducts = db.productDao().getAll()
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.progress.visibility = View.GONE
                performFilter()
            }
        }
    }

    private fun performFilter() {
        val q = query.trim().lowercase()
        val filtered = allProducts.filter { p ->
            (q.isBlank() || p.name.lowercase().contains(q) || p.barcode?.lowercase()?.contains(q) == true) &&
            when (filter) {
                StockFilter.ALL -> true
                StockFilter.LOW -> p.stock <= p.minimumStock
                StockFilter.OK -> p.stock > p.minimumStock
            }
        }.sortedBy { it.name }

        binding.recycler.adapter = StockAdapter(filtered)
    }

    private fun showStockInDialog(product: ProductEntity) {
        val view = layoutInflater.inflate(com.example.myapplication.R.layout.dialog_simple_input, null)
        val etInput = view.findViewById<TextInputEditText>(com.example.myapplication.R.id.etInput)
        val layoutInput = view.findViewById<TextInputLayout>(com.example.myapplication.R.id.layoutInput)

        layoutInput.hint = "Jumlah Stok Masuk"
        etInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Stok Masuk: ${product.name}")
            .setMessage("Stok saat ini: ${product.stock}\nMinimum Stok (MOQ): ${product.minimumStock}")
            .setView(view)
            .setPositiveButton("Tambah") { _, _ ->
                val qty = etInput.text.toString().toLongOrNull()
                if (qty != null && qty > 0) {
                    updateStock(product, qty, "STOK_MASUK")
                } else {
                    Toast.makeText(requireContext(), "Jumlah tidak valid", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showAdjustDialog(product: ProductEntity) {
        val view = layoutInflater.inflate(com.example.myapplication.R.layout.dialog_simple_input, null)
        val etInput = view.findViewById<TextInputEditText>(com.example.myapplication.R.id.etInput)
        val layoutInput = view.findViewById<TextInputLayout>(com.example.myapplication.R.id.layoutInput)

        layoutInput.hint = "Set Stok Baru"
        etInput.setText(product.stock.toString())
        etInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Penyesuaian Stok: ${product.name}")
            .setView(view)
            .setPositiveButton("Simpan") { _, _ ->
                val newQty = etInput.text.toString().toLongOrNull()
                if (newQty != null && newQty >= 0) {
                    val delta = newQty - product.stock
                    updateStock(product, delta, "PENYESUAIAN")
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun updateStock(product: ProductEntity, delta: Long, type: String) {
        if (delta == 0L && type == "PENYESUAIAN") return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val newStock = product.stock + delta
            db.productDao().update(product.copy(stock = newStock))

            db.stockMovementDao().insert(StockMovementEntity(
                productId = product.id,
                userId = SessionManager(requireContext()).userId(),
                type = type,
                quantityDelta = delta,
                note = if (type == "STOK_MASUK") "Penambahan stok" else "Penyesuaian manual"
            ))

            AuditLogger.log(requireContext(), SessionManager(requireContext()).userId(), "UPDATE", "stock", product.id, "${product.name}: $delta ($type)")

            withContext(Dispatchers.Main) {
                refresh()
                Snackbar.make(binding.root, "Stok berhasil diperbarui", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class StockAdapter(private val items: List<ProductEntity>) : RecyclerView.Adapter<StockAdapter.VH>() {
        inner class VH(val b: ItemStockRowBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemStockRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.b.txtName.text = item.name
            holder.b.txtBarcode.text = item.barcode ?: "-"
            holder.b.txtCategory.text = item.category
            holder.b.txtStock.text = item.stock.toString()

            // Use minimumStock (MOQ) instead of hardcoded threshold
            if (item.stock <= item.minimumStock) {
                holder.b.txtStock.setTextColor(requireContext().getColor(com.example.myapplication.R.color.primary_red))
                holder.b.txtStock.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                holder.b.txtStock.setTextColor(requireContext().getColor(com.example.myapplication.R.color.gray_800))
                holder.b.txtStock.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            holder.b.btnStockIn.setOnClickListener { showStockInDialog(item) }
            holder.b.btnAdjust.setOnClickListener { showAdjustDialog(item) }
        }
        override fun getItemCount() = items.size
    }
}
