package com.example.myapplication.ui.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemStockProductRowBinding
import com.example.myapplication.ui.UiFormat

data class StockProductRow(
    val id: Long,
    val name: String,
    val category: String,
    val stock: Long,
    val lastUpdateEpochMs: Long?
)

class StockProductAdapter(
    private val onClick: (StockProductRow) -> Unit
) : RecyclerView.Adapter<StockProductAdapter.VH>() {
    private val items = mutableListOf<StockProductRow>()

    fun submit(newItems: List<StockProductRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemStockProductRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b, onClick)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(
        private val b: ItemStockProductRowBinding,
        private val onClick: (StockProductRow) -> Unit
    ) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: StockProductRow) {
            b.txtName.text = row.name
            b.txtCategory.text = row.category
            b.txtStock.text = "Stok: ${row.stock}"
            b.txtLastUpdate.text = "Update: " + (row.lastUpdateEpochMs?.let { UiFormat.dateOnly(it) } ?: "-")

            val ctx = b.root.context
            when {
                row.stock <= 0L -> {
                    b.chipStatus.text = "Habis"
                    b.chipStatus.chipIcon = ContextCompat.getDrawable(ctx, android.R.drawable.ic_dialog_alert)
                    b.chipStatus.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary_red_light))
                    b.chipStatus.setTextColor(ContextCompat.getColor(ctx, R.color.primary_red_dark))
                    b.chipStatus.chipIconTint = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary_red_dark))
                    b.iconContainer.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.primary_red_light))
                }
                row.stock <= 5L -> {
                    b.chipStatus.text = "Menipis"
                    b.chipStatus.chipIcon = ContextCompat.getDrawable(ctx, android.R.drawable.ic_dialog_alert)
                    b.chipStatus.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary_red_light))
                    b.chipStatus.setTextColor(ContextCompat.getColor(ctx, R.color.primary_red_dark))
                    b.chipStatus.chipIconTint = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary_red_dark))
                    b.iconContainer.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.primary_red_light))
                }
                else -> {
                    b.chipStatus.text = "Aman"
                    b.chipStatus.chipIcon = ContextCompat.getDrawable(ctx, android.R.drawable.ic_dialog_info)
                    b.chipStatus.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.accent_teal_light))
                    b.chipStatus.setTextColor(ContextCompat.getColor(ctx, R.color.accent_teal))
                    b.chipStatus.chipIconTint = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.accent_teal))
                    b.iconContainer.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.accent_teal_light))
                }
            }

            b.root.setOnClickListener { onClick(row) }
        }
    }
}
