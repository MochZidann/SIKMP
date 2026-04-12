package com.example.myapplication.ui.owner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.LatestSaleWithCashier
import com.example.myapplication.databinding.ItemOwnerLatestSaleBinding
import com.example.myapplication.ui.UiFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OwnerLatestSalesAdapter : RecyclerView.Adapter<OwnerLatestSalesAdapter.VH>() {
    private val items = mutableListOf<LatestSaleWithCashier>()

    fun submit(rows: List<LatestSaleWithCashier>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOwnerLatestSaleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(private val binding: ItemOwnerLatestSaleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: LatestSaleWithCashier) {
            binding.txtTime.text = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date(row.createdAtEpochMs))
            binding.txtTotal.text = UiFormat.money(row.total)
            binding.txtCashier.text = row.cashierName
        }
    }
}
