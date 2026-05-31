package com.kopdes.kopdesjajar.ui.admin_gudang

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.kopdes.kopdesjajar.R
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.network.RetrofitClient
import com.kopdes.kopdesjajar.data.network.SyncManager
import com.kopdes.kopdesjajar.databinding.FragmentAdminGudangDashboardBinding
import com.kopdes.kopdesjajar.ui.DashboardActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private lateinit var syncManager: SyncManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangDashboardBinding.inflate(inflater, container, false)
        syncManager = SyncManager(requireContext())
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
        startConnectionCheck()
        
        // Coba sinkron data yang tertunda saat buka dashboard
        viewLifecycleOwner.lifecycleScope.launch {
            syncManager.pushAllDataToServer()
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun setupListeners() {
        binding.cardLowStock.setOnClickListener {
            val args = Bundle().apply { putString("filter", "LOW") }
            (activity as? DashboardActivity)?.navigateTo(R.id.nav_gudang_stock, args)
        }
        binding.btnViewLowStock.setOnClickListener {
            val args = Bundle().apply { putString("filter", "LOW") }
            (activity as? DashboardActivity)?.navigateTo(R.id.nav_gudang_stock, args)
        }
        binding.btnGoToMutation.setOnClickListener {
            (activity as? DashboardActivity)?.navigateTo(R.id.nav_gudang_reports)
        }
    }

    private fun startConnectionCheck() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (_binding != null) {
                checkLaravelConnection()
                checkFirebaseConnection()
                delay(10000) 
            }
        }
    }

    private suspend fun checkLaravelConnection() {
        val isOnline = withContext(Dispatchers.IO) {
            try {
                // Gunakan endpoint ringan untuk cek koneksi
                val response = RetrofitClient.instance.syncCategories(emptyList())
                true 
            } catch (e: Exception) {
                false
            }
        }
        withContext(Dispatchers.Main) {
            _binding?.statusLaravel?.setBackgroundResource(if (isOnline) R.drawable.bg_status_online else R.drawable.bg_status_offline)
        }
    }

    private fun checkFirebaseConnection() {
        FirebaseFirestore.getInstance().collection(".info").document("connected")
            .addSnapshotListener { snapshot, _ ->
                val connected = snapshot?.getBoolean("connected") ?: false
                _binding?.statusFirebase?.setBackgroundResource(if (connected) R.drawable.bg_status_online else R.drawable.bg_status_offline)
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
                binding.txtLastUpdate.text = "Update: ${timeFmt.format(Date())}"

                binding.cardLowStockAlert.visibility = if (lowStockCount > 0) View.VISIBLE else View.GONE

                if (lowStockProducts.isEmpty()) {
                    binding.txtNoRestock.visibility = View.VISIBLE
                    binding.recyclerRestockAlert.visibility = View.GONE
                } else {
                    binding.txtNoRestock.visibility = View.GONE
                    binding.recyclerRestockAlert.visibility = View.VISIBLE
                    restockAdapter.submit(lowStockProducts)
                }

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
