package com.example.myapplication.ui.kasir

import android.content.ContentValues
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.data.db.SaleEntity
import com.example.myapplication.data.db.SaleItemEntity
import com.example.myapplication.data.db.StockMovementEntity
import com.example.myapplication.databinding.FragmentPaymentBinding
import com.example.myapplication.databinding.ItemPaymentRowBinding
import com.example.myapplication.ui.DashboardActivity
import com.example.myapplication.ui.UiFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentFragment : Fragment() {
    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager

    private var totalAmount: Long = 0
    private var subtotal: Long = 0
    private var discount: Long = 0
    private var tax: Long = 0
    private var paymentMethod: String = "TUNAI"
    private var productIds: LongArray = longArrayOf()
    private var quantities: LongArray = longArrayOf()

    private var nominalStr: String = ""
    private var cartLines: List<Pair<ProductEntity, Long>> = emptyList()

    private var isPaymentConfirmed = false
    private var savedSaleId: Long? = null
    private var savedPaidAmount: Long = 0

    companion object {
        private const val ARG_TOTAL = "total"
        private const val ARG_SUBTOTAL = "subtotal"
        private const val ARG_DISCOUNT = "discount"
        private const val ARG_TAX = "tax"
        private const val ARG_METHOD = "method"
        private const val ARG_IDS = "ids"
        private const val ARG_QTYS = "qtys"

        fun newInstance(
            total: Long,
            subtotal: Long,
            discount: Long,
            tax: Long,
            paymentMethod: String,
            cartLines: List<Pair<ProductEntity, Long>>
        ): PaymentFragment {
            return PaymentFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TOTAL, total)
                    putLong(ARG_SUBTOTAL, subtotal)
                    putLong(ARG_DISCOUNT, discount)
                    putLong(ARG_TAX, tax)
                    putString(ARG_METHOD, paymentMethod)
                    putLongArray(ARG_IDS, cartLines.map { it.first.id }.toLongArray())
                    putLongArray(ARG_QTYS, cartLines.map { it.second }.toLongArray())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            totalAmount = it.getLong(ARG_TOTAL)
            subtotal = it.getLong(ARG_SUBTOTAL)
            discount = it.getLong(ARG_DISCOUNT)
            tax = it.getLong(ARG_TAX)
            paymentMethod = it.getString(ARG_METHOD) ?: "TUNAI"
            productIds = it.getLongArray(ARG_IDS) ?: longArrayOf()
            quantities = it.getLongArray(ARG_QTYS) ?: longArrayOf()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())

        setupSummaryViews()
        loadCartLines()
        setupNumpad()
        updateDisplay()

        binding.btnKonfirmasi.setOnClickListener { confirmPayment() }
        binding.btnCetak.setOnClickListener { confirmAndPrint() }
        binding.btnBatal.setOnClickListener { 
            if (isPaymentConfirmed) {
                finishAndReturn()
            } else {
                parentFragmentManager.popBackStack()
            }
        }
        
        binding.btnCetak.isEnabled = false
        binding.btnCetak.alpha = 0.5f
    }

    private fun setupSummaryViews() {
        binding.txtSubtotal.text = UiFormat.money(subtotal)
        binding.txtDiscount.text = UiFormat.money(discount)
        binding.txtTotal.text = UiFormat.money(totalAmount)
    }

    private fun loadCartLines() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val lines = productIds.zip(quantities.toTypedArray()).mapNotNull { (id, qty) ->
                val p = db.productDao().findById(id) ?: return@mapNotNull null
                p to qty
            }
            cartLines = lines
            withContext(Dispatchers.Main) {
                binding.recyclerPaymentItems.layoutManager = LinearLayoutManager(requireContext())
                binding.recyclerPaymentItems.adapter = PaymentItemAdapter(cartLines)
            }
        }
    }

    private fun setupNumpad() {
        val buttons = mapOf(
            binding.btnNumpad0 to "0", binding.btnNumpad1 to "1", binding.btnNumpad2 to "2",
            binding.btnNumpad3 to "3", binding.btnNumpad4 to "4", binding.btnNumpad5 to "5",
            binding.btnNumpad6 to "6", binding.btnNumpad7 to "7", binding.btnNumpad8 to "8",
            binding.btnNumpad9 to "9", binding.btnNumpad000 to "000"
        )
        buttons.forEach { (btn, value) ->
            btn.setOnClickListener {
                if (!isPaymentConfirmed && nominalStr.length < 12) {
                    nominalStr += value
                    updateDisplay()
                }
            }
        }
        binding.btnNumpadBackspace.setOnClickListener {
            if (!isPaymentConfirmed && nominalStr.isNotEmpty()) {
                nominalStr = nominalStr.dropLast(1)
                updateDisplay()
            }
        }
    }

    private fun updateDisplay() {
        if (isPaymentConfirmed) return

        val amount = nominalStr.toLongOrNull() ?: 0L
        binding.txtNominalDisplay.text = UiFormat.money(amount).replace("Rp", "").trim()
        
        val change = amount - totalAmount
        binding.txtKembalian.text = UiFormat.money(if (change > 0) change else 0L)
        
        val isValid = amount >= totalAmount
        binding.btnKonfirmasi.isEnabled = isValid
        binding.btnKonfirmasi.alpha = if (isValid) 1.0f else 0.5f

        binding.btnCetak.isEnabled = false
        binding.btnCetak.alpha = 0.5f

        binding.txtKembalian.setTextColor(if (isValid) android.graphics.Color.parseColor("#10B981") else android.graphics.Color.RED)
    }

    private fun confirmPayment() {
        val paidAmount = nominalStr.toLongOrNull() ?: 0L
        processTransaction(paidAmount)
    }

    private fun confirmAndPrint() {
        if (!isPaymentConfirmed || savedSaleId == null) return
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val settings = db.settingsDao().get() ?: com.example.myapplication.data.db.SettingsEntity()
            saveReceiptToDownloads(savedSaleId!!, savedPaidAmount, settings, shouldFinish = true)
        }
    }

    private fun processTransaction(paidAmount: Long) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val settings = db.settingsDao().get() ?: com.example.myapplication.data.db.SettingsEntity()
            
            var saleId: Long = 0
            var receiptText: String = ""
            
            db.withTransaction {
                val timestamp = System.currentTimeMillis()
                val tempId = "TRX-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestamp))

                val sale = SaleEntity(
                    transactionId = tempId,
                    cashierId = session.userId(),
                    subtotal = subtotal,
                    discount = discount,
                    tax = tax,
                    total = totalAmount,
                    paymentMethod = paymentMethod,
                    createdAtEpochMs = timestamp
                )

                val localLines = productIds.zip(quantities.toTypedArray()).mapNotNull { (id, qty) ->
                    val p = db.productDao().findById(id) ?: return@mapNotNull null
                    p to qty
                }

                val items = localLines.map { (p, qty) -> 
                    SaleItemEntity(saleId = 0, productId = p.id, productName = p.name, unitPrice = p.price, quantity = qty, lineTotal = p.price * qty) 
                }
                saleId = db.salesDao().insertSaleWithItems(sale, items)

                for ((p, qty) in localLines) {
                    db.productDao().update(p.copy(stock = p.stock - qty))
                    db.stockMovementDao().insert(StockMovementEntity(productId = p.id, userId = session.userId(), type = "PENJUALAN", quantityDelta = -qty, note = "saleId=$saleId"))
                }

                val displayId = generateReceiptId(saleId, timestamp)
                receiptText = buildString {
                    append(settings.koperasiName.ifBlank { "Koperasi Merah Putih" }).append("\n")
                    append("Metode: ").append(paymentMethod).append("\n")
                    append("Struk #").append(displayId).append("\n")
                    append("--------------------------------\n")
                    for ((p, qty) in localLines) {
                        append(p.name).append("\n")
                        append("  ").append(qty).append(" x ").append(UiFormat.money(p.price))
                        append(" = ").append(UiFormat.money(p.price * qty)).append("\n")
                    }
                    append("--------------------------------\n")
                    append("Total: ").append(UiFormat.money(totalAmount)).append("\n")
                    append("Bayar: ").append(UiFormat.money(paidAmount)).append("\n")
                    append("Kembali: ").append(UiFormat.money((paidAmount - totalAmount).coerceAtLeast(0L))).append("\n")
                }
            }

            AuditLogger.log(requireContext(), session.userId(), "CREATE", "sale", saleId, "total=${totalAmount} method=$paymentMethod")

            withContext(Dispatchers.Main) {
                savedSaleId = saleId
                savedPaidAmount = paidAmount
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
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                transitionToPostPayment()
            }
            .show()
    }

    private fun transitionToPostPayment() {
        isPaymentConfirmed = true
        binding.btnBatal.text = "SELESAI"
        binding.btnKonfirmasi.isEnabled = false
        binding.btnKonfirmasi.alpha = 0.5f
        binding.btnCetak.isEnabled = true
        binding.btnCetak.alpha = 1.0f
        binding.numpadGrid.alpha = 0.5f
    }

    private fun finishAndReturn() {
        parentFragmentManager.setFragmentResult("payment_done", Bundle.EMPTY)
        parentFragmentManager.popBackStack()
    }

    private fun saveReceiptToDownloads(saleId: Long, paid: Long, settings: com.example.myapplication.data.db.SettingsEntity, shouldFinish: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val sale = db.salesDao().findSaleById(saleId) ?: return@launch
            val items = db.salesDao().listItemsBySaleId(saleId)
            val fileName = "struk_$saleId.pdf"

            try {
                val resolver = requireContext().contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                }

                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = java.io.File(downloadsDir, fileName)
                    Uri.fromFile(file)
                }

                uri?.let {
                    exportReceiptPdf(it, sale, items, paid, settings)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "PDF struk tersimpan di Downloads", Toast.LENGTH_SHORT).show()
                        if (shouldFinish) finishAndReturn()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal menyimpan struk: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportReceiptPdf(uri: Uri, sale: SaleEntity, items: List<SaleItemEntity>, paid: Long?, settings: com.example.myapplication.data.db.SettingsEntity) {
        val receiptId = generateReceiptId(sale.id, sale.createdAtEpochMs)
        val doc = PdfDocument()
        val pageHeight = 800 + (items.size * 50)
        val pageInfo = PdfDocument.PageInfo.Builder(380, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        
        val paint = Paint().apply { isAntiAlias = true; textSize = 14f; color = Color.BLACK }
        val boldPaint = Paint(paint).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 16f }
        val titlePaint = Paint(paint).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 22f; textAlign = Paint.Align.CENTER }
        val centerPaint = Paint(paint).apply { textAlign = Paint.Align.CENTER; textSize = 14f }
        val rightPaint = Paint(paint).apply { textAlign = Paint.Align.RIGHT; textSize = 14f }
        val dashPaint = Paint(paint).apply { 
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f) 
        }

        val pageWidth = pageInfo.pageWidth.toFloat()
        val centerX = pageWidth / 2f
        var y = 40f
        
        try {
            val bitmap = android.graphics.BitmapFactory.decodeResource(requireContext().resources, R.drawable.logo_struk)
            if (bitmap != null) {
                val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 80, 80, true)
                canvas.drawBitmap(scaled, centerX - 40f, y, null)
                y += 100f
            }
        } catch (e: Exception) {}

        canvas.drawText(settings.koperasiName.ifBlank { "Koperasi Merah Putih" }, centerX, y, titlePaint); y += 25f
        canvas.drawText(settings.koperasiAddress.ifBlank { "Alamat Koperasi" }, centerX, y, centerPaint); y += 20f
        canvas.drawText(settings.koperasiPhone.ifBlank { "No. Telp" }, centerX, y, centerPaint); y += 20f
        canvas.drawText(receiptId, centerX, y, centerPaint); y += 30f

        canvas.drawLine(20f, y, pageWidth - 20f, y, dashPaint); y += 20f

        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(sale.createdAtEpochMs))
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(sale.createdAtEpochMs))
        val cashierName = session.name() ?: session.username() ?: "Kasir"
        
        canvas.drawText(dateStr, 20f, y, paint)
        canvas.drawText(cashierName, pageWidth - 20f, y, rightPaint); y += 20f
        canvas.drawText(timeStr, 20f, y, paint)
        canvas.drawText("-", pageWidth - 20f, y, rightPaint); y += 20f
        canvas.drawText("No.${sale.id}", 20f, y, paint); y += 30f

        canvas.drawLine(20f, y, pageWidth - 20f, y, dashPaint); y += 20f

        var totalQty = 0L
        items.forEachIndexed { index, item ->
            val number = index + 1
            canvas.drawText("$number. ${item.productName}", 20f, y, paint); y += 20f
            canvas.drawText("${item.quantity} x ${UiFormat.money(item.unitPrice).replace("Rp", "").trim()}", 40f, y, paint)
            canvas.drawText(UiFormat.money(item.lineTotal), pageWidth - 20f, y, rightPaint); y += 25f
            totalQty += item.quantity
        }

        canvas.drawLine(20f, y, pageWidth - 20f, y, dashPaint); y += 30f

        canvas.drawText("Total QTY : $totalQty", 20f, y, paint); y += 30f
        
        canvas.drawText("Sub Total", 20f, y, paint)
        canvas.drawText(UiFormat.money(sale.subtotal), pageWidth - 20f, y, rightPaint); y += 25f

        canvas.drawText("Total", 20f, y, boldPaint)
        val boldRightPaint = Paint(rightPaint).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 16f }
        canvas.drawText(UiFormat.money(sale.total), pageWidth - 20f, y, boldRightPaint); y += 25f

        if (paid != null) {
            canvas.drawText("Bayar (Cash)", 20f, y, paint)
            canvas.drawText(UiFormat.money(paid), pageWidth - 20f, y, rightPaint); y += 25f

            canvas.drawText("Kembali", 20f, y, paint)
            canvas.drawText(UiFormat.money((paid - sale.total).coerceAtLeast(0)), pageWidth - 20f, y, rightPaint); y += 40f
        } else {
             y += 40f
        }

        canvas.drawText("Terimakasih Telah Berbelanja", centerX, y, centerPaint); y += 30f
        
        canvas.drawText("Link Kritik dan Saran:", centerX, y, centerPaint); y += 20f
        canvas.drawText("sikmp.com/e-receipt/$receiptId", centerX, y, centerPaint); y += 20f

        doc.finishPage(page)
        requireContext().contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
        doc.close()
    }

    private class PaymentItemAdapter(private val items: List<Pair<ProductEntity, Long>>) :
        RecyclerView.Adapter<PaymentItemAdapter.VH>() {
        class VH(val b: ItemPaymentRowBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(
            ItemPaymentRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        override fun onBindViewHolder(holder: VH, position: Int) {
            val (p, qty) = items[position]
            holder.b.txtName.text = p.name
            holder.b.txtDetail.text = "$qty x ${UiFormat.money(p.price)}"
            holder.b.txtLineTotal.text = UiFormat.money(p.price * qty)
        }
        override fun getItemCount(): Int = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
