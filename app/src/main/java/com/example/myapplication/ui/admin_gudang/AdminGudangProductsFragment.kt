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
import com.example.myapplication.databinding.FragmentAdminGudangListBinding
import com.example.myapplication.ui.UiFormat
import com.example.myapplication.ui.adapters.TwoLineAdapter
import com.example.myapplication.ui.adapters.TwoLineRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminGudangProductsFragment : Fragment() {
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
        binding.fab.setOnClickListener { showProductForm(null) }
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
                    subtitle = "${it.category} • ${UiFormat.money(it.price)} • Stok: ${it.stock}"
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
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus produk?")
            .setMessage(product.name)
            .setPositiveButton("Hapus") { _, _ -> delete(product) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun delete(product: ProductEntity) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            db.productDao().delete(product)
            AuditLogger.log(
                context = requireContext(),
                userId = SessionManager(requireContext()).userId(),
                action = "DELETE",
                entity = "product",
                entityId = product.id,
                detail = product.name
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Terhapus", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    private fun showProductForm(existing: ProductEntity?) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(requireContext()).apply {
            hint = "Nama Produk"
            setText(existing?.name.orEmpty())
        }
        val categoryInput = EditText(requireContext()).apply {
            hint = "Kategori"
            setText(existing?.category.orEmpty())
        }
        val priceInput = EditText(requireContext()).apply {
            hint = "Harga (Rp)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(existing?.price?.toString().orEmpty())
        }
        val stockInput = EditText(requireContext()).apply {
            hint = "Stok awal"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(existing?.stock?.toString().orEmpty())
            isEnabled = existing == null
        }
        container.addView(nameInput)
        container.addView(categoryInput)
        container.addView(priceInput)
        container.addView(stockInput)

        AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) "Tambah Produk" else "Edit Produk")
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val category = categoryInput.text?.toString()?.trim().orEmpty()
                val price = priceInput.text?.toString()?.trim()?.toLongOrNull() ?: -1
                val stock = stockInput.text?.toString()?.trim()?.toLongOrNull() ?: -1
                if (name.isBlank() || category.isBlank() || price < 0 || (existing == null && stock < 0)) {
                    Toast.makeText(requireContext(), "Data belum lengkap", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                save(existing, name, category, price, stock.coerceAtLeast(0))
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun save(existing: ProductEntity?, name: String, category: String, price: Long, stock: Long) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            if (existing == null) {
                val id = db.productDao().insert(ProductEntity(name = name, category = category, price = price, stock = stock))
                AuditLogger.log(
                    context = requireContext(),
                    userId = SessionManager(requireContext()).userId(),
                    action = "CREATE",
                    entity = "product",
                    entityId = id,
                    detail = name
                )
            } else {
                db.productDao().update(existing.copy(name = name, category = category, price = price))
                AuditLogger.log(
                    context = requireContext(),
                    userId = SessionManager(requireContext()).userId(),
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
