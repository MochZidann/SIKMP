package com.example.myapplication.ui.owner

import com.example.myapplication.ui.DashboardActivity
import com.example.myapplication.ui.UiFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.databinding.FragmentOwnerDashboardBinding
import com.example.myapplication.ui.owner.OwnerLatestSalesAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class OwnerDashboardFragment : Fragment() {
    private var _binding: FragmentOwnerDashboardBinding? = null
    private val binding get() = _binding!!
    private val salesAdapter = OwnerLatestSalesAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOwnerDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.recyclerRecentSales.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRecentSales.adapter = salesAdapter

        binding.btnStockReport.setOnClickListener { (activity as? DashboardActivity)?.navigateTo(R.id.nav_owner_stock_report) }
        binding.btnSalesReport.setOnClickListener { (activity as? DashboardActivity)?.navigateTo(R.id.nav_owner_sales_report) }
        
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val (from, to) = todayRange()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            
            // Stats
            val summary = db.salesDao().summary(from, to)
            val lowStockCount = db.productDao().countLowStock(10, null)
            
            // Recent Sales
            val recentSales = db.salesDao().listSalesBetween(from, to).takeLast(5).reversed()
            val cashierNames = db.userDao().getAll().associateBy({ it.id }, { it.username })
            
            val saleRows = recentSales.map { s ->
                com.example.myapplication.ui.owner.OwnerLatestSaleRow(
                    id = s.id,
                    cashierName = cashierNames[s.cashierId] ?: "Unknown",
                    total = UiFormat.money(s.total),
                    time = UiFormat.dateTime(s.createdAtEpochMs)
                )
            }

            withContext(Dispatchers.Main) {
                binding.txtTotalToday.text = UiFormat.money(summary.total)
                binding.txtLowStock.text = lowStockCount.toString()
                salesAdapter.submit(saleRows)
            }
        }
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


