package com.example.myapplication.ui.owner

import android.content.Intent
import android.graphics.Color
import android.graphics.DashPathEffect
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
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.SaleEntity
import com.example.myapplication.data.db.SaleItemEntity
import com.example.myapplication.databinding.FragmentOwnerDashboardBinding
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
import java.text.SimpleDateFormat
import java.util.*

class OwnerDashboardFragment : Fragment() {
    private var _binding: FragmentOwnerDashboardBinding? = null
    private val binding get() = _binding!!
    private val salesAdapter = OwnerLatestSalesAdapter()

    private var currentSalesForExport: List<SaleEntity> = emptyList()

    private val saveReportPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val (from, to) = todayRange()
            val sales = db.salesDao().listSalesBetween(from, to)
            
            // Fetch items for all sales
            val allItems = mutableMapOf<Long, List<SaleItemEntity>>()
            sales.forEach { s ->
                allItems[s.id] = db.salesDao().listItemsBySaleId(s.id)
            }
            
            exportFullReportPdf(uri, sales, allItems)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Laporan Berhasil Diekspor", Toast.LENGTH_SHORT).show()
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
        _binding = FragmentOwnerDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        refresh()
        
        binding.btnExportPdf.setOnClickListener {
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            saveReportPdf.launch("Laporan_Owner_$dateStr.pdf")
        }
    }

    private fun setupUI() {
        binding.recyclerRecentSales.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRecentSales.adapter = salesAdapter
        
        setupLineChart(binding.chartSalesTrend)
        setupBarChart(binding.chartTopProducts)
    }

    private fun setupLineChart(chart: LineChart) {
        chart.apply {
            setExtraOffsets(10f, 10f, 20f, 10f)
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#64748B")
                granularity = 1f
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#F1F5F9")
                textColor = Color.parseColor("#64748B")
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = "Rp${value.toInt()/1000}k"
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
        }
    }

    private fun setupBarChart(chart: HorizontalBarChart) {
        chart.apply {
            setExtraOffsets(40f, 10f, 40f, 10f)
            setDrawValueAboveBar(true)
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setTouchEnabled(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#1E293B")
                granularity = 1f
            }
            
            axisLeft.apply {
                setDrawGridLines(false)
                axisMinimum = 0f
                textColor = Color.parseColor("#64748B")
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
            val lowStockCount = db.productDao().countLowStock(10, null)
            val trendData = fetchTrendData(db)
            val top5 = fetchTop5Products(db, todayFrom, todayTo)
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
                    OwnerLatestSaleRow(it.saleId, it.cashierName, UiFormat.money(it.total), UiFormat.dateTime(it.createdAtEpochMs))
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
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0)
            val start = c.timeInMillis
            val end = start + 86400000L
            val sum = db.salesDao().summary(start, end).total
            data.add(sdf.format(c.time) to sum)
        }
        return data
    }

    private fun fetchTop5Products(db: AppDatabase, from: Long, to: Long): List<Pair<String, Long>> {
        val sales = db.salesDao().listSalesBetween(from, to)
        val allItems = mutableListOf<SaleItemEntity>()
        sales.forEach { s -> allItems.addAll(db.salesDao().listItemsBySaleId(s.id)) }
        
        return allItems.groupBy { it.productName }
            .map { it.key to it.value.sumOf { item -> item.quantity } }
            .sortedByDescending { it.second }
            .take(5)
    }

    private fun updateLineChart(data: List<Pair<String, Long>>) {
        val entries = data.mapIndexed { index, pair -> Entry(index.toFloat(), pair.second.toFloat()) }
        val dataSet = LineDataSet(entries, "Pendapatan").apply {
            color = Color.parseColor("#3B82F6")
            setCircleColor(Color.parseColor("#3B82F6"))
            lineWidth = 3f
            circleRadius = 4f
            setDrawCircleHole(true)
            circleHoleRadius = 2f
            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor = Color.parseColor("#3B82F6")
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "Rp${value.toInt()/1000}k"
            }
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
            setDrawFilled(true)
            val drawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#403B82F6"), Color.TRANSPARENT)
            )
            fillDrawable = drawable
        }

        binding.chartSalesTrend.apply {
            this.data = LineData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(data.map { it.first })
            invalidate()
        }
    }

    private fun updateBarChart(data: List<Pair<String, Long>>) {
        if (data.isEmpty()) return
        val entries = data.mapIndexed { index, pair -> BarEntry(index.toFloat(), pair.second.toFloat()) }
        val dataSet = BarDataSet(entries, "Produk").apply {
            colors = listOf(
                Color.parseColor("#3B82F6"), Color.parseColor("#10B981"), 
                Color.parseColor("#F59E0B"), Color.parseColor("#8B5CF6"), 
                Color.parseColor("#EC4899")
            )
            setDrawValues(true)
            valueTextColor = Color.parseColor("#64748B")
            valueTextSize = 10f
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()} item"
            }
        }

        binding.chartTopProducts.apply {
            this.data = BarData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(data.map { it.first })
            invalidate()
        }
    }

    private fun exportFullReportPdf(uri: Uri, sales: List<SaleEntity>, items: Map<Long, List<SaleItemEntity>>) {
        val doc = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true; textSize = 12f }
        val boldPaint = Paint(paint).apply { typeface = Typeface.DEFAULT_BOLD }
        val titlePaint = Paint(paint).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        
        var y = 50f
        canvas.drawText("LAPORAN PENDAPATAN HARIAN", 50f, y, titlePaint); y += 40f
        canvas.drawText("Tanggal: ${SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())}", 50f, y, paint); y += 30f
        
        val totalRevenue = sales.sumOf { it.total }
        canvas.drawText("Total Pendapatan: ${UiFormat.money(totalRevenue)}", 50f, y, boldPaint); y += 20f
        canvas.drawText("Total Transaksi: ${sales.size}", 50f, y, paint); y += 40f
        
        canvas.drawLine(50f, y, 545f, y, paint); y += 25f
        canvas.drawText("ID Transaksi", 50f, y, boldPaint)
        canvas.drawText("Waktu", 200f, y, boldPaint)
        canvas.drawText("Total", 450f, y, boldPaint); y += 20f
        canvas.drawLine(50f, y, 545f, y, paint); y += 25f
        
        sales.forEach { s ->
            if (y > 780) { // New page logic needed for real production, but simple for now
                 // In a real app, finishPage and startPage again
            }
            canvas.drawText(s.transactionId.takeLast(12), 50f, y, paint)
            canvas.drawText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(s.createdAtEpochMs)), 200f, y, paint)
            canvas.drawText(UiFormat.money(s.total), 450f, y, paint); y += 25f
        }
        
        doc.finishPage(page)
        requireContext().contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
        doc.close()
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
