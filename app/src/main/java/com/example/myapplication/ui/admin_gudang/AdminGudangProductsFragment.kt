package com.example.myapplication.ui.admin_gudang

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.myapplication.R
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AdminGudangProductsFragment : Fragment() {
    private var _binding: FragmentAdminGudangProductsBinding? = null
    private val binding get() = _binding!!
    private var allProducts = listOf<ProductEntity>()

    // Tracks the image path selected/cleared in the currently-open product form dialog
    private var selectedImagePath: String? = null
    // Holds reference to the open dialog's binding so the launcher callback can update the preview
    private var activeFormBinding: DialogProductFormBinding? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (!isAdded || uri == null) return@registerForActivityResult
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val path = copyImageToInternal(uri)
                withContext(Dispatchers.Main) {
                    if (path != null) {
                        selectedImagePath = path
                        activeFormBinding?.let { b ->
                            b.imgProductPreview.load(File(path)) { crossfade(true) }
                            b.btnClearImage.visibility = View.VISIBLE
                        }
                    } else {
                        Toast.makeText(requireContext(), "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

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
        selectedImagePath = existing?.imagePath
        viewLifecycleOwner.lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) {
                AppDatabase.get(requireContext()).categoryDao().getAll().map { it.name }.sorted()
            }
            
            val b = DialogProductFormBinding.inflate(layoutInflater)
            activeFormBinding = b
            b.txtTitle.text = if (existing == null) "Tambah Barang Baru" else "Edit Data Barang"
            b.txtSubtitle.text = if (existing == null) "Masukkan detail barang untuk stok gudang" else "Perbarui informasi barang yang sudah ada"
            
            b.etBarcode.setText(existing?.barcode.orEmpty())
            b.etName.setText(existing?.name.orEmpty())
            b.inputCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories))
            b.inputCategory.setText(existing?.category.orEmpty(), false)
            b.etPrice.setText(existing?.price?.toString().orEmpty())
            b.etPurchasePrice.setText(existing?.purchasePrice?.toString() ?: "0")
            b.etStock.setText(existing?.stock?.toString().orEmpty())
            b.etMoq.setText(existing?.minimumStock?.toString() ?: "0")

            // Load existing image into preview (edit mode only)
            existing?.imagePath?.let { path ->
                b.imgProductPreview.load(File(path)) { crossfade(true) }
                b.btnClearImage.visibility = View.VISIBLE
            }

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

            b.btnPickImage.setOnClickListener { pickImageLauncher.launch("image/*") }
            b.btnClearImage.setOnClickListener {
                selectedImagePath = null
                b.imgProductPreview.setImageResource(android.R.drawable.ic_menu_gallery)
                b.btnClearImage.visibility = View.GONE
            }

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(b.root)
                .setCancelable(true)
                .show()
            dialog.setOnDismissListener { activeFormBinding = null }

            b.btnCancel.setOnClickListener { dialog.dismiss() }
            b.btnSave.setOnClickListener {
                val barcode = b.etBarcode.text?.toString()?.trim()
                val name = b.etName.text?.toString()?.trim().orEmpty()
                val category = b.inputCategory.text?.toString()?.trim().orEmpty()
                val price = b.etPrice.text?.toString()?.trim()?.toLongOrNull()
                val purchasePrice = b.etPurchasePrice.text?.toString()?.trim()?.toLongOrNull() ?: 0L
                val stock = b.etStock.text?.toString()?.trim()?.toLongOrNull()
                val moq = b.etMoq.text?.toString()?.trim()?.toLongOrNull() ?: 0L

                var ok = true
                if (name.isBlank()) { b.nameLayout.error = "Nama wajib diisi"; ok = false }
                if (category.isBlank()) { b.categoryLayout.error = "Pilih kategori"; ok = false }
                if (price == null || price < 0L) { b.priceLayout.error = "Harga tidak valid"; ok = false }
                if (existing == null && (stock == null || stock < 0L)) { b.stockLayout.error = "Stok awal wajib diisi"; ok = false }

                if (!ok) return@setOnClickListener

                dialog.dismiss()
                save(existing, barcode, name, category, price ?: 0L, purchasePrice, stock ?: 0L, moq, selectedExpiry, selectedImagePath)
            }
        }
    }

    private fun save(existing: ProductEntity?, barcode: String?, name: String, category: String, price: Long, purchasePrice: Long, stock: Long, moq: Long, expiry: Long?, imagePath: String?) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            if (existing == null) {
                val id = db.productDao().insert(ProductEntity(barcode = barcode, name = name, category = category, price = price, purchasePrice = purchasePrice, stock = stock, minimumStock = moq, expiredDateEpochMs = expiry, imagePath = imagePath))
                AuditLogger.log(requireContext(), SessionManager(requireContext()).userId(), "CREATE", "product", id, name)
            } else {
                // Delete replaced or cleared image file
                if (existing.imagePath != null && existing.imagePath != imagePath) {
                    File(existing.imagePath).delete()
                }
                db.productDao().update(existing.copy(barcode = barcode, name = name, category = category, price = price, purchasePrice = purchasePrice, minimumStock = moq, expiredDateEpochMs = expiry, imagePath = imagePath))
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
                    product.imagePath?.let { File(it).delete() }
                    AppDatabase.get(requireContext()).productDao().delete(product)
                    AuditLogger.log(requireContext(), SessionManager(requireContext()).userId(), "DELETE", "product", product.id, product.name)
                    withContext(Dispatchers.Main) { refresh() }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun copyImageToInternal(uri: Uri): String? {
        return try {
            val dir = File(requireContext().filesDir, "product_images")
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, "${UUID.randomUUID()}.jpg")
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
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

            // Load product image, or show tinted icon as fallback
            if (item.imagePath != null) {
                holder.b.imgProduct.load(File(item.imagePath)) {
                    crossfade(true)
                    placeholder(android.R.drawable.ic_menu_agenda)
                    error(android.R.drawable.ic_menu_agenda)
                }
                holder.b.imgProduct.clearColorFilter()
            } else {
                holder.b.imgProduct.setImageResource(android.R.drawable.ic_menu_agenda)
                holder.b.imgProduct.setColorFilter(
                    holder.itemView.context.getColor(R.color.accent_blue)
                )
            }

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
