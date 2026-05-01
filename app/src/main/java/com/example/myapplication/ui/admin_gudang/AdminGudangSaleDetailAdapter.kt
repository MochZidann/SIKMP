package com.example.myapplication.ui.admin_gudang

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.SaleItemDetailRow
import com.example.myapplication.databinding.ItemAdminGudangSaleDetailBinding
import com.example.myapplication.ui.UiFormat

class AdminGudangSaleDetailAdapter : RecyclerView.Adapter<AdminGudangSaleDetailAdapter.VH>() {
    private val items = mutableListOf<SaleItemDetailRow>()

    fun replaceAll(rows: List<SaleItemDetailRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    fun append(rows: List<SaleItemDetailRow>) {
        val start = items.size
        items.addAll(rows)
        notifyItemRangeInserted(start, rows.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAdminGudangSaleDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(private val binding: ItemAdminGudangSaleDetailBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: SaleItemDetailRow) {
            binding.txtDate.text = UiFormat.dateOnly(row.createdAtEpochMs)
            binding.txtProduct.text = if (row.category.isBlank() || row.category == "-") {
                row.productName
            } else {
                "${row.productName} [${row.category}]"
            }
            binding.txtQty.text = row.quantity.toString()
            binding.txtTotal.text = UiFormat.money(row.lineTotal)
        }
    }
}
