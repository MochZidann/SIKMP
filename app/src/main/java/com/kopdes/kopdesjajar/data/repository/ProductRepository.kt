package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.db.ProductEntity
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.ProductSyncPayload
import com.kopdes.kopdesjajar.data.network.VolleyHelper
import com.kopdes.kopdesjajar.data.pref.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProductRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val productDao = db.productDao()
    private val firestoreManager = FirestoreManager()
    private val prefManager = PreferenceManager(context)

    suspend fun addProduct(product: ProductEntity): Long = withContext(Dispatchers.IO) {
        val id = productDao.insert(product)
        val insertedProduct = product.copy(id = id)
        firestoreManager.syncProduct(insertedProduct)
        syncToLaravel(insertedProduct)
        return@withContext id
    }

    suspend fun updateProduct(product: ProductEntity) = withContext(Dispatchers.IO) {
        productDao.update(product)
        firestoreManager.syncProduct(product)
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
            Log.d("SyncDebug", "✅ Product ${product.name} synced to Laravel via Volley")
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Volley error syncing product: ${e.message}")
        }
    }

    suspend fun getAllProductsLocal(): List<ProductEntity> = withContext(Dispatchers.IO) {
        return@withContext productDao.getAll()
    }

    suspend fun pullFromFirebase(force: Boolean = false) = withContext(Dispatchers.IO) {
        val lastSync = prefManager.getLastProductSync()
        val cacheDuration = 12 * 60 * 60 * 1000L 

        if (force || (System.currentTimeMillis() - lastSync > cacheDuration)) {
            val remoteProducts = firestoreManager.getAllProducts()
            if (remoteProducts.isNotEmpty()) {
                remoteProducts.forEach { product ->
                    try {
                        productDao.insert(product)
                    } catch (e: Exception) {
                        productDao.update(product)
                    }
                }
                prefManager.saveLastProductSync(System.currentTimeMillis())
                Log.d("SyncDebug", "✅ Pulled ${remoteProducts.size} products from Firebase")
            }
        }
    }
}
