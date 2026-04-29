package com.example.myapplication.ui.kasir

import com.example.myapplication.ui.UiFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.databinding.FragmentKasirPosBinding
import com.example.myapplication.ui.DashboardActivity
import com.example.myapplication.ui.kasir.KasirCartAdapter
import com.example.myapplication.ui.kasir.KasirCartLine
import com.example.myapplication.ui.kasir.KasirProductGridAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KasirPosFragment : Fragment() {
    private var _binding: FragmentKasirPosBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    
    private val productAdapter = KasirProductGridAdapter { product -> addToCart(product) }
    private val cartAdapter = KasirCartAdapter(
        onPlus = { id -> changeQty(id, 1) },
        onMinus = { id -> changeQty(id, -1) },
        onRemove = { id -> removeFromCart(id) }
    )
    
    private val cartQty = linkedMapOf<Long, Long>()
    private var productsById: Map<Long, ProductEntity> = emptyMap()
    private var visibleProducts: List<ProductEntity> = emptyMap<Long, ProductEntity>().values.toList()
    
    private var currentTotal: Long = 0
    private var currentSubtotal: Long = 0
    private var currentDiscount: Long = 0
    private var currentTax: Long = 0
    private var settings: com.example.myapplication.data.db.SettingsEntity = com.example.myapplication.data.db.SettingsEntity()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKasirPosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        
        binding.recyclerProducts.adapter = productAdapter
        binding.recyclerCart.adapter = cartAdapter
        binding.recyclerCart.layoutManager = LinearLayoutManager(requireContext())

        binding.txtCurrentDate.text = SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()).format(Date())

        binding.search.doAfterTextChanged { applyFilter() }
        binding.tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) { applyFilter() }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
        
        binding.btnPay.setOnClickListener { pay() }

        loadProducts()
    }

    private fun loadProducts() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            settings = db.settingsDao().get() ?: com.example.myapplication.data.db.SettingsEntity()
            val products = db.productDao().getAll()
            productsById = products.associateBy { it.id }
            withContext(Dispatchers.Main) {
                ensureTabs(products)
                visibleProducts = products
                applyFilter()
                renderCart()
            }
        }
    }

    private fun ensureTabs(products: List<ProductEntity>) {
        if (binding.tabs.tabCount > 0) return
        binding.tabs.addTab(binding.tabs.newTab().setText("SEMUA"))
        val categories = products.map { it.category }.distinct().sorted()
        for (c in categories) {
            binding.tabs.addTab(binding.tabs.newTab().setText(c.uppercase()))
        }
    }

    private fun applyFilter() {
        val query = binding.search.text?.toString()?.trim().orEmpty().lowercase()
        val selected = binding.tabs.getTabAt(binding.tabs.selectedTabPosition)?.text?.toString().orEmpty()
        val base = productsById.values.toList()
        val filtered = base.filter { p ->
            val matchText = query.isBlank() || p.name.lowercase().contains(query) || p.category.lowercase().contains(query) || p.barcode?.contains(query) == true
            val matchCategory = selected.isBlank() || selected == "SEMUA" || p.category.equals(selected, ignoreCase = true)
            matchText && matchCategory
        }.sortedBy { it.name }
        visibleProducts = filtered
        productAdapter.submit(visibleProducts)
    }

    private fun addToCart(product: ProductEntity) {
        val current = cartQty[product.id] ?: 0L
        if (current + 1L > product.stock) {
            Toast.makeText(requireContext(), "Stok Habis!", Toast.LENGTH_SHORT).show()
            return
        }
        cartQty[product.id] = current + 1L
        renderCart()
    }

    private fun changeQty(productId: Long, delta: Long) {
        val product = productsById[productId] ?: return
        val current = cartQty[productId] ?: return
        val next = current + delta
        if (next > product.stock) {
            Toast.makeText(requireContext(), "Stok Terbatas!", Toast.LENGTH_SHORT).show()
            return
        }
        if (next <= 0L) cartQty.remove(productId) else cartQty[productId] = next
        renderCart()
    }

    private fun removeFromCart(productId: Long) {
        cartQty.remove(productId)
        renderCart()
    }

    private fun renderCart() {
        val lines = cartQty.entries.mapNotNull { (productId, qty) ->
            val p = productsById[productId] ?: return@mapNotNull null
            KasirCartLine(product = p, qty = qty)
        }.sortedBy { it.product.name }
        cartAdapter.submit(lines)
        recomputeSummary(lines)
    }

    private fun recomputeSummary(lines: List<KasirCartLine>) {
        val subtotal = lines.sumOf { it.product.price * it.qty }
        val globalDiscount = (subtotal * (settings.discountPercent / 100.0)).toLong()
        
        val totalDiscount = globalDiscount
        val afterDiscount = (subtotal - totalDiscount).coerceAtLeast(0)
        val tax = (afterDiscount * (settings.taxPercent / 100.0)).toLong()
        val total = afterDiscount + tax

        currentSubtotal = subtotal
        currentDiscount = totalDiscount
        currentTax = tax
        currentTotal = total

        binding.txtSubtotal.text = UiFormat.money(subtotal)
        binding.txtDiscount.text = UiFormat.money(totalDiscount)
        binding.txtTotal.text = UiFormat.money(total)
    }

    private fun pay() {
        if (cartQty.isEmpty()) {
            Toast.makeText(requireContext(), "Keranjang Kosong!", Toast.LENGTH_SHORT).show()
            return
        }

        val paymentMethod = when (binding.togglePaymentMethod.checkedButtonId) {
            R.id.btnPayTransfer -> "TRANSFER"
            R.id.btnPayQRIS -> "QRIS"
            else -> "TUNAI"
        }

        val cartLines = cartQty.entries.mapNotNull { (productId, qty) ->
            val product = productsById[productId] ?: return@mapNotNull null
            product to qty
        }

        val fragment = PaymentFragment.newInstance(
            total = currentTotal,
            subtotal = currentSubtotal,
            discount = currentDiscount,
            tax = currentTax,
            paymentMethod = paymentMethod,
            cartLines = cartLines
        )

        (requireActivity() as DashboardActivity).navigateToPayment(fragment)
    }

    fun clearCart() {
        cartQty.clear()
        renderCart()
        loadProducts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
