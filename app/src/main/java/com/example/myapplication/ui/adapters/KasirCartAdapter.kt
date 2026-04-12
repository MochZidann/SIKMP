package com.example.myapplication.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.ProductEntity
import com.example.myapplication.databinding.ItemKasirCartRowBinding
import com.example.myapplication.ui.UiFormat

data class KasirCartLine(
    val product: ProductEntity,
    val qty: Long
)

class KasirCartAdapter(
    private val onPlus: (productId: Long) -> Unit,
    private val onMinus: (productId: Long) -> Unit,
    private val onRemove: (productId: Long) -> Unit
) : RecyclerView.Adapter<KasirCartAdapter.VH>() {
    private val items = mutableListOf<KasirCartLine>()

    fun submit(lines: List<KasirCartLine>) {
        items.clear()
        items.addAll(lines)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemKasirCartRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onPlus, onMinus, onRemove)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(
        private val binding: ItemKasirCartRowBinding,
        private val onPlus: (productId: Long) -> Unit,
        private val onMinus: (productId: Long) -> Unit,
        private val onRemove: (productId: Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(line: KasirCartLine) {
            binding.txtName.text = line.product.name
            binding.txtPrice.text = "${UiFormat.money(line.product.price)} x ${line.qty} = ${UiFormat.money(line.product.price * line.qty)}"
            binding.txtQty.text = line.qty.toString()
            binding.btnPlus.setOnClickListener { onPlus(line.product.id) }
            binding.btnMinus.setOnClickListener { onMinus(line.product.id) }
            binding.btnRemove.setOnClickListener { onRemove(line.product.id) }
        }
    }
}
