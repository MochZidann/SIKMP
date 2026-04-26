package com.example.myapplication.ui.kasir

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemKasirReportRowBinding

data class KasirReportRow(
    val saleId: Long,
    val displayId: String,
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
            binding.txtNo.text = "Struk #${row.displayId}"
            binding.txtDateTime.text = row.dateTimeText
            binding.txtKasir.text = "${row.cashierText} \u2022 ${row.methodText}"
            binding.txtTotal.text = row.totalText
            
            // Status Indicator Logic
            when (row.statusText.uppercase()) {
                "SUCCESS", "LUNAS" -> {
                    binding.txtStatus.text = "LUNAS"
                    binding.txtStatus.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#10B981")) // Emerald Green
                }
                "CANCELLED", "BATAL" -> {
                    binding.txtStatus.text = "BATAL"
                    binding.txtStatus.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#EF4444")) // Red
                }
                else -> {
                    binding.txtStatus.text = row.statusText
                    binding.txtStatus.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#94A3B8")) // Slate
                }
            }

            binding.btnDetail.text = "Cek Struk"
            binding.btnDetail.setOnClickListener { onDetail(row.saleId) }
        }
    }
}
