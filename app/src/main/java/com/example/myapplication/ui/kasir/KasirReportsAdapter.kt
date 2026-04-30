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
            
            // Status Indicator Logic - Simplified to just text colors as requested
            when (row.statusText.uppercase()) {
                "SUCCESS", "LUNAS" -> {
                    binding.txtStatus.text = "LUNAS"
                    binding.txtStatus.setTextColor(Color.parseColor("#10B981")) // Emerald Green
                    binding.txtStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7")) // Light Green BG
                }
                "CANCELLED", "BATAL" -> {
                    binding.txtStatus.text = "BATAL"
                    binding.txtStatus.setTextColor(Color.parseColor("#EF4444")) // Red
                    binding.txtStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2")) // Light Red BG
                }
                "PENDING", "PROSES" -> {
                    binding.txtStatus.text = "PROSES"
                    binding.txtStatus.setTextColor(Color.parseColor("#F59E0B")) // Amber
                    binding.txtStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7")) // Light Amber BG
                }
                else -> {
                    binding.txtStatus.text = row.statusText
                    binding.txtStatus.setTextColor(Color.parseColor("#64748B")) // Slate
                    binding.txtStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9")) // Light Slate BG
                }
            }

            binding.btnDetail.setOnClickListener { onDetail(row.saleId) }
        }
    }
}
