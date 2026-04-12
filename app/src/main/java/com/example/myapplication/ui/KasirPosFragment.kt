package com.example.myapplication.ui

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
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
import com.example.myapplication.ui.adapters.KasirCartAdapter
import com.example.myapplication.ui.adapters.KasirCartLine
import com.example.myapplication.ui.adapters.KasirProductGridAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        cartQty[product.id] = (current + 1L).coerceAtLeast(1L)
        renderCart()
    }

    private fun changeQty(productId: Long, delta: Long) {
        val current = cartQty[productId] ?: return
        val next = current + delta
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
        val pay = binding.inputPay.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val change = (pay - currentTotal).coerceAtLeast(0L)
        binding.txtChange.text = UiFormat.money(change)
    }

    private fun pay() {
        if (cartQty.isEmpty()) {
            Toast.makeText(requireContext(), "Keranjang kosong", Toast.LENGTH_SHORT).show()
            return
        }
        val bayar = binding.inputPay.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        if (bayar < currentTotal) {
            Toast.makeText(requireContext(), "Nominal bayar kurang", Toast.LENGTH_SHORT).show()
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

                receiptText = buildString {
                    append("Koperasi Merah Putih\nStruk #").append(saleId).append('\n')
                    append("Tanggal: ").append(UiFormat.dateTime(System.currentTimeMillis())).append('\n')
                    append("Kasir: ").append(session.username().orEmpty()).append('\n')
                    append("Bayar: ").append(UiFormat.money(bayar)).append('\n')
                    append("Kembalian: ").append(UiFormat.money((bayar - total).coerceAtLeast(0L))).append("\n\n")
                    for ((p, qty) in cartLines) {
                        append(p.name).append(" x").append(qty).append(" = ").append(UiFormat.money(p.price * qty)).append('\n')
                    }
                    append("\nSubtotal: ").append(UiFormat.money(subtotal))
                    append("\nDiskon: ").append(UiFormat.money(discount))
                    append("\nPajak: ").append(UiFormat.money(tax))
                    append("\nTotal: ").append(UiFormat.money(total)).append('\n')
                }
            }

            AuditLogger.log(requireContext(), session.userId(), "CREATE", "sale", saleId, "items=${cartQty.size}")

            withContext(Dispatchers.Main) {
                cartQty.clear()
                renderCart()
                binding.inputPay.setText("")
                lastPaidAmount = bayar
                showSuccessDialog(saleId, receiptText)
            }
        }
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
        val linesCount = items.size + 13
        val pageHeight = cfg.margin * 2 + (linesCount * cfg.lineHeight) + 40
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(cfg.pageWidth, pageHeight.coerceAtLeast(400), 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val titlePaint = Paint().apply { isAntiAlias = true; textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val bodyPaint = Paint().apply { isAntiAlias = true; textSize = 10f }
        val boldPaint = Paint().apply { isAntiAlias = true; textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val bodyRightPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val boldRightPaint = Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT }
        var y = cfg.margin.toFloat()
        val left = cfg.margin.toFloat()
        val right = (cfg.pageWidth - cfg.margin).toFloat()
        val xQty = right - 120
        val xPrice = right - 60
        val xSub = right
        canvas.drawText("Koperasi Merah Putih", left, y, titlePaint)
        y += (cfg.lineHeight + 2)
        canvas.drawText("Struk #${sale.id}", left, y, bodyPaint)
        canvas.drawText(UiFormat.dateTime(sale.createdAtEpochMs), left, y + cfg.lineHeight, bodyPaint)
        y += (cfg.lineHeight * 2)
        canvas.drawText("Kasir: $cashierText", left, y, bodyPaint)
        y += (cfg.lineHeight + 6)
        canvas.drawText("Item", left, y, boldPaint)
        canvas.drawText("Qty", xQty, y, boldRightPaint)
        canvas.drawText("Harga", xPrice, y, boldRightPaint)
        canvas.drawText("Sub", xSub, y, boldRightPaint)
        y += (cfg.lineHeight)
        for (it in items) {
            canvas.drawText(it.productName.take(24), left, y, bodyPaint)
            canvas.drawText("${it.quantity}", xQty, y, bodyRightPaint)
            canvas.drawText(UiFormat.money(it.unitPrice), xPrice, y, bodyRightPaint)
            canvas.drawText(UiFormat.money(it.lineTotal), xSub, y, bodyRightPaint)
            y += cfg.lineHeight
        }
        y += 6
        canvas.drawText("Subtotal", left, y, boldPaint); canvas.drawText(UiFormat.money(sale.subtotal), xSub, y, bodyRightPaint); y += cfg.lineHeight
        canvas.drawText("Diskon", left, y, bodyPaint); canvas.drawText(UiFormat.money(sale.discount), xSub, y, bodyRightPaint); y += cfg.lineHeight
        canvas.drawText("Pajak", left, y, bodyPaint); canvas.drawText(UiFormat.money(sale.tax), xSub, y, bodyRightPaint); y += cfg.lineHeight
        canvas.drawText("Total", left, y, boldPaint); canvas.drawText(UiFormat.money(sale.total), xSub, y, boldRightPaint); y += cfg.lineHeight
        if (paid != null) {
            canvas.drawText("Bayar", left, y, bodyPaint); canvas.drawText(UiFormat.money(paid), xSub, y, bodyRightPaint); y += cfg.lineHeight
            canvas.drawText("Kembalian", left, y, bodyPaint); canvas.drawText(UiFormat.money((paid - sale.total).coerceAtLeast(0L)), xSub, y, bodyRightPaint); y += cfg.lineHeight
        }
        y += cfg.lineHeight; canvas.drawText("Terima kasih telah berbelanja.", left, y, bodyPaint)
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
