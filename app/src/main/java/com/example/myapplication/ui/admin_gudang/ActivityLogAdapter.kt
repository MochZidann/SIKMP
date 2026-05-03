package com.example.myapplication.ui.admin_gudang

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.StockActivityRow
import com.example.myapplication.databinding.ItemActivityLogRowBinding
import java.text.SimpleDateFormat
import java.util.*

class ActivityLogAdapter : RecyclerView.Adapter<ActivityLogAdapter.VH>() {
    private val items = mutableListOf<StockActivityRow>()
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    inner class VH(val b: ItemActivityLogRowBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemActivityLogRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val now = System.currentTimeMillis()
        val isToday = (now - item.createdAtEpochMs) < 86400000L
        holder.b.txtTime.text = if (isToday) timeFmt.format(Date(item.createdAtEpochMs)) else dateFmt.format(Date(item.createdAtEpochMs))
        holder.b.txtProductName.text = item.productName
        holder.b.txtType.text = item.type
        val delta = item.quantityDelta
        holder.b.txtQty.text = if (delta > 0) "+$delta" else delta.toString()
        holder.b.txtQty.setTextColor(if (delta > 0) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
    }

    override fun getItemCount() = items.size

    fun submit(list: List<StockActivityRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}
