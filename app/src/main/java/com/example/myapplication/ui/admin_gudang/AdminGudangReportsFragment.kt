package com.example.myapplication.ui.admin_gudang

import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.core.util.Pair as AndroidPair
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.databinding.FragmentAdminGudangReportsBinding
import com.example.myapplication.ui.UiFormat
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AdminGudangReportsFragment : Fragment() {
    private var _binding: FragmentAdminGudangReportsBinding? = null
    private val binding get() = _binding!!

    private val labelFormat = SimpleDateFormat("dd/MM", Locale("in", "ID"))
    private val buttonDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("in", "ID"))
    private val adapter = AdminGudangSaleDetailAdapter()

    // UTC constant - must match DAO's (epoch / 86400000) grouping
    private val DAY_MS = 86_400_000L

    private var fromEpochMs: Long = 0L
    private var toEpochMs: Long = 0L
    private var offset: Int = 0
    private var hasMore: Boolean = true
    private var loading: Boolean = false
    private val pageSize: Int = 20

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (fromEpochMs == 0L || toEpochMs == 0L) {
            applyQuickFilter(7)
        }
        setupUiIfNeeded()
        loadCategories()
        applyFilters(resetList = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupUiIfNeeded() {
        if (binding.recyclerDetails.adapter != null) return
        binding.recyclerDetails.adapter = adapter

        // Quick filter chips
        binding.chip7Hari.setOnClickListener { applyQuickFilter(7); applyFilters(true) }
        binding.chip30Hari.setOnClickListener { applyQuickFilter(30); applyFilters(true) }
        binding.chipBulanIni.setOnClickListener { applyThisMonth(); applyFilters(true) }
        binding.chipCustom.setOnClickListener { openDateRangePicker() }

        // Date buttons open picker
        binding.btnFrom.setOnClickListener { openDateRangePicker() }
        binding.btnTo.setOnClickListener { openDateRangePicker() }

        binding.btnApply.setOnClickListener { applyFilters(resetList = true) }
        binding.btnReset.setOnClickListener {
            applyQuickFilter(7)
            binding.inputCategory.setText("Semua", false)
            applyFilters(resetList = true)
        }
        binding.btnLoadMore.setOnClickListener { loadMore() }

        // Default chip
        binding.chip7Hari.isChecked = true
    }

    // --- Quick filters ---

    private fun applyQuickFilter(days: Int) {
        val cal = Calendar.getInstance()
        toEpochMs = localDayEnd(cal.timeInMillis)
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1))
        fromEpochMs = localDayStart(cal.timeInMillis)
        updateDateButtons()
    }

    private fun applyThisMonth() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        fromEpochMs = localDayStart(cal.timeInMillis)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        toEpochMs = localDayEnd(cal.timeInMillis)
        updateDateButtons()
    }

    // --- Filters ---

    private fun resetFilters() {
        applyQuickFilter(7)
        binding.inputCategory.setText("Semua", false)
        updateDateButtons()
        applyFilters(resetList = true)
    }

    private fun applyFilters(resetList: Boolean) {
        if (loading) return
        if (resetList) {
            offset = 0
            hasMore = true
            adapter.replaceAll(emptyList())
        }
        loadPage(resetList = resetList)
    }

    private fun loadMore() {
        if (loading || !hasMore) return
        loadPage(resetList = false)
    }

    // --- Categories ---

    private fun loadCategories() {
        if (binding.inputCategory.adapter != null) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val categories = db.productDao().listCategories()
            withContext(Dispatchers.Main) {
                val items = listOf("Semua") + categories
                binding.inputCategory.setAdapter(
                    ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
                )
                if (binding.inputCategory.text.isNullOrBlank()) {
                    binding.inputCategory.setText("Semua", false)
                }
            }
        }
    }

    private fun selectedCategory(): String? {
        val v = binding.inputCategory.text?.toString()?.trim().orEmpty()
        return if (v.isBlank() || v == "Semua") null else v
    }

    // --- Data loading ---

    private fun loadPage(resetList: Boolean) {
        val category = selectedCategory()
        val limit = pageSize
        val pageOffset = offset

        // *** FIX: Use UTC-based day starts to match DAO grouping ***
        // DAO uses: (createdAtEpochMs / 86400000) as dayKey, dayKey*86400000 as dayStartEpochMs
        // These are UTC midnight boundaries. We must use the same here.
        val utcDays = utcDaysBetween(fromEpochMs, toEpochMs)
        // Labels: format each UTC midnight as local date string (auto-converts via Date)
        val chartLabels = utcDays.map { labelFormat.format(Date(it)) }

        loading = true
        binding.btnLoadMore.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val metrics = db.salesDao().metrics(fromEpochMs, toEpochMs, category)
            val best = db.salesDao().bestSeller(fromEpochMs, toEpochMs, category)

            // daily map key = dayKey * 86400000 (UTC midnight) — matches utcDays list
            val dailyList = db.salesDao().dailyTotals(fromEpochMs, toEpochMs, category)
            val dailyMap = dailyList.associate { it.dayStartEpochMs to it.total }
            val chartValues = utcDays.map { dailyMap[it] ?: 0L }

            val page = db.salesDao().saleItemDetails(fromEpochMs, toEpochMs, category, limit, pageOffset)

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext

                binding.txtTotalTrx.text = metrics.txnCount.toString()
                binding.txtTotalRevenue.text = UiFormat.money(metrics.revenue)
                binding.txtTotalItems.text = metrics.itemsSold.toString()
                binding.txtBestSeller.text = if (best == null) "-"
                    else "${best.productName} (${best.quantity} terjual)"

                val rangeLabel = "${buttonDateFormat.format(Date(fromEpochMs))} - ${buttonDateFormat.format(Date(toEpochMs))}"
                binding.txtChartTitle.text = "Grafik Pendapatan ($rangeLabel)"

                val snapValues = chartValues
                val snapLabels = chartLabels
                binding.chart.post {
                    if (_binding != null) {
                        binding.chart.setData(snapLabels, snapValues)
                    }
                }

                if (resetList) adapter.replaceAll(page) else adapter.append(page)
                offset += page.size
                hasMore = page.size == limit
                binding.btnLoadMore.visibility = if (hasMore) View.VISIBLE else View.GONE
                binding.btnLoadMore.isEnabled = true
                loading = false
            }
        }
    }

    // --- Date picker ---

    private fun openDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Pilih Periode")
            .setSelection(AndroidPair(fromEpochMs, toEpochMs))
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            fromEpochMs = localDayStart(selection.first ?: System.currentTimeMillis())
            toEpochMs   = localDayEnd(selection.second ?: System.currentTimeMillis())
            binding.chipCustom.isChecked = true
            updateDateButtons()
            applyFilters(resetList = true)
        }
        picker.show(childFragmentManager, "date_range")
    }

    private fun updateDateButtons() {
        binding.btnFrom.text = buttonDateFormat.format(Date(fromEpochMs))
        binding.btnTo.text   = buttonDateFormat.format(Date(toEpochMs))
    }

    // --- Day helpers (local timezone, for querying boundaries) ---

    private fun localDayStart(epochMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun localDayEnd(epochMs: Long): Long = localDayStart(epochMs) + DAY_MS - 1

    // --- UTC day helpers (must match DAO's epoch/86400000 grouping) ---

    private fun utcDayStart(epochMs: Long): Long = (epochMs / DAY_MS) * DAY_MS

    private fun utcDaysBetween(from: Long, to: Long): List<Long> {
        val start = utcDayStart(from)
        val end   = utcDayStart(to)
        val result = ArrayList<Long>()
        var t = start
        while (t <= end) {
            result.add(t)
            t += DAY_MS
        }
        return result
    }
}
