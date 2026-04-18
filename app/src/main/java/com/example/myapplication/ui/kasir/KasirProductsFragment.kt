package com.example.myapplication.ui.kasir

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.databinding.FragmentKasirProductsBinding
import com.example.myapplication.ui.kasir.KasirProductGridAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KasirProductsFragment : Fragment() {
    private var _binding: FragmentKasirProductsBinding? = null
    private val binding get() = _binding!!
    private var adapter: KasirProductGridAdapter? = null
    private var allProducts: List<ProductEntity> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKasirProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = KasirProductGridAdapter { }
        binding.recycler.adapter = adapter
        
        binding.tabs.addTab(binding.tabs.newTab().setText("Semua Produk"))
        binding.tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) { applyFilter() }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
        binding.search.doAfterTextChanged { applyFilter() }
        
        refresh()
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val products = db.productDao().getAll()
            withContext(Dispatchers.Main) {
                allProducts = products
                ensureTabs(products)
                applyFilter()
            }
        }
    }

    private fun ensureTabs(products: List<ProductEntity>) {
        if (binding.tabs.tabCount > 1) return
        val categories = products.map { it.category }.distinct().sorted()
        for (c in categories) {
            binding.tabs.addTab(binding.tabs.newTab().setText(c))
        }
    }

    private fun applyFilter() {
        val query = binding.search.text?.toString()?.trim().orEmpty().lowercase()
        val selected = binding.tabs.getTabAt(binding.tabs.selectedTabPosition)?.text?.toString().orEmpty()
        val filtered = allProducts.filter { p ->
            val matchText = query.isBlank() || p.name.lowercase().contains(query) || p.category.lowercase().contains(query)
            val matchCategory = selected == "Semua Produk" || selected.isBlank() || p.category.equals(selected, ignoreCase = true)
            matchText && matchCategory
        }.sortedBy { it.name }
        adapter?.submit(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


