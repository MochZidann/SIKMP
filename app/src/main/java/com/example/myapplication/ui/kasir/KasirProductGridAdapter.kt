package com.example.myapplication.ui.kasir

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.databinding.ItemKasirProductCardBinding
import com.example.myapplication.ui.UiFormat

class KasirProductGridAdapter(
    private val onClick: (ProductEntity) -> Unit
) : RecyclerView.Adapter<KasirProductGridAdapter.VH>() {
    private val items = mutableListOf<ProductEntity>()

    fun submit(products: List<ProductEntity>) {
        items.clear()
        items.addAll(products)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemKasirProductCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onClick)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(
        private val binding: ItemKasirProductCardBinding,
        private val onClick: (ProductEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: ProductEntity) {
            binding.txtName.text = product.name
            binding.txtPrice.text = UiFormat.money(product.price)
            binding.txtStock.text = "Stok: ${product.stock}"

            // Visual logic berdasarkan jumlah stok
            when {
                product.stock <= 0 -> {
                    binding.txtStock.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFEBEE")) // Merah Muda
                    binding.txtStock.setTextColor(Color.parseColor("#D32F2F")) // Merah Tua
                    binding.txtStock.text = "HABIS"
                    binding.root.alpha = 0.5f
                }
                product.stock < 10 -> {
                    binding.txtStock.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF3E0")) // Oranye Muda
                    binding.txtStock.setTextColor(Color.parseColor("#E65100")) // Oranye Tua
                    binding.root.alpha = 1.0f
                }
                else -> {
                    binding.txtStock.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F5F5F5")) // Abu Muda
                    binding.txtStock.setTextColor(Color.parseColor("#616161")) // Abu Tua
                    binding.root.alpha = 1.0f
                }
            }

            binding.root.setOnClickListener {
                if (product.stock > 0) {
                    onClick(product)
                }
            }
        }
    }
}
