package com.example.myapplication.ui.admin_gudang

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.databinding.ItemRestockAlertRowBinding

class RestockAlertAdapter : RecyclerView.Adapter<RestockAlertAdapter.VH>() {
    private val items = mutableListOf<ProductEntity>()

    inner class VH(val b: ItemRestockAlertRowBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemRestockAlertRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.txtProductName.text = item.name
        holder.b.txtCategory.text = item.category
        holder.b.txtCurrentStock.text = item.stock.toString()
        holder.b.txtMinStock.text = item.minimumStock.toString()
        val deficit = item.minimumStock - item.stock
        holder.b.txtCurrentStock.setTextColor(
            if (deficit > 10) android.graphics.Color.parseColor("#DC2626")
            else android.graphics.Color.parseColor("#F59E0B")
        )
    }

    override fun getItemCount() = items.size

    fun submit(list: List<ProductEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}
