package com.example.myapplication.ui.admin_gudang

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.databinding.FragmentAdminGudangDashboardBinding
import com.example.myapplication.ui.UiFormat
import com.example.myapplication.ui.adapters.TwoLineAdapter
import com.example.myapplication.ui.adapters.TwoLineRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminGudangDashboardFragment : Fragment() {
    private var _binding: FragmentAdminGudangDashboardBinding? = null
    private val binding get() = _binding!!

    private val latestAdapter = TwoLineAdapter { }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerLatest.adapter = latestAdapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val productCount = db.productDao().totalProducts(category = null).toInt()
            val lowStockCount = db.productDao().countLowStock(threshold = 6, category = null).toInt()
            val latest = db.auditLogDao().latest(8)
            val rows = latest.map {
                TwoLineRow(
                    id = it.id,
                    title = "${it.action} • ${it.entity}",
                    subtitle = "${UiFormat.dateTime(it.createdAtEpochMs)} • ${it.detail.orEmpty()}"
                )
            }

            withContext(Dispatchers.Main) {
                binding.txtProductCount.text = productCount.toString()
                binding.txtLowStockCount.text = lowStockCount.toString()
                latestAdapter.submit(rows)
            }
        }
    }
}
