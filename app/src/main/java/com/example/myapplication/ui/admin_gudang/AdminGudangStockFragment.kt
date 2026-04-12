package com.example.myapplication.ui.admin_gudang

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.data.db.StockMovementEntity
import com.example.myapplication.databinding.FragmentAdminGudangListBinding
import com.example.myapplication.ui.adapters.TwoLineAdapter
import com.example.myapplication.ui.adapters.TwoLineRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminGudangStockFragment : Fragment() {
    private var _binding: FragmentAdminGudangListBinding? = null
    private val binding get() = _binding!!

    private val adapter = TwoLineAdapter { row -> onProductClicked(row.id) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recycler.adapter = adapter
        binding.fab.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val products = AppDatabase.get(requireContext()).productDao().getAll()
            val rows = products.map {
                TwoLineRow(
                    id = it.id,
                    title = it.name,
                    subtitle = "Stok: ${it.stock} • ${it.category}"
                )
            }
            withContext(Dispatchers.Main) { adapter.submit(rows) }
        }
    }

    private fun onProductClicked(productId: Long) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val product = db.productDao().findById(productId) ?: return@launch
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(requireContext())
                    .setTitle(product.name)
                    .setItems(arrayOf("Stok Masuk", "Penyesuaian")) { _, which ->
                        when (which) {
                            0 -> showStockDialog(product, "IN")
                            1 -> showStockDialog(product, "ADJUST")
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
    }

    private fun showStockDialog(product: ProductEntity, type: String) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val qtyInput = EditText(requireContext()).apply {
            hint = if (type == "IN") "Jumlah barang masuk" else "Perubahan stok (+/-)"
            inputType = InputType.TYPE_CLASS_NUMBER or if (type == "ADJUST") InputType.TYPE_NUMBER_FLAG_SIGNED else 0
        }
        val noteInput = EditText(requireContext()).apply {
            hint = "Keterangan (opsional)"
        }
        container.addView(qtyInput)
        container.addView(noteInput)

        AlertDialog.Builder(requireContext())
            .setTitle(if (type == "IN") "Stok Masuk" else "Penyesuaian Stok")
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                val qty = qtyInput.text?.toString()?.trim()?.toLongOrNull()
                val note = noteInput.text?.toString()?.trim().orEmpty().ifBlank { null }
                if (qty == null || qty == 0L || (type == "IN" && qty < 0)) {
                    Toast.makeText(requireContext(), "Jumlah tidak valid", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                applyStockChange(product, type, qty, note)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun applyStockChange(product: ProductEntity, type: String, qty: Long, note: String?) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val newStock = (product.stock + qty).coerceAtLeast(0)
            val delta = newStock - product.stock
            if (delta == 0L) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Tidak ada perubahan stok", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            db.productDao().update(product.copy(stock = newStock))
            val movementId = db.stockMovementDao().insert(
                StockMovementEntity(
                    productId = product.id,
                    userId = SessionManager(requireContext()).userId(),
                    type = if (type == "IN") "STOK_MASUK" else "PENYESUAIAN",
                    quantityDelta = delta,
                    note = note
                )
            )
            AuditLogger.log(
                context = requireContext(),
                userId = SessionManager(requireContext()).userId(),
                action = "UPDATE",
                entity = "stock",
                entityId = movementId,
                detail = "productId=${product.id} delta=$delta"
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Stok diperbarui", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }
}
