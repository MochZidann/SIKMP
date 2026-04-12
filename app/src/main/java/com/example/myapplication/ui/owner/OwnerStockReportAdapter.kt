package com.example.myapplication.ui.owner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.databinding.ItemOwnerStockProductBinding

class OwnerStockReportAdapter : RecyclerView.Adapter<OwnerStockReportAdapter.VH>() {
    private val items = mutableListOf<ProductEntity>()

    fun submit(products: List<ProductEntity>) {
        items.clear()
        items.addAll(products)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOwnerStockProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(private val binding: ItemOwnerStockProductBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: ProductEntity) {
            binding.txtName.text = product.name
            binding.txtCategory.text = product.category
            binding.txtStock.text = product.stock.toString()

            val ctx = binding.root.context
            val (status, bg, fg) = when {
                product.stock <= 0L -> Triple("Habis", android.R.color.holo_red_light, android.R.color.white)
                product.stock < 10L -> Triple("Low", android.R.color.holo_orange_dark, android.R.color.white)
                else -> Triple("Normal", android.R.color.holo_green_dark, android.R.color.white)
            }
            binding.txtStatus.text = status
            binding.txtStatus.setBackgroundColor(ContextCompat.getColor(ctx, bg))
            binding.txtStatus.setTextColor(ContextCompat.getColor(ctx, fg))
        }
    }
}

