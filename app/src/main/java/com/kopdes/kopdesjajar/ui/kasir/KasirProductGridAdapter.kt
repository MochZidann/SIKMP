package com.kopdes.kopdesjajar.ui.kasir

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.kopdes.kopdesjajar.data.db.ProductEntity
import com.kopdes.kopdesjajar.data.network.VolleyHelper
import com.kopdes.kopdesjajar.databinding.ItemKasirProductCardBinding
import com.kopdes.kopdesjajar.ui.UiFormat
import java.io.File

class KasirProductGridAdapter(
    private val onClick: (ProductEntity) -> Unit
) : RecyclerView.Adapter<KasirProductGridAdapter.VH>() {
    private val items = mutableListOf<ProductEntity>()

    fun submit(products: List<ProductEntity>) {
        items.clear()
        items.addAll(products)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemKasirProductCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onClick)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(
        private val binding: ItemKasirProductCardBinding,
        private val onClick: (ProductEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: ProductEntity) {
            binding.txtName.text = product.name
            binding.txtPrice.text = UiFormat.money(product.price)
            binding.txtStock.text = "Stok: ${product.stock}"

            // --- LOGIKA RENDER GAMBAR (SINKRON WEB & LOKAL) ---
            val imagePath = product.imagePath
            if (!imagePath.isNullOrBlank() && imagePath != "null") {
                val imgSource = when {
                    imagePath.startsWith("http") -> imagePath
                    imagePath.startsWith("/") -> File(imagePath)
                    else -> {
                        // Gabungkan BASE_URL dengan path storage Laravel
                        val cleanBase = VolleyHelper.BASE_URL.replace("/api/", "/").removeSuffix("/")
                        "$cleanBase/storage/$imagePath"
                    }
                }

                binding.imgProduct.load(imgSource) {
                    crossfade(true)
                    // Matikan cache agar gambar selalu fresh dari server (sesuai instruksi)
                    diskCachePolicy(CachePolicy.DISABLED)
                    memoryCachePolicy(CachePolicy.DISABLED)
                    placeholder(android.R.drawable.ic_menu_gallery)
                    error(android.R.drawable.ic_menu_gallery)
                }
                binding.imgProduct.alpha = 1.0f
                binding.imgProduct.scaleType = ImageView.ScaleType.CENTER_CROP
                binding.imgProduct.imageTintList = null
            } else {
                binding.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery)
                binding.imgProduct.alpha = 0.3f
                binding.imgProduct.scaleType = ImageView.ScaleType.CENTER_INSIDE
                binding.imgProduct.imageTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8"))
            }

            // Visual logic berdasarkan jumlah stok
            when {
                product.stock <= 0 -> {
                    binding.txtStock.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFEBEE"))
                    binding.txtStock.setTextColor(Color.parseColor("#D32F2F"))
                    binding.txtStock.text = "HABIS"
                    binding.root.alpha = 0.5f
                }
                product.stock < 10 -> {
                    binding.txtStock.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF3E0"))
                    binding.txtStock.setTextColor(Color.parseColor("#E65100"))
                    binding.root.alpha = 1.0f
                }
                else -> {
                    binding.txtStock.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F5F5F5"))
                    binding.txtStock.setTextColor(Color.parseColor("#616161"))
                    binding.root.alpha = 1.0f
                }
            }

            binding.root.setOnClickListener {
                if (product.stock > 0) {
                    onClick(product)
                }
            }
        }
    }
}
