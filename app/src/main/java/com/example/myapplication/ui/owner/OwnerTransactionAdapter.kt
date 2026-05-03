package com.example.myapplication.ui.owner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.TransactionRow
import com.example.myapplication.databinding.ItemOwnerTransactionRowBinding
import com.example.myapplication.ui.UiFormat
import java.text.SimpleDateFormat
import java.util.*

class OwnerTransactionAdapter : RecyclerView.Adapter<OwnerTransactionAdapter.VH>() {
    private val items = mutableListOf<TransactionRow>()
    private val dtFmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())

    inner class VH(val b: ItemOwnerTransactionRowBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemOwnerTransactionRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.txtTransId.text = item.transactionId.takeLast(10)
        holder.b.txtTime.text = dtFmt.format(Date(item.createdAtEpochMs))
        holder.b.txtItemCount.text = item.itemCount.toString()
        holder.b.txtPayMethod.text = item.paymentMethod
        holder.b.txtTotal.text = UiFormat.money(item.total)
        holder.b.txtCashier.text = "Kasir: ${item.cashierName}"
    }

    override fun getItemCount() = items.size

    fun replaceAll(list: List<TransactionRow>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    fun append(list: List<TransactionRow>) {
        val start = items.size; items.addAll(list); notifyItemRangeInserted(start, list.size)
    }

    fun getItems(): List<TransactionRow> = items
}
