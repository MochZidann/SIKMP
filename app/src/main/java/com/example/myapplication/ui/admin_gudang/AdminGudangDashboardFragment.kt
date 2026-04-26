package com.example.myapplication.ui.admin_gudang

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.StockMovementEntity
import com.example.myapplication.databinding.FragmentAdminGudangDashboardBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AdminGudangDashboardFragment : Fragment() {
    private var _binding: FragmentAdminGudangDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupChart() {
        binding.chartStockMutation.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setTouchEnabled(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = Color.parseColor("#64748B")
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#F1F5F9")
                textColor = Color.parseColor("#64748B")
                axisMinimum = 0f
            }
            
            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val productCount = db.productDao().totalProducts(category = null).toInt()
            val lowStockCount = db.productDao().countLowStock(threshold = 6, category = null).toInt()
            
            // Fetch movements for last 7 days
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -6)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            val startTime = calendar.timeInMillis
            
            val movements = db.stockMovementDao().latest(200).filter { it.createdAtEpochMs >= startTime }
            
            withContext(Dispatchers.Main) {
                binding.txtProductCount.text = productCount.toString()
                binding.txtLowStockCount.text = lowStockCount.toString()
                updateChartData(movements)
            }
        }
    }

    private fun updateChartData(movements: List<StockMovementEntity>) {
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        val last7Days = (0..6).map { i ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, - (6 - i))
            cal.timeInMillis to sdf.format(cal.time)
        }

        val entriesIn = ArrayList<BarEntry>()
        val entriesOut = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        last7Days.forEachIndexed { index, (time, label) ->
            val dayStart = Calendar.getInstance().apply { 
                timeInMillis = time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
            }.timeInMillis
            val dayEnd = dayStart + 86400000L

            val inQty = movements.filter { 
                it.createdAtEpochMs in dayStart until dayEnd && it.quantityDelta > 0 
            }.sumOf { it.quantityDelta }
            
            val outQty = movements.filter { 
                it.createdAtEpochMs in dayStart until dayEnd && it.quantityDelta < 0 
            }.sumOf { -it.quantityDelta }

            entriesIn.add(BarEntry(index.toFloat(), inQty.toFloat()))
            entriesOut.add(BarEntry(index.toFloat(), outQty.toFloat()))
            labels.add(label)
        }

        val dataSetIn = BarDataSet(entriesIn, "Barang Masuk").apply {
            color = Color.parseColor("#10B981") // Green
            setDrawValues(false)
        }
        
        val dataSetOut = BarDataSet(entriesOut, "Barang Keluar").apply {
            color = Color.parseColor("#EF4444") // Red
            setDrawValues(false)
        }

        binding.chartStockMutation.apply {
            val barData = BarData(dataSetIn, dataSetOut)
            barData.barWidth = 0.35f
            data = barData
            
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            
            groupBars(-0.5f, 0.3f, 0.05f)
            invalidate()
        }
    }
}
