package com.example.myapplication.ui.owner

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.SaleEntity
import com.example.myapplication.data.db.SaleItemEntity
import com.example.myapplication.databinding.FragmentOwnerDashboardBinding
import com.example.myapplication.databinding.DialogReceiptDetailBinding
import com.example.myapplication.databinding.ItemReceiptProductBinding
import com.example.myapplication.ui.DashboardActivity
import com.example.myapplication.ui.UiFormat
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class OwnerDashboardFragment : Fragment() {
    private var _binding: FragmentOwnerDashboardBinding? = null
    private val binding get() = _binding!!
    private val salesAdapter = OwnerLatestSalesAdapter { saleId -> showSaleDetail(saleId) }

    private val saveReportPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val (from, to) = todayRange()
            val sales = db.salesDao().listSalesBetween(from, to)
            exportFullReportPdf(uri, sales)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Laporan Berhasil Diekspor", Toast.LENGTH_SHORT).show()
                openFile(uri, "application/pdf")
            }
        }
    }

    private val saveReportExcel = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val (from, to) = todayRange()
            val sales = db.salesDao().listSalesBetween(from, to)
            performExcelExport(uri, sales)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Excel Berhasil Diekspor", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOwnerDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        refresh()
    }

    private fun setupUI() {
        binding.recyclerRecentSales.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRecentSales.adapter = salesAdapter
        
        setupLineChart(binding.chartSalesTrend)
        setupBarChart(binding.chartTopProducts)
        setupShortcutNavigation()

        binding.btnExportPdf.setOnClickListener {
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            saveReportPdf.launch("Ringkasan_Owner_$dateStr.pdf")
        }

        binding.btnExportExcel.setOnClickListener {
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            saveReportExcel.launch("Ringkasan_Owner_$dateStr.xlsx")
        }
        
        binding.btnSalesReport.setOnClickListener {
            (activity as? DashboardActivity)?.navigateTo(R.id.nav_owner_sales_report)
        }
    }

    private fun setupShortcutNavigation() {
        binding.cardRevenue.setOnClickListener {
            (activity as? DashboardActivity)?.navigateTo(R.id.nav_owner_sales_report)
        }
        binding.cardTransactions.setOnClickListener {
            (activity as? DashboardActivity)?.navigateTo(R.id.nav_owner_sales_report)
        }
        binding.cardLowStock.setOnClickListener {
            (activity as? DashboardActivity)?.navigateTo(R.id.nav_owner_stock_report)
        }
    }

    private fun setupLineChart(chart: LineChart) {
        chart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#64748B")
                granularity = 1f
                textSize = 9f
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#F1F5F9")
                textColor = Color.parseColor("#64748B")
                textSize = 9f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value >= 1_000_000) "Rp${(value/1_000_000).toInt()}jt"
                        else if (value >= 1_000) "Rp${(value/1_000).toInt()}k"
                        else "Rp${value.toInt()}"
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
        }
    }

    private fun setupBarChart(chart: HorizontalBarChart) {
        chart.apply {
            setDrawValueAboveBar(true)
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#1E293B")
                granularity = 1f
                textSize = 9f
            }
            
            axisLeft.apply {
                setDrawGridLines(false)
                axisMinimum = 0f
                textColor = Color.parseColor("#64748B")
                textSize = 9f
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
        }
    }

    private fun refresh() {
        binding.progressBar.visibility = View.VISIBLE
        val (todayFrom, todayTo) = todayRange()
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            
            val summary = db.salesDao().summary(todayFrom, todayTo)
            val lowStockCount = db.productDao().countLowStock(null)
            val trendData = fetchTrendData(db)
            val top5 = db.salesDao().top5Products(todayFrom, todayTo).map { it.productName to it.quantity }
            val recentSales = db.salesDao().latestWithCashier(10)

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.progressBar.visibility = View.GONE
                binding.txtTotalToday.text = UiFormat.money(summary.total)
                binding.txtTransactionCount.text = summary.txnCount.toString()
                binding.txtLowStock.text = lowStockCount.toString()

                updateLineChart(trendData)
                updateBarChart(top5)
                
                salesAdapter.submit(recentSales.map { 
                    OwnerLatestSaleRow(it.saleId, it.transactionId, it.cashierName, UiFormat.money(it.total), UiFormat.dateTime(it.createdAtEpochMs))
                })
            }
        }
    }

    private fun fetchTrendData(db: AppDatabase): List<Pair<String, Long>> {
        val data = mutableListOf<Pair<String, Long>>()
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        val cal = Calendar.getInstance()
        
        for (i in 6 downTo 0) {
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, -i)
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            val start = c.timeInMillis
            val end = start + 86400000L - 1
            val sum = db.salesDao().summary(start, end).total
            data.add(sdf.format(c.time) to sum)
        }
        return data
    }

    private fun updateLineChart(data: List<Pair<String, Long>>) {
        val entries = data.mapIndexed { index, pair -> Entry(index.toFloat(), pair.second.toFloat()) }
        val dataSet = LineDataSet(entries, "Pendapatan").apply {
            color = Color.parseColor("#3B82F6")
            setCircleColor(Color.parseColor("#3B82F6"))
            lineWidth = 2.5f
            circleRadius = 3.5f
            setDrawCircleHole(true)
            setDrawValues(true)
            valueTextSize = 8f
            valueTextColor = Color.parseColor("#64748B")
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value >= 1_000_000) "${(value/1_000_000).toInt()}jt"
                    else if (value >= 1_000) "${(value/1_000).toInt()}k"
                    else value.toInt().toString()
                }
            }
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#403B82F6"), Color.TRANSPARENT)
            )
        }

        binding.chartSalesTrend.apply {
            this.data = LineData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(data.map { it.first })
            xAxis.labelCount = data.size
            animateX(500)
            invalidate()
        }
    }

    private fun updateBarChart(data: List<Pair<String, Long>>) {
        if (data.isEmpty()) {
            binding.chartTopProducts.clear()
            binding.chartTopProducts.setNoDataText("Tidak ada data penjualan hari ini")
            binding.chartTopProducts.invalidate()
            return
        }
        val entries = data.mapIndexed { index, pair -> BarEntry(index.toFloat(), pair.second.toFloat()) }
        val dataSet = BarDataSet(entries, "Produk").apply {
            colors = listOf(
                Color.parseColor("#3B82F6"), Color.parseColor("#10B981"), 
                Color.parseColor("#F59E0B"), Color.parseColor("#8B5CF6"), 
                Color.parseColor("#EC4899")
            )
            setDrawValues(true)
            valueTextSize = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()}"
            }
        }

        binding.chartTopProducts.apply {
            this.data = BarData(dataSet).apply { barWidth = 0.6f }
            xAxis.valueFormatter = IndexAxisValueFormatter(data.map { it.first })
            xAxis.labelCount = data.size
            animateY(500)
            invalidate()
        }
    }

    private fun showSaleDetail(saleId: Long) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val sale = db.salesDao().findSaleById(saleId) ?: return@launch
            val items = db.salesDao().listItemsBySaleId(saleId)
            val settings = db.settingsDao().get()

            val cal = Calendar.getInstance()
            cal.timeInMillis = sale.createdAtEpochMs
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val seq = db.salesDao().countSalesBefore(sale.id, cal.timeInMillis)

            withContext(Dispatchers.Main) {
                val b = DialogReceiptDetailBinding.inflate(layoutInflater)
                b.txtKoperasiName.text = settings?.koperasiName ?: "Koperasi SIKMP"
                b.txtKoperasiAddress.text = settings?.koperasiAddress ?: "-"
                b.txtReceiptCode.text = sale.transactionId
                b.txtTransactionId.text = "#${sale.transactionId}"
                b.txtDate.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(sale.createdAtEpochMs))
                b.txtTime.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(sale.createdAtEpochMs))
                b.txtCashier.text = "-" // Could join with users table if needed
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
                b.txtPay.text = UiFormat.money(sale.total)
                b.txtChange.text = "Rp0"
                
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setView(b.root)
                    .setPositiveButton("Tutup", null)
                    .show()
            }
        }
    }

    private fun exportFullReportPdf(uri: Uri, sales: List<SaleEntity>) {
        val doc = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true; textSize = 10f }
        val boldPaint = Paint(paint).apply { typeface = Typeface.DEFAULT_BOLD }
        val titlePaint = Paint(paint).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD }
        
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        
        var y = 50f
        canvas.drawText("RINGKASAN HARIAN OWNER", 50f, y, titlePaint); y += 30f
        canvas.drawText("Tanggal: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())}", 50f, y, paint); y += 30f
        
        val totalRevenue = sales.sumOf { it.total }
        canvas.drawText("Total Pendapatan: ${UiFormat.money(totalRevenue)}", 50f, y, boldPaint); y += 20f
        canvas.drawText("Total Transaksi: ${sales.size}", 50f, y, paint); y += 40f
        
        canvas.drawLine(50f, y, 545f, y, paint); y += 20f
        canvas.drawText("WAKTU", 50f, y, boldPaint)
        canvas.drawText("ID TRANSAKSI", 120f, y, boldPaint)
        canvas.drawText("METODE", 350f, y, boldPaint)
        canvas.drawText("TOTAL", 450f, y, boldPaint); y += 15f
        canvas.drawLine(50f, y, 545f, y, paint); y += 20f
        
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        sales.take(35).forEach { s ->
            canvas.drawText(timeFmt.format(Date(s.createdAtEpochMs)), 50f, y, paint)
            canvas.drawText(s.transactionId.takeLast(12), 120f, y, paint)
            canvas.drawText(s.paymentMethod, 350f, y, paint)
            canvas.drawText(UiFormat.money(s.total), 450f, y, paint); y += 20f
        }
        
        doc.finishPage(page)
        requireContext().contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
        doc.close()
    }

    private fun performExcelExport(uri: Uri, sales: List<SaleEntity>) {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Ringkasan Hari Ini")
        val boldStyle = wb.createCellStyle().apply { val f = wb.createFont(); f.bold = true; setFont(f) }
        
        val header = sheet.createRow(0)
        listOf("Waktu", "ID Transaksi", "Total", "Metode").forEachIndexed { i, s ->
            header.createCell(i).also { it.setCellValue(s); it.cellStyle = boldStyle }
        }
        
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        sales.forEachIndexed { i, s ->
            val r = sheet.createRow(i + 1)
            r.createCell(0).setCellValue(timeFmt.format(Date(s.createdAtEpochMs)))
            r.createCell(1).setCellValue(s.transactionId)
            r.createCell(2).setCellValue(s.total.toDouble())
            r.createCell(3).setCellValue(s.paymentMethod)
        }
        
        for (i in 0..3) sheet.autoSizeColumn(i)
        requireContext().contentResolver.openOutputStream(uri)?.use { wb.write(it) }
        wb.close()
    }

    private fun openFile(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun todayRange(): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val from = c.timeInMillis
        c.add(Calendar.DAY_OF_MONTH, 1)
        val to = c.timeInMillis - 1
        return from to to
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
