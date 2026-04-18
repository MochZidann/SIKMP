package com.example.myapplication.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemTwoLineBinding

data class TwoLineRow(
    val id: Long,
    val title: String,
    val subtitle: String
)

class TwoLineAdapter(
    private val onClick: (TwoLineRow) -> Unit
) : RecyclerView.Adapter<TwoLineAdapter.VH>() {
    private val items = mutableListOf<TwoLineRow>()

    fun submit(newItems: List<TwoLineRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTwoLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], onClick)
    }

    class VH(private val binding: ItemTwoLineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: TwoLineRow, onClick: (TwoLineRow) -> Unit) {
            binding.title.text = row.title
            binding.subtitle.text = row.subtitle
            binding.root.setOnClickListener { onClick(row) }
        }
    }
}

