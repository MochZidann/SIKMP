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

    private val movementAdapter = TwoLineAdapter { }
    private val lowStockAdapter = TwoLineAdapter { }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerMovements.adapter = movementAdapter
        binding.recyclerLowStock.adapter = lowStockAdapter
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
            val products = db.productDao().getAll()
            val movements = db.stockMovementDao().latest(10)

            val productCount = products.size
            val stockTotal = products.sumOf { it.stock }

            val movementRows = movements.map {
                TwoLineRow(
                    id = it.id,
                    title = "${it.type} • Produk #${it.productId}",
                    subtitle = "${UiFormat.dateTime(it.createdAtEpochMs)} • Δ ${it.quantityDelta}"
                )
            }

            val lowStockRows = products
                .filter { it.stock <= 5 }
                .sortedBy { it.stock }
                .take(10)
                .map {
                    TwoLineRow(
                        id = it.id,
                        title = it.name,
                        subtitle = "Stok: ${it.stock} • ${it.category}"
                    )
                }

            withContext(Dispatchers.Main) {
                binding.txtProductCount.text = productCount.toString()
                binding.txtStockTotal.text = stockTotal.toString()
                movementAdapter.submit(movementRows)
                lowStockAdapter.submit(lowStockRows)
            }
        }
    }
}
