package com.example.myapplication.ui.admin_gudang

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.StockMutationDetailRow
import com.example.myapplication.databinding.ItemStockMutationRowBinding
import com.example.myapplication.ui.UiFormat
import java.util.Date

class AdminGudangStockMutationAdapter : RecyclerView.Adapter<AdminGudangStockMutationAdapter.VH>() {
    private val items = ArrayList<StockMutationDetailRow>()

    inner class VH(val b: ItemStockMutationRowBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemStockMutationRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.txtDate.text = UiFormat.dateTime(item.createdAtEpochMs)
        holder.b.txtProductName.text = item.productName
        holder.b.txtType.text = item.type
        holder.b.txtDelta.text = if (item.quantityDelta > 0) "+${item.quantityDelta}" else item.quantityDelta.toString()
        
        val context = holder.itemView.context
        if (item.quantityDelta > 0) {
            holder.b.txtDelta.setTextColor(context.getColor(com.example.myapplication.R.color.primary_green))
        } else {
            holder.b.txtDelta.setTextColor(context.getColor(com.example.myapplication.R.color.primary_red))
        }
    }

    override fun getItemCount() = items.size

    fun replaceAll(newItems: List<StockMutationDetailRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun append(newItems: List<StockMutationDetailRow>) {
        val start = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(start, newItems.size)
    }
    
    fun getItems() = items
}
