package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.kopdes.kopdesjajar.data.db.*
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.RetrofitClient
import com.kopdes.kopdesjajar.data.network.StockMovementSyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StockRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val movementDao = db.stockMovementDao()
    private val productDao = db.productDao()
    private val firestoreManager = FirestoreManager()

    suspend fun addMovement(movement: StockMovementEntity) = withContext(Dispatchers.IO) {
        // 1. Simpan Lokal & Update Stok Produk
        val id = movementDao.insert(movement)
        val product = productDao.findById(movement.productId)
        if (product != null) {
            val newStock = product.stock + movement.quantityDelta
            productDao.updateStockAbsolute(product.id, newStock)
            
            // Sync status produk juga ke cloud karena stok berubah
            val updatedProduct = product.copy(stock = newStock)
            firestoreManager.syncProduct(updatedProduct)
        }

        val insertedMovement = movement.copy(id = id)

        // 2. Realtime Firebase
        firestoreManager.syncStockMovement(insertedMovement)

        // 3. Sync Laravel
        try {
            val payload = listOf(StockMovementSyncPayload(
                id = id,
                productId = movement.productId,
                userId = movement.userId,
                type = movement.type,
                quantityDelta = movement.quantityDelta,
                note = movement.note,
                createdAtEpochMs = movement.createdAtEpochMs
            ))
            val response = RetrofitClient.instance.syncStockMovements(payload)
            if (response.isSuccessful) {
                movementDao.updateSyncStatus(id, true)
                Log.d("SyncDebug", "✅ Stock movement for product ${movement.productId} synced to Laravel")
            } else {
                Log.e("SyncDebug", "❌ Gagal sync stock movement: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Error sync stock movement: ${e.message}")
        }
    }
}
