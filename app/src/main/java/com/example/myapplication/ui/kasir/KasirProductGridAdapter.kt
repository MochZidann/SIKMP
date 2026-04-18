package com.example.myapplication.ui.kasir

import android.view.LayoutInflater
import android.view.ViewGroup
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
            binding.root.setOnClickListener { onClick(product) }
        }
    }
}


