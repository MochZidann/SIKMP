package com.example.myapplication.ui.admin_gudang

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.StockMutationDetailRow
import com.example.myapplication.databinding.ItemMutationDetailRowBinding
import java.text.SimpleDateFormat
import java.util.*

class MutationDetailAdapter : RecyclerView.Adapter<MutationDetailAdapter.VH>() {
    private val items = mutableListOf<StockMutationDetailRow>()
    private val dateFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    inner class VH(val b: ItemMutationDetailRowBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemMutationDetailRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.txtDate.text = dateFmt.format(Date(item.createdAtEpochMs))
        holder.b.txtProductName.text = item.productName
        if (item.quantityDelta > 0) {
            holder.b.txtQtyIn.text = item.quantityDelta.toString()
            holder.b.txtQtyOut.text = "-"
            holder.b.txtQtyIn.setTextColor(Color.parseColor("#10B981"))
            holder.b.txtQtyOut.setTextColor(Color.parseColor("#CBD5E1"))
        } else {
            holder.b.txtQtyIn.text = "-"
            holder.b.txtQtyOut.text = (-item.quantityDelta).toString()
            holder.b.txtQtyIn.setTextColor(Color.parseColor("#CBD5E1"))
            holder.b.txtQtyOut.setTextColor(Color.parseColor("#EF4444"))
        }
        // Current stock of product shown as "Sisa"
        holder.b.txtSisa.text = item.currentStock?.toString() ?: "-"
    }

    override fun getItemCount() = items.size

    fun replaceAll(list: List<StockMutationDetailRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun append(list: List<StockMutationDetailRow>) {
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    fun getItems(): List<StockMutationDetailRow> = items
}
