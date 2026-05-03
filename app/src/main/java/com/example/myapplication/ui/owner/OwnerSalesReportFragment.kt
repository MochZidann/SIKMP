package com.example.myapplication.ui.owner

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.util.Pair as AndroidPair
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.databinding.FragmentOwnerSalesReportBinding
import com.example.myapplication.ui.UiFormat
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class OwnerSalesReportFragment : Fragment() {
    private var _binding: FragmentOwnerSalesReportBinding? = null
    private val binding get() = _binding!!

    private val txnAdapter = OwnerTransactionAdapter()
    private val buttonDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("in", "ID"))
    private val dayLabelFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
    private val DAY_MS = 86_400_000L
    private var fromEpochMs = 0L
    private var toEpochMs = 0L
    private var txnOffset = 0
    private val pageSize = 30

    private val excelLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let { performExcelExport(it) }
    }
    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { performPdfExport(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOwnerSalesReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerTransactions.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerTransactions.adapter = txnAdapter
        setupCharts()
        setupListeners()
        applyQuickFilter(7)
        refresh(true)
    }

    private fun setupCharts() {
        binding.chartRevenue.apply {
            description.isEnabled = false; setDrawGridBackground(false)
            setTouchEnabled(true); isDragEnabled = true; setScaleEnabled(false)
            xAxis.apply { position = XAxis.XAxisPosition.BOTTOM; setDrawGridLines(false); textColor = Color.parseColor("#64748B") }
            axisLeft.apply { setDrawGridLines(true); gridColor = Color.parseColor("#F1F5F9"); textColor = Color.parseColor("#64748B")
                valueFormatter = object : ValueFormatter() { override fun getFormattedValue(v: Float) = if (v >= 1_000_000) "Rp${(v/1_000_000).toInt()}jt" else if (v >= 1_000) "Rp${(v/1_000).toInt()}rb" else "Rp${v.toInt()}" }
            }
            axisRight.isEnabled = false; legend.isEnabled = false
        }
        binding.chartTopProducts.apply {
            description.isEnabled = false; setDrawGridBackground(false); setDrawBarShadow(false); setTouchEnabled(false)
            setDrawValueAboveBar(true)
            xAxis.apply { position = XAxis.XAxisPosition.BOTTOM; setDrawGridLines(false); textColor = Color.parseColor("#1E293B"); granularity = 1f }
            axisLeft.apply { setDrawGridLines(false); axisMinimum = 0f; textColor = Color.parseColor("#64748B") }
            axisRight.isEnabled = false; legend.isEnabled = false
        }
    }

    private fun setupListeners() {
        binding.chipHariIni.setOnClickListener { applyToday(); refresh(true) }
        binding.chip7Hari.setOnClickListener { applyQuickFilter(7); refresh(true) }
        binding.chip30Hari.setOnClickListener { applyQuickFilter(30); refresh(true) }
        binding.chipBulanIni.setOnClickListener { applyThisMonth(); refresh(true) }
        binding.chipCustom.setOnClickListener { openDatePicker() }
        binding.btnFrom.setOnClickListener { openDatePicker() }
        binding.btnTo.setOnClickListener { openDatePicker() }
        binding.btnLoadMore.setOnClickListener { txnOffset += pageSize; refresh(false) }
        binding.btnExportExcel.setOnClickListener { excelLauncher.launch("Laporan_Penjualan_${System.currentTimeMillis()}.xlsx") }
        binding.btnExportPdf.setOnClickListener { pdfLauncher.launch("Laporan_Penjualan_${System.currentTimeMillis()}.pdf") }
    }

    private fun applyToday() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        fromEpochMs = cal.timeInMillis
        toEpochMs = fromEpochMs + DAY_MS - 1
        updateDateButtons()
    }

    private fun applyQuickFilter(days: Int) {
        val cal = Calendar.getInstance()
        toEpochMs = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
        fromEpochMs = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        updateDateButtons()
    }

    private fun applyThisMonth() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        fromEpochMs = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        toEpochMs = cal.timeInMillis
        updateDateButtons()
    }

    private fun updateDateButtons() {
        binding.btnFrom.text = buttonDateFormat.format(Date(fromEpochMs))
        binding.btnTo.text = buttonDateFormat.format(Date(toEpochMs))
    }

    private fun openDatePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker().setTitleText("Pilih Periode")
            .setSelection(AndroidPair(fromEpochMs, toEpochMs)).build()
        picker.addOnPositiveButtonClickListener { sel ->
            fromEpochMs = sel.first ?: fromEpochMs; toEpochMs = (sel.second ?: toEpochMs) + 86399999L
            binding.chipCustom.isChecked = true; updateDateButtons(); refresh(true)
        }
        picker.show(childFragmentManager, "date_range")
    }

    private fun refresh(reset: Boolean) {
        if (reset) { txnOffset = 0; txnAdapter.replaceAll(emptyList()) }
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val metrics = db.salesDao().metrics(fromEpochMs, toEpochMs, null)
            val avgBasket = db.salesDao().avgBasketSize(fromEpochMs, toEpochMs)
            val daily = db.salesDao().dailyTotals(fromEpochMs, toEpochMs, null)
            val top5 = db.salesDao().top5Products(fromEpochMs, toEpochMs)
            val txns = db.salesDao().transactionList(fromEpochMs, toEpochMs, pageSize, txnOffset)

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.txtRevenue.text = UiFormat.money(metrics.revenue)
                binding.txtTxnCount.text = metrics.txnCount.toString()
                binding.txtItemsSold.text = metrics.itemsSold.toString()
                binding.txtAvgBasket.text = UiFormat.money(avgBasket)

                updateRevenueChart(daily)
                updateTop5Chart(top5)

                if (reset) txnAdapter.replaceAll(txns) else txnAdapter.append(txns)
                binding.btnLoadMore.visibility = if (txns.size == pageSize) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updateRevenueChart(daily: List<com.example.myapplication.data.db.SalesDailyTotal>) {
        val utcDays = utcDaysBetween(fromEpochMs, toEpochMs)
        val labels = utcDays.map { dayLabelFormat.format(Date(it)) }
        val dailyMap = daily.associate { it.dayStartEpochMs to it.total }
        val entries = utcDays.mapIndexed { i, d -> Entry(i.toFloat(), (dailyMap[d] ?: 0L).toFloat()) }
        val dataSet = LineDataSet(entries, "Pendapatan").apply {
            color = Color.parseColor("#3B82F6"); setCircleColor(Color.parseColor("#3B82F6"))
            lineWidth = 2.5f; circleRadius = 4f; setDrawValues(true)
            valueTextSize = 8f; valueTextColor = Color.parseColor("#64748B")
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value >= 1_000_000) "${(value/1_000_000).toInt()}jt"
                    else if (value >= 1_000) "${(value/1_000).toInt()}rb"
                    else value.toInt().toString()
                }
            }
            mode = LineDataSet.Mode.CUBIC_BEZIER; cubicIntensity = 0.2f; setDrawFilled(true)
            fillDrawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#403B82F6"), Color.TRANSPARENT))
        }
        binding.chartRevenue.apply {
            data = LineData(dataSet); xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = labels.size.coerceAtMost(10); animateX(400); invalidate()
        }
    }

    private fun updateTop5Chart(top5: List<com.example.myapplication.data.db.BestSeller>) {
        if (top5.isEmpty()) {
            binding.chartTopProducts.clear()
            binding.chartTopProducts.setNoDataText("Tidak ada produk terjual")
            binding.chartTopProducts.setNoDataTextColor(Color.GRAY)
            binding.chartTopProducts.invalidate()
            return
        }
        val entries = top5.mapIndexed { i, p -> BarEntry(i.toFloat(), p.quantity.toFloat()) }
        val colors = listOf("#3B82F6", "#10B981", "#F59E0B", "#8B5CF6", "#EC4899").map { Color.parseColor(it) }
        val dataSet = BarDataSet(entries, "Produk").apply {
            this.colors = colors; setDrawValues(true); valueTextColor = Color.parseColor("#64748B"); valueTextSize = 10f
            valueFormatter = object : ValueFormatter() { override fun getFormattedValue(v: Float) = "${v.toInt()} unit" }
        }
        binding.chartTopProducts.apply {
            data = BarData(dataSet); xAxis.valueFormatter = IndexAxisValueFormatter(top5.map { it.productName })
            xAxis.labelCount = top5.size; animateY(400); invalidate()
        }
    }

    private fun performExcelExport(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val wb = XSSFWorkbook()
                val sheet = wb.createSheet("Laporan Penjualan")
                val boldStyle = wb.createCellStyle().apply { val f = wb.createFont(); f.bold = true; setFont(f) }
                val header = sheet.createRow(0)
                listOf("ID Transaksi", "Waktu", "Jumlah Item", "Metode Bayar", "Total (Rp)").forEachIndexed { i, s ->
                    header.createCell(i).also { it.setCellValue(s); it.cellStyle = boldStyle }
                }
                val dtFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                txnAdapter.getItems().forEachIndexed { i, row ->
                    val r = sheet.createRow(i + 1)
                    r.createCell(0).setCellValue(row.transactionId)
                    r.createCell(1).setCellValue(dtFmt.format(Date(row.createdAtEpochMs)))
                    r.createCell(2).setCellValue(row.itemCount.toDouble())
                    r.createCell(3).setCellValue(row.paymentMethod)
                    r.createCell(4).setCellValue(row.total.toDouble())
                }
                for (i in 0..4) sheet.autoSizeColumn(i)
                val os: OutputStream? = requireContext().contentResolver.openOutputStream(uri)
                os?.use { wb.write(it) }; wb.close()
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "✅ Laporan Excel berhasil diekspor!", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun performPdfExport(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val doc = android.graphics.pdf.PdfDocument()
                val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 12f }
                val boldPaint = android.graphics.Paint(paint).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD }
                val titlePaint = android.graphics.Paint(paint).apply { textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas
                var y = 50f
                canvas.drawText("LAPORAN PENJUALAN OWNER", 50f, y, titlePaint); y += 30f
                val dtRange = "${buttonDateFormat.format(Date(fromEpochMs))} s/d ${buttonDateFormat.format(Date(toEpochMs))}"
                canvas.drawText("Periode: $dtRange", 50f, y, paint); y += 25f
                canvas.drawText("Dicetak: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}", 50f, y, paint); y += 35f
                canvas.drawLine(50f, y, 545f, y, paint); y += 20f
                canvas.drawText("ID Transaksi", 50f, y, boldPaint); canvas.drawText("Waktu", 200f, y, boldPaint)
                canvas.drawText("Item", 360f, y, boldPaint); canvas.drawText("Total", 450f, y, boldPaint); y += 15f
                canvas.drawLine(50f, y, 545f, y, paint); y += 20f
                val dtFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                txnAdapter.getItems().forEach { t ->
                    if (y > 800) { y = 50f }
                    canvas.drawText(t.transactionId.takeLast(12), 50f, y, paint)
                    canvas.drawText(dtFmt.format(Date(t.createdAtEpochMs)), 200f, y, paint)
                    canvas.drawText("${t.itemCount} item", 360f, y, paint)
                    canvas.drawText(UiFormat.money(t.total), 420f, y, paint); y += 20f
                }
                doc.finishPage(page)
                requireContext().contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
                doc.close()
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "✅ Laporan PDF berhasil diekspor!", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun utcDayStart(ms: Long) = (ms / DAY_MS) * DAY_MS
    private fun utcDaysBetween(from: Long, to: Long): List<Long> {
        val start = utcDayStart(from); val end = utcDayStart(to)
        val result = ArrayList<Long>(); var t = start
        while (t <= end) { result.add(t); t += DAY_MS }
        return result
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
