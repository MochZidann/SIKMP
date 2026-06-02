package com.kopdes.kopdesjajar.ui.kasir

import com.kopdes.kopdesjajar.ui.UiFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kopdes.kopdesjajar.R
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.db.ProductEntity
import com.kopdes.kopdesjajar.data.db.PromoEntity
import com.kopdes.kopdesjajar.data.auth.SessionManager
import com.kopdes.kopdesjajar.databinding.FragmentKasirPosBinding
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

class KasirPosFragment : Fragment() {
    private var _binding: FragmentKasirPosBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager

    // ── Bluetooth HID Scanner support ──────────────────────────────────────
    // Scanner Bluetooth kirim karakter sangat cepat (<50ms per char) lalu ENTER.
    // Kita bedakan dari typing manual dengan debounce: kalau seluruh barcode
    // masuk dalam <200ms → anggap dari scanner, langsung proses.
    private val bluetoothHandler = Handler(Looper.getMainLooper())
    private var lastKeyTime = 0L
    private val SCAN_TIMEOUT_MS = 200L   // scanner selesai dalam <200ms
    // ───────────────────────────────────────────────────────────────────────

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScannedBarcode(result.contents)
        } else {
            Toast.makeText(requireContext(), "Pemindaian dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val productAdapter = KasirProductGridAdapter { product -> addToCart(product) }
    private val cartAdapter = KasirCartAdapter(
        onPlus = { id -> changeQty(id, 1) },
        onMinus = { id -> changeQty(id, -1) },
        onRemove = { id -> removeFromCart(id) },
        onQtyClick = { id, qty -> showQtyEditDialog(id, qty) }
    )
    
    private val cartQty = linkedMapOf<Long, Long>()
    private var productsById: Map<Long, ProductEntity> = emptyMap()
    private var visibleProducts: List<ProductEntity> = emptyMap<Long, ProductEntity>().values.toList()
    
    private var appliedPromo: PromoEntity? = null
    private var currentInputPay: String = ""
    
    private var currentTotal: Long = 0
    private var currentSubtotal: Long = 0
    private var currentDiscount: Long = 0
    private var currentTax: Long = 0
    private var settings: com.kopdes.kopdesjajar.data.db.SettingsEntity = com.kopdes.kopdesjajar.data.db.SettingsEntity()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKasirPosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        
        binding.recyclerProducts.adapter = productAdapter
        binding.recyclerCart.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerCart.adapter = cartAdapter

        binding.tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) { applyFilter() }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        binding.btnPay.setOnClickListener { pay() }
        binding.btnApplyPromo.setOnClickListener { applyPromo() }

        // ── Bluetooth HID Scanner: auto-focus field barcode ───────────────
        // showSoftInputOnFocus=false agar floating keyboard tidak muncul.
        // Scanner HID tidak butuh soft keyboard — input langsung ke field.
        binding.search.showSoftInputOnFocus = false
        binding.search.requestFocus()

        // ── Debounce: bedakan typing manual vs scanner Bluetooth ──────────
        // Scanner kirim semua karakter dalam <50ms lalu kirim ENTER.
        // Kita pantau waktu antar karakter: kalau sangat cepat → scan mode.
        binding.search.doAfterTextChanged { editable ->
            val now = System.currentTimeMillis()
            val elapsed = now - lastKeyTime
            lastKeyTime = now

            // Reset timer setiap kali ada karakter masuk
            bluetoothHandler.removeCallbacksAndMessages(null)

            val text = editable?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                applyFilter()
                return@doAfterTextChanged
            }

            if (elapsed < SCAN_TIMEOUT_MS && elapsed > 0) {
                // Karakter masuk sangat cepat → kemungkinan dari scanner
                // Tunggu sebentar, kalau tidak ada karakter baru → proses
                bluetoothHandler.postDelayed({
                    val scanned = binding.search.text?.toString()?.trim().orEmpty()
                    if (scanned.isNotEmpty()) {
                        handleScannedBarcode(scanned)
                        binding.search.text?.clear()
                    }
                }, SCAN_TIMEOUT_MS)
            } else {
                // Typing normal → filter produk
                applyFilter()
            }
        }

        // ── ENTER key: proses barcode (scanner selalu kirim ENTER di akhir) 
        binding.search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val barcode = binding.search.text?.toString()?.trim().orEmpty()
                if (barcode.isNotEmpty()) {
                    bluetoothHandler.removeCallbacksAndMessages(null)
                    handleScannedBarcode(barcode)
                    binding.search.text?.clear()
                }
                true
            } else false
        }

        binding.search.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                val barcode = binding.search.text?.toString()?.trim().orEmpty()
                if (barcode.isNotEmpty()) {
                    bluetoothHandler.removeCallbacksAndMessages(null)
                    handleScannedBarcode(barcode)
                    binding.search.text?.clear()
                }
                true
            } else false
        }
        
        binding.btnResetCart.setOnClickListener {
            if (cartQty.isEmpty()) return@setOnClickListener
            
            AlertDialog.Builder(requireContext())
                .setTitle("Reset Keranjang")
                .setMessage("Apakah Anda yakin ingin menghapus semua item di keranjang?")
                .setPositiveButton("Ya, Reset") { _, _ -> clearCart() }
                .setNegativeButton("Batal", null)
                .show()
        }
        
        parentFragmentManager.setFragmentResultListener("payment_done", viewLifecycleOwner) { _, _ ->
            clearCart()
        }

        loadProducts()
    }

    fun clearCart() {
        cartQty.clear()
        appliedPromo = null
        currentInputPay = ""
        renderCart()
    }

    private fun loadProducts() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            settings = db.settingsDao().get() ?: com.kopdes.kopdesjajar.data.db.SettingsEntity()
            val products = db.productDao().getAll()
            productsById = products.associateBy { it.id }
            withContext(Dispatchers.Main) {
                ensureTabs(products)
                visibleProducts = products
                applyFilter()
                renderCart()
            }
        }
    }

    private fun ensureTabs(products: List<ProductEntity>) {
        if (binding.tabs.tabCount > 0) return
        binding.tabs.addTab(binding.tabs.newTab().setText("SEMUA"))
        val categories = products.map { it.category }.distinct().sorted()
        for (c in categories) {
            binding.tabs.addTab(binding.tabs.newTab().setText(c.uppercase()))
        }
    }

    private fun applyFilter() {
        val query = binding.search.text?.toString()?.trim().orEmpty().lowercase()
        val selected = binding.tabs.getTabAt(binding.tabs.selectedTabPosition)?.text?.toString().orEmpty()
        val base = productsById.values.toList()
        val filtered = base.filter { p ->
            val matchText = query.isBlank() || p.name.lowercase().contains(query) || p.category.lowercase().contains(query) || p.barcode?.contains(query) == true
            val matchCategory = selected.isBlank() || selected == "SEMUA" || p.category.equals(selected, ignoreCase = true)
            matchText && matchCategory
        }.sortedBy { it.name }
        visibleProducts = filtered
        productAdapter.submit(visibleProducts)
    }

    private fun addToCart(product: ProductEntity) {
        val current = cartQty[product.id] ?: 0L
        if (current + 1L > product.stock) {
            Toast.makeText(requireContext(), "Stok Habis!", Toast.LENGTH_SHORT).show()
            return
        }
        cartQty[product.id] = current + 1L
        renderCart()
    }

    private fun changeQty(productId: Long, delta: Long) {
        val product = productsById[productId] ?: return
        val current = cartQty[productId] ?: return
        val next = current + delta
        if (next > product.stock) {
            Toast.makeText(requireContext(), "Stok Terbatas!", Toast.LENGTH_SHORT).show()
            return
        }
        if (next <= 0L) cartQty.remove(productId) else cartQty[productId] = next
        renderCart()
    }

    private fun applyPromo() {
        val code = binding.etPromoCode.text?.toString()?.trim()
        if (code.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Masukkan kode promo!", Toast.LENGTH_SHORT).show()
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val promo = db.promoDao().findByCode(code)
            
            withContext(Dispatchers.Main) {
                if (promo == null) {
                    Toast.makeText(requireContext(), "Kode promo tidak valid atau tidak aktif", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                
                if (promo.validUntilEpochMs < System.currentTimeMillis()) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        db.promoDao().update(promo.copy(isActive = false, isSynced = false))
                    }
                    Toast.makeText(requireContext(), "Promo sudah kedaluwarsa", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                if (promo.promoType == "TRANSACTION" && currentSubtotal < promo.minimumPurchase) {
                    Toast.makeText(requireContext(), "Minimum pembelanjaan ${UiFormat.money(promo.minimumPurchase)}", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                if (promo.promoType == "PRODUCT" && !cartQty.containsKey(promo.productId)) {
                    Toast.makeText(requireContext(), "Produk promo tidak ada di keranjang", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                
                appliedPromo = promo
                binding.etPromoCode.text?.clear()
                Toast.makeText(requireContext(), "Promo ${promo.name} berhasil digunakan!", Toast.LENGTH_SHORT).show()
                renderCart()
            }
        }
    }

    private fun removeFromCart(productId: Long) {
        cartQty.remove(productId)
        renderCart()
    }

    private fun renderCart() {
        val lines = cartQty.entries.mapNotNull { (productId, qty) ->
            val p = productsById[productId] ?: return@mapNotNull null
            KasirCartLine(product = p, qty = qty)
        }.sortedBy { it.product.name }
        cartAdapter.submit(lines)
        recomputeSummary(lines)
    }

    private fun recomputeSummary(lines: List<KasirCartLine>) {
        val subtotal = lines.sumOf { it.product.price * it.qty }
        val globalDiscount = (subtotal * (settings.discountPercent / 100.0)).toLong()
        
        var promoDiscount = 0L
        appliedPromo?.let { promo ->
            if (promo.promoType == "PRODUCT") {
                val targetItem = lines.find { it.product.id == promo.productId }
                if (targetItem != null) {
                    val lineTotal = targetItem.product.price * targetItem.qty
                    promoDiscount = (lineTotal * (promo.discountPercent / 100.0)).toLong()
                }
            } else {
                promoDiscount = (subtotal * (promo.discountPercent / 100.0)).toLong()
            }
        }
        
        val totalDiscount = globalDiscount + promoDiscount
        val afterDiscount = (subtotal - totalDiscount).coerceAtLeast(0)
        val tax = (afterDiscount * (settings.taxPercent / 100.0)).toLong()
        val total = afterDiscount + tax

        currentSubtotal = subtotal
        currentDiscount = totalDiscount
        currentTax = tax
        currentTotal = total

        binding.txtSubtotal.text = UiFormat.money(subtotal)
        if (appliedPromo != null) {
            binding.txtDiscount.text = "${UiFormat.money(totalDiscount)} (${appliedPromo!!.name})"
        } else {
            binding.txtDiscount.text = UiFormat.money(totalDiscount)
        }
        binding.txtTotal.text = UiFormat.money(total)
    }

    private fun pay() {
        if (cartQty.isEmpty()) {
            Toast.makeText(requireContext(), "Keranjang Kosong!", Toast.LENGTH_SHORT).show()
            return
        }

        val paymentMethod = when (binding.togglePaymentMethod.checkedButtonId) {
            R.id.btnPayTransfer -> "TRANSFER"
            R.id.btnPayQRIS -> "QRIS"
            else -> "TUNAI"
        }
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val cartLines = cartQty.entries.mapNotNull { (productId, qty) ->
                val product = db.productDao().findById(productId) ?: return@mapNotNull null
                product to qty
            }
            
            withContext(Dispatchers.Main) {
                val fragment = PaymentFragment.newInstance(
                    total = currentTotal,
                    subtotal = currentSubtotal,
                    discount = currentDiscount,
                    tax = currentTax,
                    paymentMethod = paymentMethod,
                    cartLines = cartLines
                )
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun startBarcodeScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            setPrompt("Pindai Barcode Barang untuk Menambah ke Keranjang")
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
                    
                    // Search again after syncing with remote Laravel MySQL
                    existing = db.productDao().findByBarcode(barcode)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            withContext(Dispatchers.Main) {
                binding.search.text?.clear()
                binding.search.requestFocus()

                if (existing != null) {
                    // Refresh products in memory
                    loadProducts()
                    
                    // Add to cart logic
                    val current = cartQty[existing.id] ?: 0L
                    if (current + 1L > existing.stock) {
                        Toast.makeText(requireContext(), "Stok Habis untuk ${existing.name}!", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    cartQty[existing.id] = current + 1L
                    renderCart()
                    Toast.makeText(requireContext(), "${existing.name} ditambahkan ke keranjang", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Produk dengan barcode $barcode tidak ditemukan di server.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startBarcodeSimulation() {
        if (productsById.isEmpty()) {
            Toast.makeText(requireContext(), "Tidak ada produk di database untuk disimulasikan", Toast.LENGTH_SHORT).show()
            return
        }

        val productList = productsById.values.toList().sortedBy { it.name }
        val productStrings = productList.map { p ->
            "${p.name} (${p.barcode ?: "Tanpa Barcode"}) - Stok: ${p.stock}"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Simulasi Scan Barcode (Demo)")
            .setItems(productStrings) { _, which ->
                val selectedProduct = productList[which]
                val barcode = selectedProduct.barcode
                if (barcode.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Produk ini tidak memiliki barcode", Toast.LENGTH_SHORT).show()
                } else {
                    handleScannedBarcode(barcode)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showQtyEditDialog(productId: Long, currentQty: Long) {
        val product = productsById[productId] ?: return
        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentQty.toString())
            setSelection(text?.length ?: 0)
        }
        
        val container = FrameLayout(requireContext()).apply {
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val px = (24 * resources.displayMetrics.density).toInt()
                leftMargin = px
                rightMargin = px
                topMargin = (8 * resources.displayMetrics.density).toInt()
                bottomMargin = (8 * resources.displayMetrics.density).toInt()
            }
            addView(input, params)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Ubah Jumlah")
            .setMessage("Masukkan jumlah untuk ${product.name} (Stok: ${product.stock}):")
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                val textValue = input.text?.toString()?.trim()
                if (textValue.isNullOrEmpty()) return@setPositiveButton
                val newQty = textValue.toLongOrNull() ?: return@setPositiveButton
                if (newQty <= 0) {
                    removeFromCart(productId)
                    return@setPositiveButton
                }
                if (newQty > product.stock) {
                    Toast.makeText(requireContext(), "Stok tidak mencukupi! Maksimal: ${product.stock}", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                cartQty[productId] = newQty
                renderCart()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothHandler.removeCallbacksAndMessages(null)
        _binding = null
    }
}
