package com.example.myapplication.ui.admin_gudang

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
import com.example.myapplication.databinding.FragmentAdminGudangReportsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
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

class AdminGudangReportsFragment : Fragment() {
    private var _binding: FragmentAdminGudangReportsBinding? = null
    private val binding get() = _binding!!

    private val mutationAdapter = MutationDetailAdapter()
    private val buttonDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("in", "ID"))
    private val dayLabelFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

    private val DAY_MS = 86_400_000L
    private var fromEpochMs = 0L
    private var toEpochMs = 0L
    private var offset = 0
    private val pageSize = 50

    private val mutationTypeOptions = listOf("Semua", "Masuk", "Keluar")

    private val excelLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let { performExcelExport(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangReportsBinding.inflate(inflater, container, false)
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
        refresh(true)
    }

    private fun setupChart() {
        binding.chartMutation.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setTouchEnabled(true)
            setDrawValueAboveBar(true)
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
        binding.chip7Hari.setOnClickListener { applyQuickFilter(7); refresh(true) }
        binding.chip30Hari.setOnClickListener { applyQuickFilter(30); refresh(true) }
        binding.chipBulanIni.setOnClickListener { applyThisMonth(); refresh(true) }
        binding.chipCustom.setOnClickListener { openDatePicker() }
        binding.btnFrom.setOnClickListener { openDatePicker() }
        binding.btnTo.setOnClickListener { openDatePicker() }
        binding.btnLoadMore.setOnClickListener { offset += pageSize; refresh(false) }
        binding.btnExportExcel.setOnClickListener {
            excelLauncher.launch("Laporan_Mutasi_Gudang_${System.currentTimeMillis()}.xlsx")
        }
        binding.inputCategory.setOnItemClickListener { _, _, _, _ -> refresh(true) }
        binding.inputMutationType.setOnItemClickListener { _, _, _, _ -> refresh(true) }
    }

    private fun loadCategories() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val cats = listOf("Semua") + db.productDao().listCategories()
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.inputCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, cats))
                if (binding.inputCategory.text.isNullOrBlank()) binding.inputCategory.setText("Semua", false)
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
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
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
            refresh(true)
        }
        picker.show(childFragmentManager, "date_range")
    }

    private fun refresh(reset: Boolean) {
        if (reset) {
            offset = 0
            mutationAdapter.replaceAll(emptyList())
        }
        val category = binding.inputCategory.text?.toString().let { if (it == "Semua" || it.isNullOrBlank()) null else it }
        val typeFilter = when (binding.inputMutationType.text?.toString()) {
            "Masuk" -> "POSITIVE"
            "Keluar" -> "NEGATIVE"
            else -> null
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val metrics = db.stockMovementDao().metrics(fromEpochMs, toEpochMs, category, typeFilter)
            val daily = db.stockMovementDao().dailyInOut(fromEpochMs, toEpochMs, category)
            val details = db.stockMovementDao().mutationDetails(fromEpochMs, toEpochMs, category, typeFilter, pageSize, offset)

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                
                binding.txtTotalIn.text = metrics.totalIn.toString()
                binding.txtTotalOut.text = metrics.totalOut.toString()
                binding.txtNetDelta.text = (metrics.totalIn - metrics.totalOut).toString()

                updateChart(daily, typeFilter)

                if (reset) mutationAdapter.replaceAll(details) else mutationAdapter.append(details)
                binding.btnLoadMore.visibility = if (details.size == pageSize) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updateChart(daily: List<DailyInOutRow>, typeFilter: String?) {
        val utcDays = utcDaysBetween(fromEpochMs, toEpochMs)
        val labels = utcDays.map { dayLabelFormat.format(Date(it)) }
        val inMap = daily.associate { it.dayStartEpochMs to it.totalIn }
        val outMap = daily.associate { it.dayStartEpochMs to it.totalOut }

        val dataSets = mutableListOf<BarDataSet>()
        
        val intFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = value.toInt().toString()
        }

        if (typeFilter == null || typeFilter == "POSITIVE") {
            val entriesIn = utcDays.mapIndexed { i, d -> BarEntry(i.toFloat(), (inMap[d] ?: 0L).toFloat()) }
            dataSets.add(BarDataSet(entriesIn, "Hijau Masuk").apply {
                color = Color.parseColor("#10B981")
                setDrawValues(true)
                valueTextSize = 10f
                valueTextColor = Color.parseColor("#64748B")
                valueFormatter = intFormatter
            })
        }

        if (typeFilter == null || typeFilter == "NEGATIVE") {
            val entriesOut = utcDays.mapIndexed { i, d -> BarEntry(i.toFloat(), (outMap[d] ?: 0L).toFloat()) }
            dataSets.add(BarDataSet(entriesOut, "Merah Keluar").apply {
                color = Color.parseColor("#EF4444")
                setDrawValues(true)
                valueTextSize = 10f
                valueTextColor = Color.parseColor("#64748B")
                valueFormatter = intFormatter
            })
        }

        binding.chartMutation.apply {
            data = if (dataSets.isNotEmpty()) {
                BarData(*dataSets.toTypedArray()).apply { 
                    barWidth = if (dataSets.size > 1) 0.35f else 0.7f 
                }
            } else null
            
            if (dataSets.size > 1) {
                groupBars(0f, 0.2f, 0.05f)
            }
            
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = labels.size.coerceAtMost(7)
            animateY(500)
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
                listOf("Tanggal", "Nama Barang", "Kategori", "Masuk", "Keluar", "Sisa", "Note").forEachIndexed { i, s ->
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
                    r.createCell(5).setCellValue((row.currentStock ?: 0L).toDouble())
                    r.createCell(6).setCellValue(row.note ?: "-")
                }
                for (i in 0..6) sheet.autoSizeColumn(i)
                requireContext().contentResolver.openOutputStream(uri)?.use { wb.write(it) }
                wb.close()
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "✅ Export Berhasil", Toast.LENGTH_SHORT).show() }
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
