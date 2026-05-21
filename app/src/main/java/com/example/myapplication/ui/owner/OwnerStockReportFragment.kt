package com.example.myapplication.ui.owner

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.util.Pair as AndroidPair
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.DailyInOutRow
import com.example.myapplication.data.db.StockMutationDetailRow
import com.example.myapplication.databinding.FragmentOwnerStockReportBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class OwnerStockReportFragment : Fragment() {
    private var _binding: FragmentOwnerStockReportBinding? = null
    private val binding get() = _binding!!

    private val mutationAdapter = com.example.myapplication.ui.admin_gudang.MutationDetailAdapter()
    private val buttonDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("in", "ID"))
    private val dayLabelFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

    private val DAY_MS = 86_400_000L
    private var fromEpochMs = 0L
    private var toEpochMs = 0L
    
    private var currentPage = 0
    private val pageSize = 20
    private var isLoading = false

    private val mutationTypeOptions = listOf("Semua", "Masuk", "Keluar")

    private val excelLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let { performExcelExport(it) }
    }
    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { performPdfExport(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOwnerStockReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerMutation.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerMutation.adapter = mutationAdapter
        setupChart()
        setupListeners()
        applyQuickFilter(7)
        loadCategories()
        loadMutationType()
        fetchData()
    }

    private fun setupChart() {
        binding.chartMutation.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setTouchEnabled(true)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textSize = 10f
                textColor = Color.parseColor("#64748B")
            }
            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#F1F5F9")
                textColor = Color.parseColor("#64748B")
            }
            axisRight.isEnabled = false
            legend.isEnabled = true
            legend.textColor = Color.parseColor("#64748B")
        }
    }

    private fun setupListeners() {
        binding.chip7Hari.setOnClickListener { applyQuickFilter(7); refresh() }
        binding.chip30Hari.setOnClickListener { applyQuickFilter(30); refresh() }
        binding.chipBulanIni.setOnClickListener { applyThisMonth(); refresh() }
        binding.chipCustom.setOnClickListener { openDatePicker() }
        binding.btnFrom.setOnClickListener { openDatePicker() }
        binding.btnTo.setOnClickListener { openDatePicker() }
        
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

        binding.btnExportExcel.setOnClickListener {
            excelLauncher.launch("Laporan_Mutasi_Stok_${System.currentTimeMillis()}.xlsx")
        }
        binding.btnExportPdf.setOnClickListener {
            pdfLauncher.launch("Laporan_Mutasi_Stok_${System.currentTimeMillis()}.pdf")
        }
        
        binding.inputCategory.setOnItemClickListener { _, _, _, _ -> refresh() }
        binding.inputMutationType.setOnItemClickListener { _, _, _, _ -> refresh() }
    }

    private fun loadCategories() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val cats = listOf("Semua") + db.productDao().listCategories()
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.inputCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, cats))
                binding.inputCategory.setText("Semua", false)
            }
        }
    }

    private fun loadMutationType() {
        binding.inputMutationType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutationTypeOptions))
        binding.inputMutationType.setText("Semua", false)
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
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Pilih Periode").setSelection(AndroidPair(fromEpochMs, toEpochMs)).build()
        picker.addOnPositiveButtonClickListener { sel ->
            fromEpochMs = sel.first ?: fromEpochMs
            toEpochMs = (sel.second ?: toEpochMs) + 86399999L
            binding.chipCustom.isChecked = true
            updateDateButtons()
            refresh()
        }
        picker.show(childFragmentManager, "date_range")
    }

    private fun refresh() {
        currentPage = 0
        fetchData()
    }

    private fun fetchData() {
        if (isLoading) return
        isLoading = true

        val category = binding.inputCategory.text?.toString().let { if (it == "Semua" || it.isNullOrBlank()) null else it }
        val typeFilter = when (binding.inputMutationType.text?.toString()) {
            "Masuk" -> "POSITIVE"
            "Keluar" -> "NEGATIVE"
            else -> null
        }

        val currentOffset = currentPage * pageSize

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val metrics = db.stockMovementDao().metrics(fromEpochMs, toEpochMs, category, typeFilter)
            val daily = db.stockMovementDao().dailyInOut(fromEpochMs, toEpochMs, category)
            
            val totalCount = db.stockMovementDao().countMutationDetails(fromEpochMs, toEpochMs, category, typeFilter)
            val details = db.stockMovementDao().mutationDetails(fromEpochMs, toEpochMs, category, typeFilter, pageSize, currentOffset)
            
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                isLoading = false
                
                binding.txtTotalIn.text = metrics.totalIn.toString()
                binding.txtTotalOut.text = metrics.totalOut.toString()
                binding.txtNetDelta.text = (metrics.totalIn - metrics.totalOut).toString()

                updateChart(daily, typeFilter)

                mutationAdapter.replaceAll(details)
                renderPagination(totalCount)
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

    private fun updateChart(daily: List<DailyInOutRow>, typeFilter: String?) {
        val days = utcDaysBetween(fromEpochMs, toEpochMs)
        val inMap = daily.associate { it.dayStartEpochMs to it.totalIn }
        val outMap = daily.associate { it.dayStartEpochMs to it.totalOut }

        val labels = days.map { dayLabelFormat.format(Date(it)) }

        val inEntries = days.mapIndexed { i, day -> BarEntry(i.toFloat(), (inMap[day] ?: 0L).toFloat()) }
        val outEntries = days.mapIndexed { i, day -> BarEntry(i.toFloat(), (outMap[day] ?: 0L).toFloat()) }

        val dataSets = when (typeFilter) {
            "POSITIVE" -> {
                val dsIn = BarDataSet(inEntries, "Masuk").apply {
                    color = Color.parseColor("#3B82F6")
                    setDrawValues(false)
                }
                listOf(dsIn)
            }
            "NEGATIVE" -> {
                val dsOut = BarDataSet(outEntries, "Keluar").apply {
                    color = Color.parseColor("#EF4444")
                    setDrawValues(false)
                }
                listOf(dsOut)
            }
            else -> {
                val dsIn = BarDataSet(inEntries, "Masuk").apply {
                    color = Color.parseColor("#3B82F6")
                    setDrawValues(false)
                }
                val dsOut = BarDataSet(outEntries, "Keluar").apply {
                    color = Color.parseColor("#EF4444")
                    setDrawValues(false)
                }
                listOf(dsIn, dsOut)
            }
        }

        val barData = BarData(dataSets).apply {
            if (dataSets.size > 1) {
                barWidth = 0.35f
            } else {
                barWidth = 0.6f
            }
        }

        binding.chartMutation.apply {
            data = barData
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = labels.size.coerceAtMost(7)
            if (dataSets.size > 1) {
                val groupSpace = 0.3f
                val barSpace = 0f
                groupBars(0f, groupSpace, barSpace)
                xAxis.setCenterAxisLabels(true)
                xAxis.axisMinimum = 0f
                xAxis.axisMaximum = days.size.toFloat()
            } else {
                xAxis.resetAxisMinimum()
                xAxis.resetAxisMaximum()
                xAxis.setCenterAxisLabels(false)
            }
            axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = value.toLong().toString()
            }
            setVisibleXRangeMaximum(14f)
            moveViewToX(barData.entryCount.toFloat())
            invalidate()
        }
    }

    private fun performExcelExport(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val wb = XSSFWorkbook()
                val sheet = wb.createSheet("Mutasi Stok")
                val boldStyle = wb.createCellStyle().apply { val f = wb.createFont(); f.bold = true; setFont(f) }
                val header = sheet.createRow(0)
                listOf("Tanggal", "Barang", "Kategori", "Masuk", "Keluar", "Sisa", "Note").forEachIndexed { i, s ->
                    header.createCell(i).also { it.setCellValue(s); it.cellStyle = boldStyle }
                }
                val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                mutationAdapter.getItems().forEachIndexed { i, row ->
                    val r = sheet.createRow(i + 1)
                    r.createCell(0).setCellValue(dateFmt.format(Date(row.createdAtEpochMs)))
                    r.createCell(1).setCellValue(row.productName)
                    r.createCell(2).setCellValue(row.category)
                    r.createCell(3).setCellValue(if (row.quantityDelta > 0) row.quantityDelta.toDouble() else 0.0)
                    r.createCell(4).setCellValue(if (row.quantityDelta < 0) (-row.quantityDelta).toDouble() else 0.0)
                    r.createCell(5).setCellValue((row.currentStock ?: 0).toDouble())
                    r.createCell(6).setCellValue(row.note ?: "")
                }
                sheet.setColumnWidth(0, 20 * 256)  // Tanggal
                sheet.setColumnWidth(1, 30 * 256)  // Barang
                sheet.setColumnWidth(2, 20 * 256)  // Kategori
                sheet.setColumnWidth(3, 10 * 256)  // Masuk
                sheet.setColumnWidth(4, 10 * 256)  // Keluar
                sheet.setColumnWidth(5, 10 * 256)  // Sisa
                sheet.setColumnWidth(6, 25 * 256)  // Note
                requireContext().contentResolver.openOutputStream(uri)?.use { wb.write(it) }
                wb.close()
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Excel Berhasil Diekspor", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun performPdfExport(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val doc = android.graphics.pdf.PdfDocument()
                val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 10f }
                val boldPaint = android.graphics.Paint(paint).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD }
                val titlePaint = android.graphics.Paint(paint).apply { textSize = 16f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
                
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas
                var y = 50f
                
                canvas.drawText("LAPORAN MUTASI STOK OWNER", 50f, y, titlePaint); y += 30f
                canvas.drawText("Periode: ${buttonDateFormat.format(Date(fromEpochMs))} - ${buttonDateFormat.format(Date(toEpochMs))}", 50f, y, paint); y += 30f
                
                canvas.drawLine(50f, y, 545f, y, paint); y += 15f
                canvas.drawText("TANGGAL", 50f, y, boldPaint)
                canvas.drawText("BARANG", 150f, y, boldPaint)
                canvas.drawText("IN", 400f, y, boldPaint)
                canvas.drawText("OUT", 450f, y, boldPaint)
                canvas.drawText("SISA", 500f, y, boldPaint); y += 10f
                canvas.drawLine(50f, y, 545f, y, paint); y += 20f
                
                val dateFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                mutationAdapter.getItems().take(35).forEach { row ->
                    canvas.drawText(dateFmt.format(Date(row.createdAtEpochMs)), 50f, y, paint)
                    canvas.drawText(row.productName.take(35), 150f, y, paint)
                    canvas.drawText(if (row.quantityDelta > 0) row.quantityDelta.toString() else "0", 400f, y, paint)
                    canvas.drawText(if (row.quantityDelta < 0) (-row.quantityDelta).toString() else "0", 450f, y, paint)
                    canvas.drawText((row.currentStock ?: 0).toString(), 500f, y, paint)
                    y += 20f
                }
                
                doc.finishPage(page)
                requireContext().contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
                doc.close()
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "PDF Berhasil Diekspor", Toast.LENGTH_SHORT).show() }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
