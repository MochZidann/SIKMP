package com.example.myapplication.ui.owner

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.model.Role
import com.example.myapplication.databinding.ActivityOwnerStockReportBinding
import com.example.myapplication.ui.BaseAuthedActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OwnerStockReportActivity : BaseAuthedActivity() {
    private lateinit var binding: ActivityOwnerStockReportBinding
    private val adapter = OwnerStockReportAdapter()

    override fun allowedRoles(): Set<Role> = setOf(Role.OWNER_PENGAWAS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOwnerStockReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recycler.adapter = adapter

        binding.spinnerSort.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                listOf("Stok terendah", "Nama produk")
            )
        )
        binding.spinnerSort.setText("Stok terendah", false)

        binding.spinnerCategory.setOnItemClickListener { _, _, _, _ -> refresh() }
        binding.spinnerSort.setOnItemClickListener { _, _, _, _ -> refresh() }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            AuditLogger.log(
                context = this@OwnerStockReportActivity,
                userId = session.userId(),
                action = "VIEW_STOCK_REPORT",
                entity = "stock_report",
                entityId = null
            )
        }
        loadCategoriesAndRefresh()
        loadTrend()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadCategoriesAndRefresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@OwnerStockReportActivity)
            val categories = db.productDao().listCategories()
            withContext(Dispatchers.Main) {
                val items = listOf("Semua") + categories
                binding.spinnerCategory.setAdapter(ArrayAdapter(this@OwnerStockReportActivity, android.R.layout.simple_list_item_1, items))
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
            val db = AppDatabase.get(this@OwnerStockReportActivity)
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
            val db = AppDatabase.get(this@OwnerStockReportActivity)
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
}

