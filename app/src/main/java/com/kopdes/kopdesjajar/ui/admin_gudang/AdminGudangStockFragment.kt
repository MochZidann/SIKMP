package com.kopdes.kopdesjajar.ui.admin_gudang

import android.content.DialogInterface
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
import com.kopdes.kopdesjajar.data.audit.AuditLogger
import com.kopdes.kopdesjajar.data.auth.SessionManager
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.network.SyncManager
import com.kopdes.kopdesjajar.data.db.ProductEntity
import com.kopdes.kopdesjajar.data.db.StockMovementEntity
import com.kopdes.kopdesjajar.databinding.FragmentAdminGudangStockBinding
import com.kopdes.kopdesjajar.databinding.ItemStockRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.android.volley.Request
import com.google.gson.reflect.TypeToken
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.kopdes.kopdesjajar.data.db.KoperasiDbHelper
import com.kopdes.kopdesjajar.data.network.ProductSyncPayload
import com.kopdes.kopdesjajar.data.network.VolleyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminGudangStockFragment : Fragment() {
    private var _binding: FragmentAdminGudangStockBinding? = null

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScannedBarcode(result.contents)
        } else {
            Toast.makeText(requireContext(), "Pemindaian dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }
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

        binding.fabScan.setOnClickListener {
            startBarcodeScanner()
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
        val view = layoutInflater.inflate(com.kopdes.kopdesjajar.R.layout.dialog_stock_input, null)
        val etProductName = view.findViewById<TextInputEditText>(com.kopdes.kopdesjajar.R.id.etProductName)
        val etProductBarcode = view.findViewById<TextInputEditText>(com.kopdes.kopdesjajar.R.id.etProductBarcode)
        val etQty = view.findViewById<TextInputEditText>(com.kopdes.kopdesjajar.R.id.etQty)
        val layoutQty = view.findViewById<TextInputLayout>(com.kopdes.kopdesjajar.R.id.layoutQty)

        etProductName.setText(product.name)
        etProductBarcode.setText(product.barcode ?: "-")
        layoutQty.hint = "Jumlah Stok Masuk"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Stok Masuk")
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("Tambah", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val qty = etQty.text.toString().toLongOrNull()
                if (qty != null && qty > 0) {
                    updateStock(product, qty, "STOK_MASUK")
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Jumlah tidak valid", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Batalkan Input?")
                    .setMessage("Data yang sudah diisi akan hilang. Yakin ingin keluar?")
                    .setPositiveButton("Keluar") { _, _ -> dialog.dismiss() }
                    .setNegativeButton("Lanjutkan Isi", null)
                    .show()
            }
        }
        dialog.show()
    }

    private fun showAdjustDialog(product: ProductEntity) {
        val view = layoutInflater.inflate(com.kopdes.kopdesjajar.R.layout.dialog_stock_input, null)
        val etProductName = view.findViewById<TextInputEditText>(com.kopdes.kopdesjajar.R.id.etProductName)
        val etProductBarcode = view.findViewById<TextInputEditText>(com.kopdes.kopdesjajar.R.id.etProductBarcode)
        val etQty = view.findViewById<TextInputEditText>(com.kopdes.kopdesjajar.R.id.etQty)
        val layoutQty = view.findViewById<TextInputLayout>(com.kopdes.kopdesjajar.R.id.layoutQty)

        etProductName.setText(product.name)
        etProductBarcode.setText(product.barcode ?: "-")
        layoutQty.hint = "Set Stok Baru"
        etQty.setText(product.stock.toString())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Penyesuaian Stok")
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val newQty = etQty.text.toString().toLongOrNull()
                if (newQty != null && newQty >= 0) {
                    val delta = newQty - product.stock
                    updateStock(product, delta, "PENYESUAIAN")
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Jumlah tidak valid", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Batalkan Input?")
                    .setMessage("Data yang sudah diisi akan hilang. Yakin ingin keluar?")
                    .setPositiveButton("Keluar") { _, _ -> dialog.dismiss() }
                    .setNegativeButton("Lanjutkan Isi", null)
                    .show()
            }
        }
        dialog.show()
    }

    private fun updateStock(product: ProductEntity, delta: Long, type: String) {
        if (delta == 0L && type == "PENYESUAIAN") return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val newStock = product.stock + delta
            db.productDao().update(product.copy(stock = newStock, isSynced = false))

            db.stockMovementDao().insert(StockMovementEntity(
                productId = product.id,
                userId = SessionManager(requireContext()).userId(),
                type = type,
                quantityDelta = delta,
                note = if (type == "STOK_MASUK") "Penambahan stok" else "Penyesuaian manual"
            ))

            AuditLogger.log(requireContext(), SessionManager(requireContext()).userId(), "UPDATE", "stock", product.id, "${product.name}: $delta ($type)")

            try {
                SyncManager(requireContext()).pushAllDataToServer()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                refresh()
                Snackbar.make(binding.root, "Stok berhasil diperbarui", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun startBarcodeScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            setPrompt("Pindai Barcode untuk Menambah Stok Barang")
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
                    // Fetch all products from Laravel to synchronize database
                    val serverProducts = VolleyHelper.requestList(
                        requireContext(),
                        Request.Method.GET,
                        "sync/products",
                        object : TypeToken<List<ProductSyncPayload>>() {}
                    )

                    val helper = KoperasiDbHelper(requireContext())
                    val writableDb = helper.writableDatabase
                    writableDb.beginTransaction()
                    try {
                        serverProducts.forEach { p ->
                            writableDb.execSQL(
                                """
                                INSERT INTO products (id, barcode, name, category, price, stock, purchasePrice, minimumStock, expiredDateEpochMs, imagePath, isSynced, createdAtEpochMs)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                                ON CONFLICT(id) DO UPDATE SET
                                    barcode=excluded.barcode, name=excluded.name, category=excluded.category, price=excluded.price, stock=excluded.stock, isSynced=1
                                """.trimIndent(),
                                arrayOf<Any?>(p.id, p.barcode, p.name, p.category, p.price, p.stock, p.purchasePrice, p.minimumStock, p.expiredDateEpochMs, p.imagePath, p.createdAtEpochMs)
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
                    showStockInDialog(existing)
                } else {
                    Toast.makeText(requireContext(), "Produk dengan barcode $barcode tidak ditemukan.", Toast.LENGTH_LONG).show()
                }
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
                holder.b.txtStock.setTextColor(requireContext().getColor(com.kopdes.kopdesjajar.R.color.primary_red))
                holder.b.txtStock.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                holder.b.txtStock.setTextColor(requireContext().getColor(com.kopdes.kopdesjajar.R.color.gray_800))
                holder.b.txtStock.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            holder.b.btnStockIn.setOnClickListener { showStockInDialog(item) }
            holder.b.btnAdjust.setOnClickListener { showAdjustDialog(item) }
        }
        override fun getItemCount() = items.size
    }
}
