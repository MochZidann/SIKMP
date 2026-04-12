package com.example.myapplication.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemKasirSaleRowBinding

data class KasirSaleRow(
    val saleId: Long,
    val timeText: String,
    val itemCountText: String,
    val totalText: String,
    val statusText: String
)

class KasirSalesAdapter(
    private val onDetail: (saleId: Long) -> Unit,
    private val onRepeat: (saleId: Long) -> Unit
) : RecyclerView.Adapter<KasirSalesAdapter.VH>() {
    private val items = mutableListOf<KasirSaleRow>()

    fun submit(rows: List<KasirSaleRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemKasirSaleRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onDetail, onRepeat)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(
        private val binding: ItemKasirSaleRowBinding,
        private val onDetail: (saleId: Long) -> Unit,
        private val onRepeat: (saleId: Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: KasirSaleRow) {
            binding.txtNoStruk.text = "#${row.saleId}"
            binding.txtJam.text = row.timeText
            binding.txtItemCount.text = row.itemCountText
            binding.txtTotal.text = row.totalText
            binding.txtStatus.text = row.statusText
            binding.btnDetail.setOnClickListener { onDetail(row.saleId) }
            binding.btnRepeat.setOnClickListener { onRepeat(row.saleId) }
        }
    }
}
