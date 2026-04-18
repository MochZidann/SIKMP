package com.example.myapplication.ui.admin_gudang

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.databinding.DialogCategoryFormBinding
import com.example.myapplication.databinding.DialogProductFormBinding
import com.example.myapplication.databinding.FragmentAdminGudangProductsBinding
import com.example.myapplication.ui.UiFormat
import com.example.myapplication.ui.adapters.TwoLineAdapter
import com.example.myapplication.ui.adapters.TwoLineRow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminGudangProductsFragment : Fragment() {
    private var _binding: FragmentAdminGudangProductsBinding? = null
    private val binding get() = _binding!!

    private val adapter = TwoLineAdapter { row -> onProductClicked(row.id) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangProductsBinding.inflate(inflater, container, false)
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
                    subtitle = "${it.category} â€¢ ${UiFormat.money(it.price)} â€¢ Stok: ${it.stock}"
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
        viewLifecycleOwner.lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) {
                AppDatabase.get(requireContext()).categoryDao().getAll().map { it.name }.sorted()
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
        b.inputCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories))
        b.inputCategory.setText(existing?.category.orEmpty(), false)
        b.etPrice.setText(existing?.price?.toString().orEmpty())
        b.etStock.setText(existing?.stock?.toString().orEmpty())
        b.etStock.isEnabled = existing == null
        b.stockLayout.isEnabled = existing == null

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(b.root)
            .setCancelable(true)
            .show()

        b.btnQuickAddCategory.setOnClickListener {
            showQuickAddCategory { newName ->
                currentCategories.add(newName)
                val next = currentCategories.toList().sorted()
                b.inputCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, next))
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
        val dialog = MaterialAlertDialogBuilder(requireContext())
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
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.get(requireContext())
                val id = try {
                    db.categoryDao().insert(com.example.myapplication.data.db.CategoryEntity(name = name))
                } catch (_: Exception) {
                    -1L
                }
                withContext(Dispatchers.Main) {
                    if (id <= 0L) {
                        Toast.makeText(requireContext(), "Kategori sudah ada", Toast.LENGTH_SHORT).show()
                    } else {
                        AuditLogger.log(
                            context = requireContext(),
                            userId = SessionManager(requireContext()).userId(),
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


