package com.kopdes.kopdesjajar.ui.admin_gudang

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.android.volley.Request
import com.kopdes.kopdesjajar.R
import com.kopdes.kopdesjajar.data.audit.AuditLogger
import com.kopdes.kopdesjajar.data.auth.SessionManager
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.network.SyncManager
import com.kopdes.kopdesjajar.data.network.VolleyHelper
import com.kopdes.kopdesjajar.data.db.ProductEntity
import com.kopdes.kopdesjajar.data.db.StockMovementEntity
import com.kopdes.kopdesjajar.databinding.DialogProductFormBinding
import com.kopdes.kopdesjajar.databinding.FragmentAdminGudangProductsBinding
import com.kopdes.kopdesjajar.databinding.ItemProductTableRowBinding
import com.kopdes.kopdesjajar.ui.UiFormat
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.reflect.TypeToken
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.kopdes.kopdesjajar.data.db.KoperasiDbHelper
import com.kopdes.kopdesjajar.data.network.ProductSyncPayload
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

    private var selectedImagePath: String? = null
    private var activeFormBinding: DialogProductFormBinding? = null
    private var pendingCameraImagePath: String? = null
    private var isSpeedDialOpen = false

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScannedBarcode(result.contents)
        } else {
            Toast.makeText(requireContext(), "Pemindaian dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

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

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && pendingCameraImagePath != null) {
                selectedImagePath = pendingCameraImagePath
                activeFormBinding?.let { b ->
                    b.imgProductPreview.load(File(pendingCameraImagePath!!)) { crossfade(true) }
                    b.btnClearImage.visibility = View.VISIBLE
                }
            }
        }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(requireContext(), "Izin kamera dibutuhkan", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        
        binding.fab.setOnClickListener { toggleSpeedDial() }
        binding.fabManual.setOnClickListener {
            toggleSpeedDial()
            showProductForm(null)
        }
        binding.fabCamera.setOnClickListener {
            toggleSpeedDial()
            startBarcodeScanner()
        }
        
        refresh()
    }

    private fun toggleSpeedDial() {
        isSpeedDialOpen = !isSpeedDialOpen
        if (isSpeedDialOpen) {
            binding.fab.animate().rotation(45f).setDuration(200).start()
            
            binding.fabManual.visibility = View.VISIBLE
            binding.fabManual.alpha = 0f
            binding.fabManual.translationY = 50f
            binding.fabManual.animate().alpha(1f).translationY(0f).setDuration(200).start()

            binding.fabCamera.visibility = View.VISIBLE
            binding.fabCamera.alpha = 0f
            binding.fabCamera.translationY = 50f
            binding.fabCamera.animate().alpha(1f).translationY(0f).setDuration(200).setStartDelay(50).start()
        } else {
            binding.fab.animate().rotation(0f).setDuration(200).start()
            
            binding.fabManual.animate().alpha(0f).translationY(50f).setDuration(200).withEndAction { 
                binding.fabManual.visibility = View.GONE 
            }.start()

            binding.fabCamera.animate().alpha(0f).translationY(50f).setDuration(200).withEndAction { 
                binding.fabCamera.visibility = View.GONE 
            }.start()
        }
    }

    private fun startBarcodeScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            setPrompt("Pindai Barcode Barang")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(true)
            setOrientationLocked(false)
        }
        barcodeLauncher.launch(options)
    }

    private fun handleScannedBarcode(barcode: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            var existing = db.productDao().findByBarcode(barcode)
            
            if (existing == null) {
                try {
                    val serverProducts = VolleyHelper.requestList(
                        requireContext(),
                        Request.Method.GET,
                        "sync/products",
                        object : TypeToken<List<ProductSyncPayload>>() {}
                    )
                    
                    val helper = KoperasiDbHelper(requireContext())
                    val writableDb = helper.writableDatabase
                    val baseStorageUrl = VolleyHelper.BASE_URL.removeSuffix("api/") + "storage/"
                    writableDb.beginTransaction()
                    try {
                        serverProducts.forEach { p ->
                            // Resolve relative imagePath to full URL for display
                            val resolvedImagePath = when {
                                p.imagePath.isNullOrEmpty() || p.imagePath == "null" -> null
                                p.imagePath!!.startsWith("http") -> p.imagePath
                                p.imagePath!!.startsWith("/") -> null  // discard android local path
                                else -> baseStorageUrl + p.imagePath
                            }
                            writableDb.execSQL(
                                """
                                INSERT INTO products (id, barcode, name, category, price, stock, purchasePrice, minimumStock, expiredDateEpochMs, imagePath, isSynced, createdAtEpochMs)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                                ON CONFLICT(id) DO UPDATE SET
                                    barcode=excluded.barcode, name=excluded.name, category=excluded.category, price=excluded.price, 
                                    stock=excluded.stock, purchasePrice=excluded.purchasePrice, minimumStock=excluded.minimumStock, 
                                    expiredDateEpochMs=excluded.expiredDateEpochMs,
                                    imagePath = CASE 
                                        WHEN (excluded.imagePath IS NOT NULL AND excluded.imagePath != '' AND excluded.imagePath != 'null') THEN excluded.imagePath 
                                        ELSE products.imagePath END,
                                    isSynced=1
                                """.trimIndent(),
                                arrayOf(p.id, p.barcode, p.name, p.category, p.price, p.stock, p.purchasePrice, p.minimumStock, p.expiredDateEpochMs, resolvedImagePath, p.createdAtEpochMs)
                            )
                        }
                        writableDb.setTransactionSuccessful()
                    } finally {
                        writableDb.endTransaction()
                    }
                    
                    existing = db.productDao().findByBarcode(barcode)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            withContext(Dispatchers.Main) {
                if (existing != null) {
                    Toast.makeText(requireContext(), "Barang ditemukan! Mengisi form otomatis.", Toast.LENGTH_SHORT).show()
                    showProductForm(existing)
                } else {
                    val mockProduct = when (barcode) {
                        "8997009510123" -> ProductEntity(id = 0, barcode = barcode, name = "Susu Kotak UHT 250ml", category = "Minuman", price = 6000, purchasePrice = 4800, stock = 50, minimumStock = 10)
                        "8992753021408" -> ProductEntity(id = 0, barcode = barcode, name = "Indomie Goreng", category = "Makanan", price = 3500, purchasePrice = 2800, stock = 100, minimumStock = 20)
                        "8998009010214" -> ProductEntity(id = 0, barcode = barcode, name = "Kopi Instan Sachet", category = "Minuman", price = 2000, purchasePrice = 1500, stock = 150, minimumStock = 30)
                        else -> null
                    }
                    if (mockProduct != null) {
                        Toast.makeText(requireContext(), "Barcode Demo Terdeteksi! Mengisi form otomatis.", Toast.LENGTH_SHORT).show()
                        showProductForm(mockProduct)
                    } else {
                        Toast.makeText(requireContext(), "Barang baru terdeteksi.", Toast.LENGTH_SHORT).show()
                        showProductForm(null, prefilledBarcode = barcode)
                    }
                }
            }
        }
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

    private fun showProductForm(existing: ProductEntity?, prefilledBarcode: String? = null) {
        selectedImagePath = existing?.imagePath
        viewLifecycleOwner.lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) {
                AppDatabase.get(requireContext()).categoryDao().getAll().map { it.name }.sorted()
            }
            
            val b = DialogProductFormBinding.inflate(layoutInflater)
            activeFormBinding = b
            val isNewProduct = existing == null || existing.id == 0L
            b.txtTitle.text = if (isNewProduct) "Tambah Barang Baru" else "Edit Data Barang"
            b.txtSubtitle.text = if (isNewProduct) "Masukkan detail barang untuk stok gudang" else "Perbarui informasi barang yang sudah ada"
            
            b.etBarcode.setText(existing?.barcode ?: prefilledBarcode.orEmpty())
            b.etName.setText(existing?.name.orEmpty())
            b.inputCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories))
            b.inputCategory.setText(existing?.category.orEmpty(), false)
            b.etPrice.setText(existing?.price?.toString().orEmpty())
            b.etPurchasePrice.setText(existing?.purchasePrice?.toString() ?: "0")
            b.etStock.setText(existing?.stock?.toString().orEmpty())
            b.etMoq.setText(existing?.minimumStock?.toString() ?: "0")

            existing?.imagePath?.let { path ->
                val imgSource = when {
                    path.startsWith("http") -> path
                    path.startsWith("/") -> File(path)
                    else -> VolleyHelper.BASE_URL.replace("/api/", "/") + "storage/" + path
                }
                b.imgProductPreview.load(imgSource) { crossfade(true) }
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
            
            b.etStock.isEnabled = isNewProduct
            b.stockLayout.isEnabled = isNewProduct

            b.btnPickCamera.setOnClickListener { checkCameraPermissionAndLaunch() }
            b.btnPickGallery.setOnClickListener { pickImageLauncher.launch("image/*") }
            b.btnClearImage.setOnClickListener {
                selectedImagePath = null
                b.imgProductPreview.setImageResource(android.R.drawable.ic_menu_gallery)
                b.btnClearImage.visibility = View.GONE
            }

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(b.root)
                .setCancelable(false)
                .show()
            dialog.setOnDismissListener { activeFormBinding = null }

            b.btnCancel.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Batalkan Input?")
                    .setMessage("Data yang sudah diisi akan hilang. Yakin ingin keluar?")
                    .setPositiveButton("Keluar") { _, _ -> dialog.dismiss() }
                    .setNegativeButton("Lanjutkan Isi", null)
                    .show()
            }
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
                if (isNewProduct && (stock == null || stock < 0L)) { b.stockLayout.error = "Stok awal wajib diisi"; ok = false }

                if (!ok) return@setOnClickListener

                dialog.dismiss()
                save(if (isNewProduct) null else existing, barcode, name, category, price ?: 0L, purchasePrice, stock ?: 0L, moq, selectedExpiry, selectedImagePath)
            }
        }
    }

    private fun checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            val dir = File(requireContext().filesDir, "product_images")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "CAM_${System.currentTimeMillis()}.jpg")
            pendingCameraImagePath = file.absolutePath
            
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Gagal membuka kamera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun save(existing: ProductEntity?, barcode: String?, name: String, category: String, price: Long, purchasePrice: Long, stock: Long, moq: Long, expiry: Long?, imagePath: String?) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            if (existing == null) {
                val id = db.productDao().insert(ProductEntity(barcode = barcode, name = name, category = category, price = price, purchasePrice = purchasePrice, stock = stock, minimumStock = moq, expiredDateEpochMs = expiry, imagePath = imagePath))
                if (stock > 0) {
                    db.stockMovementDao().insert(StockMovementEntity(
                        productId = id,
                        userId = SessionManager(requireContext()).userId(),
                        type = "IN",
                        quantityDelta = stock,
                        note = "Stok awal saat pendaftaran barang"
                    ))
                }
                AuditLogger.log(requireContext(), SessionManager(requireContext()).userId(), "CREATE", "product", id, name)
            } else {
                if (existing.imagePath != null && existing.imagePath != imagePath && existing.imagePath!!.startsWith("/")) {
                    File(existing.imagePath!!).delete()
                }
                db.productDao().update(existing.copy(barcode = barcode, name = name, category = category, price = price, purchasePrice = purchasePrice, minimumStock = moq, expiredDateEpochMs = expiry, imagePath = imagePath, isSynced = false))
                AuditLogger.log(requireContext(), SessionManager(requireContext()).userId(), "UPDATE", "product", existing.id, name)
            }
            try {
                SyncManager(requireContext()).pushAllDataToServer()
            } catch (e: Exception) {
                e.printStackTrace()
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
                    if (product.imagePath != null && product.imagePath!!.startsWith("/")) {
                        File(product.imagePath!!).delete()
                    }
                    AppDatabase.get(requireContext()).productDao().delete(product)
                    AuditLogger.log(requireContext(), SessionManager(requireContext()).userId(), "DELETE", "product", product.id, product.name)
                    try {
                        val apiParam = product.barcode?.takeIf { it.isNotBlank() } ?: product.name
                        VolleyHelper.requestDelete(requireContext(), "sync/products/$apiParam")
                        SyncManager(requireContext()).pushAllDataToServer()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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

            val imagePath = item.imagePath
            if (!imagePath.isNullOrEmpty() && imagePath != "null") {
                val imgSource = when {
                    imagePath.startsWith("http") -> imagePath
                    imagePath.startsWith("/") -> File(imagePath)
                    else -> VolleyHelper.BASE_URL.replace("/api/", "/") + "storage/" + imagePath
                }

                holder.b.imgProduct.load(imgSource) {
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
            
            holder.b.btnEdit.setOnClickListener { showProductForm(item) }
            holder.b.btnDelete.setOnClickListener { confirmDelete(item) }
        }
        override fun getItemCount() = items.size
    }
}
