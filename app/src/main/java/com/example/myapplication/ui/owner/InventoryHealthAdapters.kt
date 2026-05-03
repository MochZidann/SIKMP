package com.example.myapplication.ui.owner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.CategoryAssetRow
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.databinding.ItemCategoryAssetRowBinding
import com.example.myapplication.databinding.ItemDeadStockRowBinding
import com.example.myapplication.ui.UiFormat

class CategoryAssetAdapter : RecyclerView.Adapter<CategoryAssetAdapter.VH>() {
    private val items = mutableListOf<CategoryAssetRow>()

    inner class VH(val b: ItemCategoryAssetRowBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemCategoryAssetRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.txtCategory.text = item.category
        holder.b.txtSkuCount.text = item.productCount.toString()
        holder.b.txtValue.text = UiFormat.money(item.totalValue)
    }

    override fun getItemCount() = items.size

    fun submit(list: List<CategoryAssetRow>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }
}

class DeadStockAdapter : RecyclerView.Adapter<DeadStockAdapter.VH>() {
    private val items = mutableListOf<ProductEntity>()

    inner class VH(val b: ItemDeadStockRowBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemDeadStockRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.txtProductName.text = item.name
        holder.b.txtCategory.text = item.category
        holder.b.txtStock.text = item.stock.toString()
        holder.b.txtValue.text = UiFormat.money(item.stock * item.price)
    }

    override fun getItemCount() = items.size

    fun submit(list: List<ProductEntity>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }
}
