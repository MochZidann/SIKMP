package com.example.myapplication.ui.owner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.databinding.FragmentOwnerInventoryHealthBinding
import com.example.myapplication.ui.UiFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class OwnerInventoryHealthFragment : Fragment() {
    private var _binding: FragmentOwnerInventoryHealthBinding? = null
    private val binding get() = _binding!!

    private val categoryAdapter = CategoryAssetAdapter()
    private val deadStockAdapter = DeadStockAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOwnerInventoryHealthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerCategoryAsset.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerDeadStock.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerCategoryAsset.adapter = categoryAdapter
        binding.recyclerDeadStock.adapter = deadStockAdapter
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val cal = Calendar.getInstance()
        val toMs = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val fromMs = cal.timeInMillis

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val totalAsset = db.productDao().totalAssetValue()
            val totalSku = db.productDao().totalProducts(null)
            val lowStockCount = db.productDao().countLowStock(null)
            val categoryAsset = db.productDao().assetValueByCategory()
            val deadStock = db.productDao().deadStockProducts(fromMs, toMs)

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.txtTotalAsset.text = UiFormat.money(totalAsset)
                binding.txtTotalSku.text = totalSku.toString()
                binding.txtLowStockCount.text = lowStockCount.toString()
                binding.txtDeadStockCount.text = deadStock.size.toString()

                categoryAdapter.submit(categoryAsset)

                if (deadStock.isEmpty()) {
                    binding.txtNoDeadStock.visibility = View.VISIBLE
                    binding.recyclerDeadStock.visibility = View.GONE
                } else {
                    binding.txtNoDeadStock.visibility = View.GONE
                    binding.recyclerDeadStock.visibility = View.VISIBLE
                    deadStockAdapter.submit(deadStock)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
