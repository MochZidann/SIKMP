package com.example.myapplication.ui.owner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.databinding.FragmentOwnerStockReportBinding
import com.example.myapplication.ui.UiFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OwnerStockReportFragment : Fragment() {
    private var _binding: FragmentOwnerStockReportBinding? = null
    private val binding get() = _binding!!
    private val adapter = OwnerStockReportAdapter()
    private lateinit var session: SessionManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOwnerStockReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        binding.recycler.adapter = adapter

        binding.spinnerSort.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                listOf("Stok terendah", "Nama produk")
            )
        )
        binding.spinnerSort.setText("Stok terendah", false)

        binding.spinnerCategory.setOnItemClickListener { _, _, _, _ -> refresh() }
        binding.spinnerSort.setOnItemClickListener { _, _, _, _ -> refresh() }
        
        loadCategoriesAndRefresh()
        loadTrend()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            AuditLogger.log(
                context = requireContext(),
                userId = session.userId(),
                action = "VIEW_STOCK_REPORT",
                entity = "stock_report",
                entityId = null
            )
        }
    }

    private fun loadCategoriesAndRefresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val categories = db.productDao().listCategories()
            withContext(Dispatchers.Main) {
                val items = listOf("Semua") + categories
                binding.spinnerCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items))
                if (binding.spinnerCategory.text.isNullOrBlank()) binding.spinnerCategory.setText("Semua", false)
                refresh()
            }
        }
    }

    private fun selectedCategory(): String? {
        val v = binding.spinnerCategory.text?.toString()?.trim().orEmpty()
        return if (v.isBlank() || v == "Semua") null else v
    }

    private fun selectedSortByStock(): Boolean {
        val v = binding.spinnerSort.text?.toString()?.trim().orEmpty()
        return v != "Nama produk"
    }

    private fun refresh() {
        val category = selectedCategory()
        val sortByStock = selectedSortByStock()
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val products = if (sortByStock) db.productDao().listByCategoryOrderByStockAsc(category) else db.productDao().listByCategoryOrderByName(category)

            val totalProducts = db.productDao().totalProducts(category)
            val totalStock = db.productDao().totalStock(category)
            val lowCount = db.productDao().countLowStock(10, category)
            val outCount = db.productDao().countOutOfStock(category)

            withContext(Dispatchers.Main) {
                binding.txtTotalProducts.text = totalProducts.toString()
                binding.txtTotalStock.text = totalStock.toString()
                binding.txtLowStockCount.text = lowCount.toString()
                binding.txtOutOfStockCount.text = outCount.toString()
                adapter.submit(products)
            }
        }
    }

    private fun loadTrend() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val (from, to) = last7DaysRange()
            val points = db.stockMovementDao().dailyDelta(from, to)
            val byDay = points.associateBy({ it.dayStartEpochMs }, { it.totalDelta })
            val days = last7DayStarts()
            val labelFmt = SimpleDateFormat("dd/MM", Locale("in", "ID"))
            val labels = days.map { labelFmt.format(Date(it)) }
            val values = days.map { byDay[it] ?: 0L }
            withContext(Dispatchers.Main) {
                binding.chart.setData(labels, values)
            }
        }
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

    private fun last7DayStarts(): List<Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_MONTH, -6)
        return (0..6).map {
            val v = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            v
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

