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

class AdminGudangReportsFragment : Fragment() {
    private var _binding: FragmentAdminGudangReportsBinding? = null
    private val binding get() = _binding!!

    private val labelFormat = SimpleDateFormat("dd/MM", Locale("in", "ID"))
    private val buttonDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("in", "ID"))
    private val adapter = AdminGudangSaleDetailAdapter()
    private val dayMs = 86_400_000L

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
            val range = last7DaysRange()
            fromEpochMs = range.first
            toEpochMs = range.second
            updateDateButtons()
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
        if (binding.recyclerDetails.adapter == null) {
            binding.recyclerDetails.adapter = adapter
            binding.btnFrom.setOnClickListener { openDateRangePicker() }
            binding.btnTo.setOnClickListener { openDateRangePicker() }
            binding.btnApply.setOnClickListener { applyFilters(resetList = true) }
            binding.btnReset.setOnClickListener { resetFilters() }
            binding.btnLoadMore.setOnClickListener { loadMore() }
        }
    }

    private fun resetFilters() {
        val range = last7DaysRange()
        fromEpochMs = range.first
        toEpochMs = range.second
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

    private fun loadCategories() {
        if (binding.inputCategory.adapter != null) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val categories = db.productDao().listCategories()
            withContext(Dispatchers.Main) {
                val items = listOf("Semua") + categories
                binding.inputCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items))
                if (binding.inputCategory.text.isNullOrBlank()) binding.inputCategory.setText("Semua", false)
            }
        }
    }

    private fun selectedCategory(): String? {
        val v = binding.inputCategory.text?.toString()?.trim().orEmpty()
        return if (v.isBlank() || v == "Semua") null else v
    }

    private fun loadPage(resetList: Boolean) {
        val category = selectedCategory()
        val limit = pageSize
        val pageOffset = offset
        val range = chartRangeWithinFilter(fromEpochMs, toEpochMs)
        val chartFrom = range.first
        val chartTo = range.second
        val chartDays = dayStartsBetween(chartFrom, chartTo).takeLast(7)
        val chartLabels = chartDays.map { labelFormat.format(Date(it)) }

        loading = true
        binding.btnLoadMore.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val metrics = db.salesDao().metrics(fromEpochMs, toEpochMs, category)
            val best = db.salesDao().bestSeller(fromEpochMs, toEpochMs, category)
            val daily = db.salesDao().dailyTotals(chartFrom, chartTo, category).associateBy({ it.dayStartEpochMs }, { it.total })
            val chartValues = chartDays.map { daily[it] ?: 0L }
            val page = db.salesDao().saleItemDetails(fromEpochMs, toEpochMs, category, limit, pageOffset)

            withContext(Dispatchers.Main) {
                binding.txtTotalTrx.text = metrics.txnCount.toString()
                binding.txtTotalRevenue.text = UiFormat.money(metrics.revenue)
                binding.txtTotalItems.text = metrics.itemsSold.toString()
                binding.txtBestSeller.text = if (best == null) "-" else "${best.productName} • ${best.quantity}"
                binding.chart.setData(chartLabels, chartValues)

                if (resetList) adapter.replaceAll(page) else adapter.append(page)
                offset += page.size
                hasMore = page.size == limit
                binding.btnLoadMore.visibility = if (hasMore) View.VISIBLE else View.GONE
                binding.btnLoadMore.isEnabled = true
                loading = false
            }
        }
    }

    private fun openDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Pilih Periode")
            .setSelection(AndroidPair(fromEpochMs, toEpochMs))
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            fromEpochMs = dayStart(selection.first)
            toEpochMs = dayEnd(selection.second)
            updateDateButtons()
        }
        picker.show(childFragmentManager, "date_range")
    }

    private fun updateDateButtons() {
        binding.btnFrom.text = buttonDateFormat.format(Date(fromEpochMs))
        binding.btnTo.text = buttonDateFormat.format(Date(toEpochMs))
    }

    private fun last7DaysRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_MONTH, -6)
        val from = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 7)
        val to = cal.timeInMillis - 1
        return from to to
    }

    private fun chartRangeWithinFilter(from: Long, to: Long): Pair<Long, Long> {
        val days = dayStartsBetween(from, to)
        val chosen = if (days.size <= 7) days else days.takeLast(7)
        val chartFrom = chosen.firstOrNull() ?: from
        val chartTo = dayEnd(chosen.lastOrNull() ?: to)
        return chartFrom to chartTo
    }

    private fun dayStartsBetween(from: Long, to: Long): List<Long> {
        val start = dayStart(from)
        val end = dayStart(to)
        val out = ArrayList<Long>()
        var t = start
        while (t <= end) {
            out.add(t)
            t += dayMs
        }
        return out
    }

    private fun dayStart(epochMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun dayEnd(epochMs: Long): Long {
        return dayStart(epochMs) + dayMs - 1
    }
}
