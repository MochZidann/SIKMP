package com.example.myapplication.ui.admin_gudang

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.databinding.DialogProductFormBinding
import com.example.myapplication.databinding.FragmentAdminGudangProductsBinding
import com.example.myapplication.databinding.ItemProductTableRowBinding
import com.example.myapplication.ui.UiFormat
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AdminGudangProductsFragment : Fragment() {
    private var _binding: FragmentAdminGudangProductsBinding? = null
    private val binding get() = _binding!!
    private var allProducts = listOf<ProductEntity>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.fab.setOnClickListener { showProductForm(null) }
        refresh()
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
            allProducts = AppDatabase.get(requireContext()).productDao().getAll()
            withContext(Dispatchers.Main) {
                binding.recycler.adapter = ProductAdapter(allProducts)
            }
        }
    }

    private fun updateQuickStock(product: ProductEntity, delta: Long) {
        val newStock = (product.stock + delta).coerceAtLeast(0)
        if (newStock == product.stock) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            db.productDao().update(product.copy(stock = newStock))
            
            AuditLogger.log(
                context = requireContext(),
                userId = SessionManager(requireContext()).userId(),
                action = if (delta > 0) "STOCK_IN" else "STOCK_OUT",
                entity = "product",
                entityId = product.id,
                detail = "Quick update: ${product.name} ($newStock)"
            )
            
            withContext(Dispatchers.Main) {
                refresh()
            }
        }
    }

    private fun showProductForm(existing: ProductEntity?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) {
                AppDatabase.get(requireContext()).categoryDao().getAll().map { it.name }.sorted()
            }
            
            val b = DialogProductFormBinding.inflate(layoutInflater)
            b.txtTitle.text = if (existing == null) "Tambah Barang Baru" else "Edit Data Barang"
            b.txtSubtitle.text = if (existing == null) "Masukkan detail barang untuk stok gudang" else "Perbarui informasi barang yang sudah ada"
            
            b.etBarcode.setText(existing?.barcode.orEmpty())
            b.etName.setText(existing?.name.orEmpty())
            b.inputCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories))
            b.inputCategory.setText(existing?.category.orEmpty(), false)
            b.etPrice.setText(existing?.price?.toString().orEmpty())
            b.etStock.setText(existing?.stock?.toString().orEmpty())
            
            var selectedExpiry: Long? = existing?.expiredDateEpochMs
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            
            fun updateExpiryText() {
                b.etExpiry.setText(selectedExpiry?.let { sdf.format(Date(it)) } ?: "Tidak ada")
            }
            updateExpiryText()

            b.etExpiry.setOnClickListener {
                val picker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Pilih Tanggal Kedaluwarsa")
                    .setSelection(selectedExpiry ?: System.currentTimeMillis())
                    .build()
                picker.addOnPositiveButtonClickListener {
                    selectedExpiry = it
                    updateExpiryText()
                }
                picker.show(childFragmentManager, "EXPIRY_PICKER")
            }
            
            b.etStock.isEnabled = existing == null
            b.stockLayout.isEnabled = existing == null

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(b.root)
                .setCancelable(true)
                .show()

            b.btnCancel.setOnClickListener { dialog.dismiss() }
            b.btnSave.setOnClickListener {
                val barcode = b.etBarcode.text?.toString()?.trim()
                val name = b.etName.text?.toString()?.trim().orEmpty()
                val category = b.inputCategory.text?.toString()?.trim().orEmpty()
                val price = b.etPrice.text?.toString()?.trim()?.toLongOrNull()
                val stock = b.etStock.text?.toString()?.trim()?.toLongOrNull()

                var ok = true
                if (name.isBlank()) { b.nameLayout.error = "Nama wajib diisi"; ok = false }
                if (category.isBlank()) { b.categoryLayout.error = "Pilih kategori"; ok = false }
                if (price == null || price < 0L) { b.priceLayout.error = "Harga tidak valid"; ok = false }
                if (existing == null && (stock == null || stock < 0L)) { b.stockLayout.error = "Stok awal wajib diisi"; ok = false }

                if (!ok) return@setOnClickListener

                dialog.dismiss()
                save(existing, barcode, name, category, price ?: 0L, stock ?: 0L, selectedExpiry)
            }
        }
    }

    private fun save(existing: ProductEntity?, barcode: String?, name: String, category: String, price: Long, stock: Long, expiry: Long?) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            if (existing == null) {
                val id = db.productDao().insert(ProductEntity(barcode = barcode, name = name, category = category, price = price, stock = stock, expiredDateEpochMs = expiry))
                AuditLogger.log(requireContext(), SessionManager(requireContext()).userId(), "CREATE", "product", id, name)
            } else {
                db.productDao().update(existing.copy(barcode = barcode, name = name, category = category, price = price, expiredDateEpochMs = expiry))
                AuditLogger.log(requireContext(), SessionManager(requireContext()).userId(), "UPDATE", "product", existing.id, name)
            }
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    private fun confirmDelete(product: ProductEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Barang?")
            .setMessage("Apakah Anda yakin ingin menghapus '${product.name}'? Data ini tidak dapat dikembalikan.")
            .setPositiveButton("Hapus") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(requireContext()).productDao().delete(product)
                    AuditLogger.log(requireContext(), SessionManager(requireContext()).userId(), "DELETE", "product", product.id, product.name)
                    withContext(Dispatchers.Main) { refresh() }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    inner class ProductAdapter(private val items: List<ProductEntity>) : RecyclerView.Adapter<ProductAdapter.VH>() {
        inner class VH(val b: ItemProductTableRowBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemProductTableRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.b.txtName.text = item.name
            holder.b.txtBarcode.text = "Barcode: ${item.barcode ?: "-"}"
            holder.b.txtPrice.text = UiFormat.money(item.price)
            holder.b.txtStock.text = item.stock.toString()
            holder.b.txtCategory.text = item.category
            
            if (item.expiredDateEpochMs != null) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                holder.b.txtExpiry.visibility = View.VISIBLE
                holder.b.txtExpiry.text = "Exp: ${sdf.format(Date(item.expiredDateEpochMs))}"
            } else {
                holder.b.txtExpiry.visibility = View.GONE
            }
            
            holder.b.btnPlus.setOnClickListener { updateQuickStock(item, 1) }
            holder.b.btnMinus.setOnClickListener { updateQuickStock(item, -1) }
            holder.b.btnEdit.setOnClickListener { showProductForm(item) }
            holder.b.btnDelete.setOnClickListener { confirmDelete(item) }
        }
        override fun getItemCount() = items.size
    }
}
