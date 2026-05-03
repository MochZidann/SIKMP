package com.example.myapplication.ui.admin_gudang

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.databinding.FragmentAdminGudangDashboardBinding
import com.example.myapplication.ui.DashboardActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AdminGudangDashboardFragment : Fragment() {
    private var _binding: FragmentAdminGudangDashboardBinding? = null
    private val binding get() = _binding!!

    private val restockAdapter = RestockAlertAdapter()
    private val activityAdapter = ActivityLogAdapter()
    private val timeFmt = SimpleDateFormat("HH:mm, dd MMM", Locale("in", "ID"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerRestockAlert.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerActivityLog.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerRestockAlert.adapter = restockAdapter
        binding.recyclerActivityLog.adapter = activityAdapter

        setupListeners()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun setupListeners() {
        // Klik Stok Rendah → pergi ke Kelola Stok dengan filter LOW
        binding.cardLowStock.setOnClickListener {
            val args = Bundle().apply { putString("filter", "LOW") }
            (activity as? DashboardActivity)?.navigateTo(R.id.nav_gudang_stock, args)
        }

        // Banner: Lihat stok kritis
        binding.btnViewLowStock.setOnClickListener {
            val args = Bundle().apply { putString("filter", "LOW") }
            (activity as? DashboardActivity)?.navigateTo(R.id.nav_gudang_stock, args)
        }

        // Tombol ke Mutasi Stok
        binding.btnGoToMutation.setOnClickListener {
            (activity as? DashboardActivity)?.navigateTo(R.id.nav_gudang_reports)
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val totalSku = db.productDao().totalProducts(null)
            val totalStock = db.productDao().totalStock(null)
            val totalCategories = db.productDao().countCategories()
            val lowStockCount = db.productDao().countLowStock(null)
            val lowStockProducts = db.productDao().lowStockList(20)
            val activityLog = db.stockMovementDao().latestWithProductName(10)

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext

                binding.txtProductCount.text = totalSku.toString()
                binding.txtTotalStock.text = totalStock.toString()
                binding.txtTotalCategories.text = totalCategories.toString()
                binding.txtLowStockCount.text = lowStockCount.toString()
                binding.txtLastUpdate.text = "Diperbarui: ${timeFmt.format(Date())}"

                // Low stock alert banner
                if (lowStockCount > 0) {
                    binding.cardLowStockAlert.visibility = View.VISIBLE
                } else {
                    binding.cardLowStockAlert.visibility = View.GONE
                }

                // Restock table
                if (lowStockProducts.isEmpty()) {
                    binding.txtNoRestock.visibility = View.VISIBLE
                    binding.recyclerRestockAlert.visibility = View.GONE
                } else {
                    binding.txtNoRestock.visibility = View.GONE
                    binding.recyclerRestockAlert.visibility = View.VISIBLE
                    restockAdapter.submit(lowStockProducts)
                }

                // Activity log
                if (activityLog.isEmpty()) {
                    binding.txtNoActivity.visibility = View.VISIBLE
                    binding.recyclerActivityLog.visibility = View.GONE
                } else {
                    binding.txtNoActivity.visibility = View.GONE
                    binding.recyclerActivityLog.visibility = View.VISIBLE
                    activityAdapter.submit(activityLog)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
