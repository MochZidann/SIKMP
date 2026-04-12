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

data class OwnerLatestSaleRow(
    val id: Long,
    val cashierName: String,
    val total: String,
    val time: String
)

class OwnerLatestSalesAdapter : RecyclerView.Adapter<OwnerLatestSalesAdapter.VH>() {
    private val items = mutableListOf<OwnerLatestSaleRow>()

    fun submit(rows: List<OwnerLatestSaleRow>) {
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
        fun bind(row: OwnerLatestSaleRow) {
            binding.txtTime.text = row.time
            binding.txtTotal.text = row.total
            binding.txtCashier.text = row.cashierName
        }
    }
}
