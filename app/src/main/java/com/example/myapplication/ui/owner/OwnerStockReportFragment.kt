package com.example.myapplication.ui.owner

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.databinding.FragmentOwnerStockReportBinding
import com.example.myapplication.databinding.ItemOwnerStockRowBinding
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class OwnerStockReportFragment : Fragment() {
    private var _binding: FragmentOwnerStockReportBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    private val lowStockThreshold = 10L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOwnerStockReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        
        setupSpinners()
        loadTrend()
        refresh()
    }

    private fun setupSpinners() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val categories = listOf("Semua") + db.productDao().listCategories()
            val sortOptions = listOf("Stok Terendah", "Nama Produk", "Kategori")
            
            withContext(Dispatchers.Main) {
                binding.spinnerCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories))
                binding.spinnerCategory.setText("Semua", false)
                binding.spinnerCategory.setOnItemClickListener { _, _, _, _ -> refresh() }

                binding.spinnerSort.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sortOptions))
                binding.spinnerSort.setText("Stok Terendah", false)
                binding.spinnerSort.setOnItemClickListener { _, _, _, _ -> refresh() }
            }
        }
    }

    private fun refresh() {
        val category = binding.spinnerCategory.text.toString().let { if (it == "Semua") null else it }
        val sort = binding.spinnerSort.text.toString()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            
            // Stats
            val totalProd = db.productDao().totalProducts(category)
            val totalStk = db.productDao().totalStock(category)
            val lowStk = db.productDao().countLowStock(lowStockThreshold, category)
            val outStk = db.productDao().countOutOfStock(category)

            // List
            var products = db.productDao().getAll().filter { category == null || it.category == category }
            products = when (sort) {
                "Stok Terendah" -> products.sortedBy { it.stock }
                "Nama Produk" -> products.sortedBy { it.name }
                "Kategori" -> products.sortedBy { it.category }
                else -> products
            }

            withContext(Dispatchers.Main) {
                binding.txtTotalProducts.text = totalProd.toString()
                binding.txtTotalStock.text = totalStk.toString()
                binding.txtLowStockCount.text = lowStk.toString()
                binding.txtOutOfStockCount.text = outStk.toString()
                binding.recycler.adapter = StockAdapter(products)
            }
        }
    }

    private fun loadTrend() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_YEAR, -6)
            
            val entries = mutableListOf<BarEntry>()
            val labels = mutableListOf<String>()
            val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())

            for (i in 0..6) {
                val start = cal.timeInMillis
                labels.add(sdf.format(cal.time))
                
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val end = cal.timeInMillis - 1
                
                val movements = db.stockMovementDao().dailyDelta(start, end)
                val totalDelta = movements.sumOf { it.totalDelta }
                entries.add(BarEntry(i.toFloat(), totalDelta.toFloat()))
            }

            withContext(Dispatchers.Main) {
                val dataSet = BarDataSet(entries, "Mutasi Stok")
                dataSet.color = Color.parseColor("#3B82F6")
                dataSet.valueTextSize = 10f
                
                binding.chart.apply {
                    data = BarData(dataSet)
                    xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                    xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    axisLeft.setDrawGridLines(true)
                    axisRight.isEnabled = false
                    description.isEnabled = false
                    animateY(1000)
                    invalidate()
                }
            }
        }
    }

    inner class StockAdapter(private val items: List<ProductEntity>) : RecyclerView.Adapter<StockAdapter.VH>() {
        inner class VH(val b: ItemOwnerStockRowBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemOwnerStockRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.b.txtName.text = item.name
            holder.b.txtCategory.text = item.category
            holder.b.txtStock.text = item.stock.toString()
            
            if (item.stock <= lowStockThreshold) {
                holder.b.txtStock.setTextColor(Color.RED)
            } else {
                holder.b.txtStock.setTextColor(Color.BLACK)
            }
        }
        override fun getItemCount() = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
