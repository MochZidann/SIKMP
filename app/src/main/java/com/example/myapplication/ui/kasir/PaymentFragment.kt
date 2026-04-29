package com.example.myapplication.ui.kasir

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

    private var lastPaidAmount: Long? = null
    private var pendingPdfSaleId: Long? = null
    private var pendingPdfPaid: Long? = null

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
            val settings = db.settingsDao().get() ?: com.example.myapplication.data.db.SettingsEntity()
            exportReceiptPdf(uri, sale, items, paid, settings)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "PDF struk tersimpan", Toast.LENGTH_SHORT).show()
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivity(viewIntent)
                } catch (_: Exception) {}
                finishAndReturn()
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
        binding.btnBatal.setOnClickListener { parentFragmentManager.popBackStack() }
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
                if (nominalStr.length < 12) {
                    nominalStr += value
                    updateDisplay()
                }
            }
        }
        binding.btnNumpadBackspace.setOnClickListener {
            if (nominalStr.isNotEmpty()) {
                nominalStr = nominalStr.dropLast(1)
                updateDisplay()
            }
        }
    }

    private fun updateDisplay() {
        val amount = nominalStr.toLongOrNull() ?: 0L
        binding.txtNominalDisplay.text = UiFormat.money(amount).replace("Rp", "").trim()
        
        val change = amount - totalAmount
        binding.txtKembalian.text = UiFormat.money(if (change > 0) change else 0L)
        
        val isValid = amount >= totalAmount
        binding.btnKonfirmasi.isEnabled = isValid
        binding.btnKonfirmasi.alpha = if (isValid) 1.0f else 0.5f
        binding.txtKembalian.setTextColor(if (isValid) android.graphics.Color.parseColor("#10B981") else android.graphics.Color.RED)
    }

    private fun confirmPayment() {
        val paidAmount = nominalStr.toLongOrNull() ?: 0L
        processTransaction(paidAmount)
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
                val items = cartLines.map { (p, qty) -> 
                    SaleItemEntity(saleId = 0, productId = p.id, productName = p.name, unitPrice = p.price, quantity = qty, lineTotal = p.price * qty) 
                }
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
                    append("Total: ").append(UiFormat.money(totalAmount)).append("\n")
                    append("Bayar: ").append(UiFormat.money(paidAmount)).append("\n")
                    append("Kembali: ").append(UiFormat.money((paidAmount - totalAmount).coerceAtLeast(0L))).append("\n")
                }
            }

            AuditLogger.log(requireContext(), session.userId(), "CREATE", "sale", saleId, "total=${totalAmount} method=$paymentMethod")

            withContext(Dispatchers.Main) {
                lastPaidAmount = paidAmount
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
            .setPositiveButton("Cetak") { _, _ ->
                pendingPdfSaleId = saleId
                pendingPdfPaid = lastPaidAmount
                saveReceiptPdf.launch("struk_$saleId.pdf")
            }
            .setNegativeButton("Tutup") { _, _ ->
                finishAndReturn()
            }
            .show()
    }

    private fun finishAndReturn() {
        val navHost = parentFragmentManager.findFragmentById(R.id.fragment_container)
        if (navHost is KasirPosFragment) {
            navHost.clearCart()
        }
        parentFragmentManager.popBackStack()
    }

    private fun exportReceiptPdf(uri: Uri, sale: SaleEntity, items: List<SaleItemEntity>, paid: Long?, settings: com.example.myapplication.data.db.SettingsEntity) {
        val receiptId = generateReceiptId(sale.id, sale.createdAtEpochMs)
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(300, 600, 1).create()
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
