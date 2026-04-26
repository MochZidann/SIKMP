package com.example.myapplication.ui.kasir

import com.example.myapplication.ui.UiFormat
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.data.db.SaleEntity
import com.example.myapplication.data.db.SaleItemEntity
import com.example.myapplication.data.db.StockMovementEntity
import com.example.myapplication.data.db.PromoEntity
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.databinding.FragmentKasirPosBinding
import com.example.myapplication.databinding.DialogPromoInputBinding
import com.example.myapplication.ui.kasir.KasirCartAdapter
import com.example.myapplication.ui.kasir.KasirCartLine
import com.example.myapplication.ui.kasir.KasirProductGridAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KasirPosFragment : Fragment() {
    private var _binding: FragmentKasirPosBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    
    private val productAdapter = KasirProductGridAdapter { product -> addToCart(product) }
    private val cartAdapter = KasirCartAdapter(
        onPlus = { id -> changeQty(id, 1) },
        onMinus = { id -> changeQty(id, -1) },
        onRemove = { id -> removeFromCart(id) }
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
    private var settings: com.example.myapplication.data.db.SettingsEntity = com.example.myapplication.data.db.SettingsEntity()

    private var pendingSaveReceipt: String? = null
    private var lastPaidAmount: Long? = null
    private var pendingPdfSaleId: Long? = null
    private var pendingPdfPaid: Long? = null

    private val saveReceipt = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = pendingSaveReceipt
        if (uri == null || text.isNullOrBlank()) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Struk tersimpan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val saveReceiptPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val saleId = pendingPdfSaleId
        val paid = pendingPdfPaid
        pendingPdfSaleId = null
        pendingPdfPaid = null
        if (uri == null || saleId == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val sale = db.salesDao().findSaleById(saleId) ?: return@launch
            val items = db.salesDao().listItemsBySaleId(saleId)
            val cashierText = session.username().orEmpty()
            exportReceiptPdf(uri, sale, items, paid, cashierText)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "PDF struk tersimpan", Toast.LENGTH_SHORT).show()
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivity(viewIntent)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKasirPosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        
        binding.recyclerProducts.adapter = productAdapter
        binding.recyclerCart.adapter = cartAdapter

        binding.search.doAfterTextChanged { applyFilter() }
        binding.tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) { applyFilter() }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        setupNumpad()
        
        binding.btnPay.setOnClickListener { pay() }

        loadProducts()
    }

    private fun setupNumpad() {
        val numButtons = mapOf(
            binding.btnNum0 to "0", binding.btnNum1 to "1", binding.btnNum2 to "2",
            binding.btnNum3 to "3", binding.btnNum4 to "4", binding.btnNum5 to "5",
            binding.btnNum6 to "6", binding.btnNum7 to "7", binding.btnNum8 to "8",
            binding.btnNum9 to "9", binding.btnNum000 to "000"
        )

        numButtons.forEach { (btn, value) ->
            btn.setOnClickListener {
                if (currentInputPay.length < 12) {
                    currentInputPay += value
                    updatePayDisplay()
                }
            }
        }

        binding.btnDel.setOnClickListener {
            if (currentInputPay.isNotEmpty()) {
                currentInputPay = currentInputPay.dropLast(1)
                updatePayDisplay()
            }
        }

        binding.btnClear.setOnClickListener {
            currentInputPay = ""
            updatePayDisplay()
        }

        binding.btnPromo.setOnClickListener { showPromoDialog() }
    }

    private fun updatePayDisplay() {
        val amount = currentInputPay.toLongOrNull() ?: 0L
        binding.inputPay.setText(if (currentInputPay.isEmpty()) "" else UiFormat.money(amount).replace("Rp", "").trim())
        updateChangeUi()
    }

    private fun showPromoDialog() {
        val b = DialogPromoInputBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle("Input Kode Promo")
            .setView(b.root)
            .setPositiveButton("Gunakan") { _, _ ->
                val code = b.etPromoCode.text?.toString()?.trim()?.uppercase()
                if (!code.isNullOrBlank()) {
                    validatePromo(code)
                }
            }
            .setNegativeButton("Batal", null)
            .setNeutralButton("Hapus Promo") { _, _ ->
                appliedPromo = null
                binding.txtAppliedPromos.visibility = View.GONE
                renderCart()
            }
            .show()
    }

    private fun validatePromo(code: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val promo = db.promoDao().findByCode(code)
            withContext(Dispatchers.Main) {
                if (promo == null || !promo.isActive || promo.validUntilEpochMs < System.currentTimeMillis()) {
                    Toast.makeText(requireContext(), "Promo tidak valid atau kadaluarsa", Toast.LENGTH_SHORT).show()
                } else {
                    appliedPromo = promo
                    binding.txtAppliedPromos.text = "PROMO: ${promo.name} (${promo.discountPercent}%)"
                    binding.txtAppliedPromos.visibility = View.VISIBLE
                    renderCart()
                }
            }
        }
    }

    private fun loadProducts() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            settings = db.settingsDao().get() ?: com.example.myapplication.data.db.SettingsEntity()
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
        val promoDiscountPercent = appliedPromo?.discountPercent ?: 0.0
        val promoDiscount = (subtotal * (promoDiscountPercent / 100.0)).toLong()
        
        val totalDiscount = globalDiscount + promoDiscount
        val afterDiscount = (subtotal - totalDiscount).coerceAtLeast(0)
        val tax = (afterDiscount * (settings.taxPercent / 100.0)).toLong()
        val total = afterDiscount + tax

        currentSubtotal = subtotal
        currentDiscount = totalDiscount
        currentTax = tax
        currentTotal = total

        binding.txtSubtotal.text = "Sub: " + UiFormat.money(subtotal)
        binding.txtDiscount.text = "Disk: " + UiFormat.money(totalDiscount)
        binding.txtTotal.text = UiFormat.money(total)
        updateChangeUi()
    }

    private fun updateChangeUi() {
        val pay = currentInputPay.toLongOrNull() ?: 0L
        val change = pay - currentTotal
        binding.txtChange.text = UiFormat.money(if (change > 0) change else 0L)
        
        if (currentInputPay.isNotEmpty() && pay < currentTotal) {
            binding.txtChange.setTextColor(Color.parseColor("#D32F2F"))
        } else {
            binding.txtChange.setTextColor(Color.parseColor("#10B981"))
        }
    }

    private fun pay() {
        if (cartQty.isEmpty()) {
            Toast.makeText(requireContext(), "Keranjang Kosong!", Toast.LENGTH_SHORT).show()
            return
        }
        val bayar = currentInputPay.toLongOrNull() ?: 0L
        if (bayar < currentTotal) {
            Toast.makeText(requireContext(), "Pembayaran Kurang!", Toast.LENGTH_SHORT).show()
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
            
            var saleId: Long = 0
            var receiptText: String = ""
            db.withTransaction {
                val timestamp = System.currentTimeMillis()
                val tempId = "TRX-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestamp))

                val sale = SaleEntity(
                    transactionId = tempId,
                    cashierId = session.userId(),
                    subtotal = currentSubtotal,
                    discount = currentDiscount,
                    tax = currentTax,
                    total = currentTotal,
                    paymentMethod = paymentMethod,
                    createdAtEpochMs = timestamp
                )
                val items = cartLines.map { (p, qty) -> SaleItemEntity(saleId = 0, productId = p.id, productName = p.name, unitPrice = p.price, quantity = qty, lineTotal = p.price * qty) }
                saleId = db.salesDao().insertSaleWithItems(sale, items)

                for ((p, qty) in cartLines) {
                    db.productDao().update(p.copy(stock = p.stock - qty))
                    db.stockMovementDao().insert(StockMovementEntity(productId = p.id, userId = session.userId(), type = "PENJUALAN", quantityDelta = -qty, note = "saleId=$saleId"))
                }

                val displayId = generateReceiptId(saleId, timestamp)
                receiptText = buildString {
                    append(settings.koperasiName.ifBlank { "Koperasi Merah Putih" }).append("\n")
                    append("Metode: ").append(paymentMethod).append("\n")
                    append("Struk #").append(displayId).append("\n")
                    append("--------------------------------\n")
                    for ((p, qty) in cartLines) {
                        append(p.name).append("\n")
                        append("  ").append(qty).append(" x ").append(UiFormat.money(p.price))
                        append(" = ").append(UiFormat.money(p.price * qty)).append("\n")
                    }
                    append("--------------------------------\n")
                    append("Total: ").append(UiFormat.money(currentTotal)).append("\n")
                    append("Bayar: ").append(UiFormat.money(bayar)).append("\n")
                    append("Kembali: ").append(UiFormat.money((bayar - currentTotal).coerceAtLeast(0L))).append("\n")
                }
            }

            AuditLogger.log(requireContext(), session.userId(), "CREATE", "sale", saleId, "total=${currentTotal} method=$paymentMethod")

            withContext(Dispatchers.Main) {
                cartQty.clear()
                appliedPromo = null
                currentInputPay = ""
                binding.txtAppliedPromos.visibility = View.GONE
                updatePayDisplay()
                loadProducts()
                renderCart()
                lastPaidAmount = bayar
                showSuccessDialog(saleId, receiptText)
            }
        }
    }

    private fun generateReceiptId(saleId: Long, timestamp: Long): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(timestamp))
        return "64174$datePart${(saleId % 10000).toString().padStart(4, '0')}"
    }

    private fun showSuccessDialog(saleId: Long, receiptText: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Transaksi Berhasil")
            .setMessage(receiptText)
            .setPositiveButton("Cetak") { _, _ ->
                pendingPdfSaleId = saleId
                pendingPdfPaid = lastPaidAmount
                saveReceiptPdf.launch("struk_$saleId.pdf")
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun exportReceiptPdf(uri: Uri, sale: SaleEntity, items: List<SaleItemEntity>, paid: Long?, cashierText: String) {
        val cfg = ReceiptLayoutConfig()
        val receiptId = generateReceiptId(sale.id, sale.createdAtEpochMs)
        
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(cfg.pageWidth, 600, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint().apply { isAntiAlias = true; textSize = 12f }
        val boldPaint = Paint(paint).apply { typeface = Typeface.DEFAULT_BOLD }
        
        var y = 40f
        canvas.drawText(settings.koperasiName, 20f, y, boldPaint); y += 20f
        canvas.drawText("Struk: $receiptId", 20f, y, paint); y += 15f
        canvas.drawText("Metode: ${sale.paymentMethod}", 20f, y, paint); y += 30f
        
        items.forEach { item ->
            canvas.drawText(item.productName, 20f, y, paint); y += 15f
            canvas.drawText("${item.quantity} x ${item.unitPrice} = ${item.lineTotal}", 30f, y, paint); y += 20f
        }
        y += 10f
        canvas.drawText("TOTAL: ${UiFormat.money(sale.total)}", 20f, y, boldPaint); y += 20f
        if (paid != null) {
            canvas.drawText("BAYAR: ${UiFormat.money(paid)}", 20f, y, paint); y += 20f
            canvas.drawText("KEMBALI: ${UiFormat.money(paid - sale.total)}", 20f, y, paint)
        }

        doc.finishPage(page)
        requireContext().contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
        doc.close()
    }

    private data class ReceiptLayoutConfig(val pageWidth: Int = 300)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
