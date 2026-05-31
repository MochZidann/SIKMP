package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.db.ProductEntity
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.ProductSyncPayload
import com.kopdes.kopdesjajar.data.network.RetrofitClient
import com.kopdes.kopdesjajar.data.pref.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProductRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val productDao = db.productDao()
    private val firestoreManager = FirestoreManager()
    private val prefManager = PreferenceManager(context)

    suspend fun addProduct(product: ProductEntity): Long = withContext(Dispatchers.IO) {
        // 1. Simpan ke SQLite Lokal
        val id = productDao.insert(product)
        val insertedProduct = product.copy(id = id)
        
        // 2. Kirim ke Firebase (Realtime)
        firestoreManager.syncProduct(insertedProduct)
        
        // 3. Sync ke Laravel (MySQL)
        syncToLaravel(insertedProduct)
        
        return@withContext id
    }

    suspend fun updateProduct(product: ProductEntity) = withContext(Dispatchers.IO) {
        // 1. Update Lokal
        productDao.update(product)
        
        // 2. Sync ke Firebase (Realtime)
        firestoreManager.syncProduct(product)

        // 3. Sync ke Laravel (MySQL)
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
            val response = RetrofitClient.instance.syncProducts(payload)
            if (response.isSuccessful) {
                productDao.updateSyncStatus(product.id, true)
                Log.d("SyncDebug", "✅ Product ${product.name} synced to Laravel")
            } else {
                Log.e("SyncDebug", "❌ Gagal sync product ${product.name}: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Error sync product: ${e.message}")
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
