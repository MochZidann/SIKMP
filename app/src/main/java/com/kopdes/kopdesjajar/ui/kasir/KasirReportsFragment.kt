package com.kopdes.kopdesjajar.ui.kasir

import com.kopdes.kopdesjajar.ui.UiFormat
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Paint
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
import com.kopdes.kopdesjajar.R
import com.kopdes.kopdesjajar.data.audit.AuditLogger
import com.kopdes.kopdesjajar.data.auth.SessionManager
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.db.SaleEntity
import com.kopdes.kopdesjajar.databinding.FragmentKasirReportsBinding
import com.kopdes.kopdesjajar.databinding.DialogReceiptDetailBinding
import com.kopdes.kopdesjajar.databinding.ItemReceiptProductBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import android.graphics.drawable.GradientDrawable

class KasirReportsFragment : Fragment() {
    private var _binding: FragmentKasirReportsBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    private var adapter: KasirReportsAdapter? = null
    private var filterFromEpochMs: Long? = null
    private var filterToEpochMs: Long? = null

    private var chartLabels: List<String> = emptyList()
    private var chartPendapatan: List<Long> = emptyList()
    private var chartMargin: List<Long> = emptyList()
    private var currentSalesList: List<SaleEntity> = emptyList()

    private var currentPage = 0
    private var isLoading = false
    private val pageSize = 20
    private val salesRowsList = mutableListOf<KasirReportRow>()

    private val pdfExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        uri?.let { performPdfExport(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKasirReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        adapter = KasirReportsAdapter { saleId -> showSaleDetail(saleId) }
        binding.recyclerRows.adapter = adapter

        setupCharts()
        applyQuickFilter(7)

        binding.chipHariIni.setOnClickListener { applyToday(); refresh() }
        binding.chip7Hari.setOnClickListener { applyQuickFilter(7); refresh() }
        binding.chip30Hari.setOnClickListener { applyQuickFilter(30); refresh() }
        binding.chipBulanIni.setOnClickListener { applyThisMonth(); refresh() }

        binding.btnFrom.setOnClickListener { pickDate(true) }
        binding.btnTo.setOnClickListener { pickDate(false) }
        binding.btnApply.setOnClickListener { refresh() }
        binding.btnExportPdf.setOnClickListener {
            val fileName = "Laporan_Penjualan_${System.currentTimeMillis()}.pdf"
            pdfExportLauncher.launch(fileName)
        }

        binding.btnPrev.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                fetchData()
            }
        }

        binding.btnNext.setOnClickListener {
            currentPage++
            fetchData()
        }

        binding.toggleChartType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnChartPendapatan -> updateLineChart(chartLabels, chartPendapatan, "#3B82F6")
                    R.id.btnChartMargin -> updateLineChart(chartLabels, chartMargin, "#673AB7")
                }
            }
        }
        binding.toggleChartType.check(R.id.btnChartPendapatan)

        refresh()
    }

    private fun pickDate(isFrom: Boolean) {
        val cal = Calendar.getInstance()
        val initial = if (isFrom) filterFromEpochMs else filterToEpochMs
        if (initial != null) cal.timeInMillis = initial
        DatePickerDialog(requireContext(), { _, year, month, day ->
            val c = Calendar.getInstance()
            c.set(year, month, day, 0, 0, 0)
            c.set(Calendar.MILLISECOND, 0)
            val selected = c.timeInMillis

            if (isFrom) {
                filterFromEpochMs = selected
            } else {
                c.set(year, month, day, 23, 59, 59)
                c.set(Calendar.MILLISECOND, 999)
                filterToEpochMs = c.timeInMillis
            }
            updateFilterButtons()
            binding.chipGroupTime.clearCheck()
            refresh()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateFilterButtons() {
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale("in", "ID"))
        binding.btnFrom.text = filterFromEpochMs?.let { fmt.format(Date(it)) } ?: "Mulai"
        binding.btnTo.text = filterToEpochMs?.let { fmt.format(Date(it)) } ?: "Selesai"
    }

    private fun refresh() {
        currentPage = 0
        fetchData()
    }

    private fun fetchData() {
        if (isLoading) return
        isLoading = true

        val from = filterFromEpochMs ?: rangeDay().first
        val to = filterToEpochMs ?: rangeDay().second
        val offset = currentPage * pageSize

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            
            val totalCount = db.salesDao().countSalesBetween(from, to)
            val pagedSales = db.salesDao().listSalesBetweenPaged(from, to, pageSize, offset)
            
            // For charts and summary, we still need the full range data
            val allSales = db.salesDao().listSalesBetween(from, to)
            
            val timeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("in", "ID"))
            
            val newRows = pagedSales.map { s ->
                val cal = Calendar.getInstance()
                cal.timeInMillis = s.createdAtEpochMs
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val seq = db.salesDao().countSalesBefore(s.id, cal.timeInMillis)
                
                KasirReportRow(
                    saleId = s.id,
                    displayId = generateReceiptId(s.id, s.createdAtEpochMs, seq),
                    dateTimeText = timeFmt.format(Date(s.createdAtEpochMs)),
                    cashierText = "Kasir: ${session.username().orEmpty()}",
                    itemCountText = "", 
                    totalText = UiFormat.money(s.total),
                    methodText = s.paymentMethod,
                    statusText = s.status
                )
            }

            val totalTrx = allSales.size.toLong()
            val totalPendapatan = allSales.sumOf { it.total }
            val labaKotor = allSales.sumOf { (it.subtotal - it.discount).coerceAtLeast(0) }

            val dayFmt = SimpleDateFormat("dd/MM", Locale("in", "ID"))
            val grouped = allSales.groupBy { dayFmt.format(Date(it.createdAtEpochMs)) }
            
            val labels = mutableListOf<String>()
            val pendapatanPerHari = mutableListOf<Long>()
            val marginPerHari = mutableListOf<Long>()
            
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = from
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            val limitCal = Calendar.getInstance()
            limitCal.timeInMillis = to
            
            while (calendar.timeInMillis <= limitCal.timeInMillis) {
                val label = dayFmt.format(calendar.time)
                labels.add(label)
                
                val daySales = grouped[label]
                val revenue = daySales?.sumOf { it.total } ?: 0L
                val margin = daySales?.sumOf { (it.subtotal - it.discount).coerceAtLeast(0) } ?: 0L
                
                pendapatanPerHari.add(revenue)
                marginPerHari.add(margin)
                
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            if (currentPage == 0) AuditLogger.log(requireContext(), session.userId(), "VIEW", "report_kasir", null, "from=$from to=$to")

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                isLoading = false
                currentSalesList = allSales
                
                salesRowsList.clear()
                salesRowsList.addAll(newRows)
                adapter?.submit(salesRowsList)

                renderPagination(totalCount)

                binding.txtTotalTrx.text = totalTrx.toString()
                binding.txtTotalPendapatan.text = UiFormat.money(totalPendapatan)
                binding.txtLabaKotor.text = UiFormat.money(labaKotor)

                chartLabels = labels
                chartPendapatan = pendapatanPerHari
                chartMargin = marginPerHari

                val checkedId = binding.toggleChartType.checkedButtonId
                when (checkedId) {
                    R.id.btnChartPendapatan -> updateLineChart(labels, pendapatanPerHari, "#3B82F6")
                    R.id.btnChartMargin -> updateLineChart(labels, marginPerHari, "#673AB7")
                    else -> updateLineChart(labels, pendapatanPerHari, "#3B82F6")
                }
            }
        }
    }

    private fun renderPagination(totalCount: Long) {
        val totalPages = Math.ceil(totalCount.toDouble() / pageSize).toInt().coerceAtLeast(1)
        binding.layoutPagination.visibility = if (totalPages > 1) View.VISIBLE else View.GONE
        
        binding.btnPrev.isEnabled = currentPage > 0
        binding.btnNext.isEnabled = currentPage < totalPages - 1
        
        binding.layoutPageNumbers.removeAllViews()
        
        val startPage = (currentPage - 2).coerceAtLeast(0)
        val endPage = (startPage + 4).coerceAtMost(totalPages - 1)
        val actualStart = (endPage - 4).coerceAtLeast(0)
        
        for (i in actualStart..endPage) {
            val btn = MaterialButton(
                requireContext(), null, com.google.android.material.R.attr.borderlessButtonStyle
            ).apply {
                text = (i + 1).toString()
                minWidth = 0
                minimumWidth = 0
                setPadding(24, 0, 24, 0)
                setTextColor(if (i == currentPage) Color.parseColor("#3B82F6") else Color.parseColor("#64748B"))
                if (i == currentPage) {
                    paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                textSize = 13f
                setOnClickListener {
                    if (currentPage != i) {
                        currentPage = i
                        fetchData()
                    }
                }
            }
            binding.layoutPageNumbers.addView(btn)
        }
    }

    private fun performPdfExport(uri: Uri) {
        if (currentSalesList.isEmpty()) {
            Toast.makeText(requireContext(), "Tidak ada data untuk diexport", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val pdfDocument = PdfDocument()
            val paint = Paint().apply { isAntiAlias = true; textSize = 10f }
            val headerPaint = Paint(paint).apply { isFakeBoldText = true; textSize = 12f }
            val titlePaint = Paint(paint).apply { isFakeBoldText = true; textSize = 16f }

            val pageWidth = 595 // A4 width in points
            val pageHeight = 842
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            var y = 50f
            canvas.drawText("Laporan Riwayat Penjualan SIKMP", 40f, y, titlePaint); y += 25f
            val rangeText = "Periode: ${binding.btnFrom.text} - ${binding.btnTo.text}"
            canvas.drawText(rangeText, 40f, y, paint); y += 40f

            // Table Headers
            canvas.drawText("NO STRUK", 40f, y, headerPaint)
            canvas.drawText("TANGGAL", 180f, y, headerPaint)
            canvas.drawText("METODE", 330f, y, headerPaint)
            canvas.drawText("TOTAL", 460f, y, headerPaint)
            y += 10f
            canvas.drawLine(40f, y, 555f, y, paint); y += 20f

            currentSalesList.forEach { sale ->
                if (y > pageHeight - 50) {
                    pdfDocument.finishPage(page)
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = 50f
                }

                val cal = Calendar.getInstance().apply { timeInMillis = sale.createdAtEpochMs }
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val seq = db.salesDao().countSalesBefore(sale.id, cal.timeInMillis)
                val receiptId = generateReceiptId(sale.id, sale.createdAtEpochMs, seq)
                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(sale.createdAtEpochMs))

                canvas.drawText(receiptId, 40f, y, paint)
                canvas.drawText(dateStr, 180f, y, paint)
                canvas.drawText(sale.paymentMethod, 330f, y, paint)
                canvas.drawText(UiFormat.money(sale.total), 460f, y, paint)
                y += 20f
            }

            pdfDocument.finishPage(page)
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { pdfDocument.writeTo(it) }
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Laporan PDF berhasil disimpan", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal menyimpan PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            pdfDocument.close()
        }
    }

    private fun generateReceiptId(saleId: Long, timestamp: Long, seq: Long): String {
        val datePart = SimpleDateFormat("yyMMdd", Locale.US).format(Date(timestamp))
        val todaySeq = seq + 1
        val seqPart = todaySeq.toString().padStart(4, '0')
        return "TRX-$datePart-$seqPart"
    }

    private fun showSaleDetail(saleId: Long) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val sale = db.salesDao().findSaleById(saleId) ?: return@launch
            val items = db.salesDao().listItemsBySaleId(saleId)
            val settings = db.settingsDao().get()

            val cal = Calendar.getInstance()
            cal.timeInMillis = sale.createdAtEpochMs
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val seq = db.salesDao().countSalesBefore(sale.id, cal.timeInMillis)

            withContext(Dispatchers.Main) {
                val b = DialogReceiptDetailBinding.inflate(layoutInflater)

                b.txtKoperasiName.text = settings?.koperasiName ?: "Koperasi Merah Putih"
                b.txtKoperasiAddress.text = settings?.koperasiAddress ?: "Alamat Koperasi"
                b.txtKoperasiPhone.text = settings?.koperasiPhone ?: "No. Telp"
                
                val code = generateReceiptId(sale.id, sale.createdAtEpochMs, seq)
                b.txtReceiptCode.text = code
                b.txtTransactionId.text = "#$code"

                b.txtDate.text = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sale.createdAtEpochMs))
                b.txtTime.text = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(sale.createdAtEpochMs))
                b.txtCashier.text = session.username()
                b.txtNo.text = "No.$seq"

                b.itemsContainer.removeAllViews()
                var totalQty = 0L
                items.forEachIndexed { index, item ->
                    val ib = ItemReceiptProductBinding.inflate(layoutInflater, b.itemsContainer, true)
                    ib.txtProductName.text = "${index + 1}. ${item.productName}"
                    ib.txtProductQtyPrice.text = "   ${item.quantity} x ${UiFormat.money(item.unitPrice).replace("Rp", "").trim()}"
                    ib.txtProductLineTotal.text = UiFormat.money(item.lineTotal)
                    totalQty += item.quantity
                }

                b.txtTotalQty.text = "Total QTY : $totalQty"
                b.txtSubtotal.text = UiFormat.money(sale.subtotal)
                b.txtTotal.text = UiFormat.money(sale.total)
                b.txtLabelPay.text = "Bayar (${sale.paymentMethod})"
                b.txtPay.text = UiFormat.money(sale.total) // Placeholder
                b.txtChange.text = "Rp0"
                
                b.txtLink.text = "sikmp.com/e-receipt/$code"

                AlertDialog.Builder(requireContext())
                    .setView(b.root)
                    .setPositiveButton("Tutup", null)
                    .show()
            }
        }
    }

    private fun setupCharts() {
        binding.chartPenjualan.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#64748B")
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#F1F5F9")
                textColor = Color.parseColor("#64748B")
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(v: Float) =
                        if (v >= 1_000_000) "Rp${(v/1_000_000).toInt()}jt"
                        else if (v >= 1_000) "Rp${(v/1_000).toInt()}rb"
                        else "Rp${v.toInt()}"
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
        }
    }

    private fun updateLineChart(labels: List<String>, values: List<Long>, colorHex: String) {
        val entries = values.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
        val dataSet = LineDataSet(entries, "Total").apply {
            color = Color.parseColor(colorHex)
            setCircleColor(Color.parseColor(colorHex))
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(true)
            valueTextSize = 8f
            valueTextColor = Color.parseColor("#64748B")
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value >= 1_000_000) "${(value/1_000_000).toInt()}jt"
                    else if (value >= 1_000) "${(value/1_000).toInt()}rb"
                    else value.toInt().toString()
                }
            }
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
            setDrawFilled(true)
            fillDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#40" + colorHex.removePrefix("#")), Color.TRANSPARENT)
            )
        }
        binding.chartPenjualan.apply {
            data = LineData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = labels.size.coerceAtMost(7)
            animateX(400)
            invalidate()
        }
    }

    private fun applyToday() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        filterFromEpochMs = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        filterToEpochMs = cal.timeInMillis

        updateFilterButtons()
    }

    private fun applyQuickFilter(days: Int) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        filterToEpochMs = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        filterFromEpochMs = cal.timeInMillis

        updateFilterButtons()
    }

    private fun applyThisMonth() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        filterFromEpochMs = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        filterToEpochMs = cal.timeInMillis

        updateFilterButtons()
    }

    private fun rangeDay(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val from = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val to = cal.timeInMillis
        return from to to
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
