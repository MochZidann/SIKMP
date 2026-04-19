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
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.databinding.FragmentKasirPosBinding
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
    private var visibleProducts: List<ProductEntity> = emptyList()
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

        binding.inputPay.doAfterTextChanged { updateChangeUi() }
        binding.btnPay.setOnClickListener { pay() }

        loadProducts()
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
        binding.tabs.addTab(binding.tabs.newTab().setText("Semua Produk"))
        val categories = products.map { it.category }.distinct().sorted()
        for (c in categories) {
            binding.tabs.addTab(binding.tabs.newTab().setText(c))
        }
    }

    private fun applyFilter() {
        val query = binding.search.text?.toString()?.trim().orEmpty().lowercase()
        val selected = binding.tabs.getTabAt(binding.tabs.selectedTabPosition)?.text?.toString().orEmpty()
        val base = productsById.values.toList()
        val filtered = base.filter { p ->
            val matchText = query.isBlank() || p.name.lowercase().contains(query) || p.category.lowercase().contains(query)
            val matchCategory = selected.isBlank() || selected == "Semua Produk" || p.category == selected
            matchText && matchCategory
        }.sortedBy { it.name }
        visibleProducts = filtered
        productAdapter.submit(visibleProducts)
    }

    private fun addToCart(product: ProductEntity) {
        val current = cartQty[product.id] ?: 0L
        if (current + 1L > product.stock) {
            Toast.makeText(requireContext(), "Stok tidak cukup", Toast.LENGTH_SHORT).show()
            return
        }
        cartQty[product.id] = (current + 1L).coerceAtLeast(1L)
        renderCart()
    }

    private fun changeQty(productId: Long, delta: Long) {
        val product = productsById[productId] ?: return
        val current = cartQty[productId] ?: return
        val next = current + delta
        if (next > product.stock) {
            Toast.makeText(requireContext(), "Stok tidak cukup", Toast.LENGTH_SHORT).show()
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
        val discount = (subtotal * (settings.discountPercent / 100.0)).toLong()
        val afterDiscount = (subtotal - discount).coerceAtLeast(0)
        val tax = (afterDiscount * (settings.taxPercent / 100.0)).toLong()
        val total = afterDiscount + tax

        currentSubtotal = subtotal
        currentDiscount = discount
        currentTax = tax
        currentTotal = total

        binding.txtSubtotal.text = UiFormat.money(subtotal)
        binding.txtDiscount.text = UiFormat.money(discount)
        binding.txtTotal.text = UiFormat.money(total)
        updateChangeUi()
    }

    private fun updateChangeUi() {
        val payStr = binding.inputPay.text?.toString()?.trim().orEmpty()
        val pay = payStr.toLongOrNull() ?: 0L
        val change = pay - currentTotal
        binding.txtChange.text = UiFormat.money(if (change > 0) change else 0L)
        
        // Visual feedback for insufficient payment
        if (payStr.isNotEmpty() && pay < currentTotal) {
            binding.txtChange.setTextColor(Color.parseColor("#D32F2F"))
        } else {
            binding.txtChange.setTextColor(Color.parseColor("#00897B")) // Teal default
        }
    }

    private fun pay() {
        if (cartQty.isEmpty()) {
            Toast.makeText(requireContext(), "Keranjang masih kosong, cuy!", Toast.LENGTH_SHORT).show()
            return
        }
        val bayar = binding.inputPay.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        if (bayar < currentTotal) {
            Toast.makeText(requireContext(), "Waduh, uang bayarnya kurang nih!", Toast.LENGTH_SHORT).show()
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val cartLines = cartQty.entries.mapNotNull { (productId, qty) ->
                val product = db.productDao().findById(productId) ?: return@mapNotNull null
                product to qty
            }
            if (cartLines.size != cartQty.size) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Produk tidak ditemukan", Toast.LENGTH_SHORT).show() }
                return@launch
            }
            val stockIssue = cartLines.firstOrNull { (p, qty) -> p.stock < qty }
            if (stockIssue != null) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Stok tidak cukup: ${stockIssue.first.name}", Toast.LENGTH_SHORT).show() }
                return@launch
            }

            var saleId: Long = 0
            var receiptText: String = ""
            db.withTransaction {
                val subtotal = cartLines.sumOf { (p, qty) -> p.price * qty }
                val discount = (subtotal * (settings.discountPercent / 100.0)).toLong()
                val afterDiscount = (subtotal - discount).coerceAtLeast(0)
                val tax = (afterDiscount * (settings.taxPercent / 100.0)).toLong()
                val total = afterDiscount + tax

                val sale = SaleEntity(cashierId = session.userId(), subtotal = subtotal, discount = discount, tax = tax, total = total)
                val items = cartLines.map { (p, qty) -> SaleItemEntity(saleId = 0, productId = p.id, productName = p.name, unitPrice = p.price, quantity = qty, lineTotal = p.price * qty) }
                saleId = db.salesDao().insertSaleWithItems(sale, items)

                for ((p, qty) in cartLines) {
                    val newStock = p.stock - qty
                    db.productDao().update(p.copy(stock = newStock))
                    db.stockMovementDao().insert(StockMovementEntity(productId = p.id, userId = session.userId(), type = "PENJUALAN", quantityDelta = -qty, note = "saleId=$saleId"))
                }

                val timestamp = System.currentTimeMillis()
                val displayId = generateReceiptId(saleId, timestamp)
                receiptText = buildString {
                    append(settings.koperasiName.ifBlank { "Koperasi Merah Putih" }).append("\n")
                    append(settings.koperasiAddress.ifBlank { "Alamat Koperasi" }).append("\n")
                    append("Struk #").append(displayId).append("\n")
                    append("--------------------------------\n")
                    append("Tgl: ").append(UiFormat.dateTime(timestamp)).append("\n")
                    append("Kasir: ").append(session.username().orEmpty()).append("\n")
                    append("--------------------------------\n")
                    for ((p, qty) in cartLines) {
                        append(p.name).append("\n")
                        append("  ").append(qty).append(" x ").append(UiFormat.money(p.price))
                        append(" = ").append(UiFormat.money(p.price * qty)).append("\n")
                    }
                    append("--------------------------------\n")
                    append("Subtotal: ").append(UiFormat.money(subtotal)).append("\n")
                    append("Diskon: ").append(UiFormat.money(discount)).append("\n")
                    append("Pajak: ").append(UiFormat.money(tax)).append("\n")
                    append("TOTAL: ").append(UiFormat.money(total)).append("\n")
                    append("BAYAR: ").append(UiFormat.money(bayar)).append("\n")
                    append("KEMBALI: ").append(UiFormat.money((bayar - total).coerceAtLeast(0L))).append("\n")
                    append("--------------------------------\n")
                    append("Terima Kasih Telah Berbelanja\n")
                }
            }

            AuditLogger.log(requireContext(), session.userId(), "CREATE", "sale", saleId, "items=${cartQty.size}")

            withContext(Dispatchers.Main) {
                cartQty.clear()
                loadProducts() // Refresh product list for stock update
                renderCart()
                binding.inputPay.setText("")
                lastPaidAmount = bayar
                showSuccessDialog(saleId, receiptText)
            }
        }
    }

    private fun generateReceiptId(saleId: Long, timestamp: Long): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(timestamp))
        val sequence = (saleId % 10000).toString().padStart(4, '0')
        return "64174$datePart$sequence"
    }

    private fun showSuccessDialog(saleId: Long, receiptText: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Transaksi Berhasil")
            .setMessage(receiptText)
            .setPositiveButton("Cetak Struk") { _, _ ->
                pendingPdfSaleId = saleId
                pendingPdfPaid = lastPaidAmount
                saveReceiptPdf.launch("struk_$saleId.pdf")
            }
            .setNeutralButton("Simpan") { _, _ ->
                pendingSaveReceipt = receiptText
                saveReceipt.launch("struk_$saleId.txt")
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun exportReceiptPdf(uri: Uri, sale: SaleEntity, items: List<SaleItemEntity>, paid: Long?, cashierText: String) {
        val cfg = ReceiptLayoutConfig()
        val logo = try {
            BitmapFactory.decodeResource(requireContext().resources, R.drawable.logo_struk)
        } catch (_: Exception) {
            null
        }

        val totalQty = items.sumOf { it.quantity }
        val receiptId = generateReceiptId(sale.id, sale.createdAtEpochMs)
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sale.createdAtEpochMs))
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(sale.createdAtEpochMs))

        val itemLineHeight = 36f
        val headerHeight = if (logo != null) 120f else 60f
        val metaHeight = 100f
        val summaryHeight = 160f
        val footerHeight = 100f
        val totalHeight = headerHeight + metaHeight + (items.size * itemLineHeight) + summaryHeight + footerHeight + (cfg.margin * 2)

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(cfg.pageWidth, totalHeight.toInt().coerceAtLeast(400), 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true; textSize = 11f; typeface = Typeface.DEFAULT }
        val boldPaint = Paint(paint).apply { typeface = Typeface.DEFAULT_BOLD }
        val titlePaint = Paint(paint).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val centerPaint = Paint(paint).apply { textAlign = Paint.Align.CENTER }
        val rightPaint = Paint(paint).apply { textAlign = Paint.Align.RIGHT }
        val dashPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
        }

        var y = cfg.margin.toFloat() + 20f
        val centerX = cfg.pageWidth / 2f
        val left = cfg.margin.toFloat()
        val right = cfg.pageWidth.toFloat() - cfg.margin

        // Logo
        if (logo != null) {
            val logoSize = 60f
            val logoRect = Rect((centerX - logoSize / 2).toInt(), y.toInt(), (centerX + logoSize / 2).toInt(), (y + logoSize).toInt())
            canvas.drawBitmap(logo, null, logoRect, paint)
            y += logoSize + 10f
        }

        // Header
        canvas.drawText(settings.koperasiName.ifBlank { "Koperasi Merah Putih" }, centerX, y, titlePaint)
        y += 20f
        canvas.drawText(settings.koperasiAddress.ifBlank { "Alamat Koperasi" }, centerX, y, centerPaint)
        y += 15f
        canvas.drawText("No. Telp " + (if (settings.koperasiAddress.contains("08")) "0812345678" else ""), centerX, y, centerPaint)
        y += 15f
        canvas.drawText(receiptId, centerX, y, centerPaint)
        y += 20f

        canvas.drawLine(left, y, right, y, dashPaint)
        y += 20f

        // Meta (Date, Time, Cashier)
        canvas.drawText(dateStr, left, y, paint)
        canvas.drawText(cashierText, right, y, rightPaint)
        y += 15f
        canvas.drawText(timeStr, left, y, paint)
        canvas.drawText("-", right, y, rightPaint) // Customer placeholder
        y += 15f
        canvas.drawText("No.${sale.id}", left, y, paint)
        y += 20f

        canvas.drawLine(left, y, right, y, dashPaint)
        y += 20f

        // Items
        items.forEachIndexed { index, item ->
            val num = "${index + 1}. "
            canvas.drawText(num + item.productName, left, y, boldPaint)
            y += 16f
            canvas.drawText("  ${item.quantity} x " + UiFormat.money(item.unitPrice).replace("Rp", "").trim(), left + 10f, y, paint)
            canvas.drawText(UiFormat.money(item.lineTotal), right, y, rightPaint)
            y += 20f
        }

        canvas.drawLine(left, y, right, y, dashPaint)
        y += 25f

        // Summary
        canvas.drawText("Total QTY : $totalQty", left, y, paint)
        y += 20f
        canvas.drawText("Sub Total", left, y, paint)
        canvas.drawText(UiFormat.money(sale.subtotal), right, y, rightPaint)
        y += 20f
        canvas.drawText("Total", left, y, boldPaint)
        canvas.drawText(UiFormat.money(sale.total), right, y, boldPaint.apply { textAlign = Paint.Align.RIGHT })
        y += 20f
        if (paid != null) {
            canvas.drawText("Bayar (Cash)", left, y, paint)
            canvas.drawText(UiFormat.money(paid), right, y, rightPaint)
            y += 20f
            canvas.drawText("Kembali", left, y, paint)
            canvas.drawText(UiFormat.money((paid - sale.total).coerceAtLeast(0L)), right, y, rightPaint)
            y += 20f
        }

        // Footer
        y += 10f
        canvas.drawText("Terimakasih Telah Berbelanja", centerX, y, centerPaint)
        y += 20f
        canvas.drawText("Link Kritik dan Saran:", centerX, y, paint.apply { textAlign = Paint.Align.CENTER; textSize = 9f })
        y += 12f
        canvas.drawText("sikmp.com/e-receipt/$receiptId", centerX, y, paint.apply { textAlign = Paint.Align.CENTER; textSize = 9f })

        doc.finishPage(page)
        requireContext().contentResolver.openOutputStream(uri)?.use { out -> doc.writeTo(out) }
        doc.close()
    }

    private data class ReceiptLayoutConfig(val pageWidth: Int = 300, val margin: Int = 16, val lineHeight: Int = 18)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
