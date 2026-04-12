package com.example.myapplication.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemKasirReportRowBinding

data class KasirReportRow(
    val saleId: Long,
    val dateTimeText: String,
    val cashierText: String,
    val itemCountText: String,
    val totalText: String,
    val methodText: String,
    val statusText: String
)

class KasirReportsAdapter(
    private val onDetail: (saleId: Long) -> Unit
) : RecyclerView.Adapter<KasirReportsAdapter.VH>() {
    private val items = mutableListOf<KasirReportRow>()

    fun submit(rows: List<KasirReportRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemKasirReportRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onDetail)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(
        private val binding: ItemKasirReportRowBinding,
        private val onDetail: (saleId: Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: KasirReportRow) {
            binding.txtNo.text = "#${row.saleId}"
            binding.txtDateTime.text = row.dateTimeText
            binding.txtKasir.text = row.cashierText
            binding.txtItemCount.text = row.itemCountText
            binding.txtTotal.text = row.totalText
            binding.txtMethod.text = row.methodText
            binding.txtStatus.text = row.statusText
            binding.btnDetail.setOnClickListener { onDetail(row.saleId) }
        }
    }
}
