package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.db.ProductEntity
import com.kopdes.kopdesjajar.data.network.ProductSyncPayload
import com.kopdes.kopdesjajar.data.network.VolleyHelper
import com.kopdes.kopdesjajar.data.pref.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProductRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val productDao = db.productDao()
    private val prefManager = PreferenceManager(context)

    suspend fun addProduct(product: ProductEntity): Long = withContext(Dispatchers.IO) {
        val id = productDao.insert(product)
        val insertedProduct = product.copy(id = id)
        // Sinkronisasi ke Firebase dihapus karena beralih ke Laravel
        syncToLaravel(insertedProduct)
        return@withContext id
    }

    suspend fun updateProduct(product: ProductEntity) = withContext(Dispatchers.IO) {
        productDao.update(product)
        // Sinkronisasi ke Firebase dihapus karena beralih ke Laravel
        syncToLaravel(product)
    }

    private suspend fun syncToLaravel(product: ProductEntity) {
        try {
            val payload = listOf(ProductSyncPayload(
                id = product.id,
                barcode = product.barcode,
                name = product.name,
                category = product.category,
                price = product.price,
                stock = product.stock,
                minimumStock = product.minimumStock,
                expiredDateEpochMs = product.expiredDateEpochMs,
                imagePath = product.imagePath,
                purchasePrice = product.purchasePrice,
                createdAtEpochMs = product.createdAtEpochMs
            ))
            VolleyHelper.requestObject(context, Request.Method.POST, "sync/products", payload)
            productDao.updateSyncStatus(product.id, true)
            Log.d("SyncDebug", "✅ Product ${product.name} synced to Laravel")
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 error syncing product to Laravel: ${e.message}")
        }
    }

    suspend fun getAllProductsLocal(): List<ProductEntity> = withContext(Dispatchers.IO) {
        return@withContext productDao.getAll()
    }

    suspend fun pullFromServer(force: Boolean = false) = withContext(Dispatchers.IO) {
        val lastSync = prefManager.getLastProductSync()
        val cacheDuration = 12 * 60 * 60 * 1000L 

        if (force || (System.currentTimeMillis() - lastSync > cacheDuration)) {
            // Logika pull dari Laravel bisa ditambahkan di sini via VolleyHelper
            Log.d("SyncDebug", "Checking updates from Laravel server...")
            prefManager.saveLastProductSync(System.currentTimeMillis())
        }
    }
}
