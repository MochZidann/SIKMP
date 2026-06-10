package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.kopdes.kopdesjajar.data.db.*
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.VolleyHelper
import com.kopdes.kopdesjajar.data.network.StockMovementSyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StockRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val movementDao = db.stockMovementDao()
    private val productDao = db.productDao()
    private val firestoreManager = FirestoreManager()

    suspend fun addMovement(movement: StockMovementEntity) = withContext(Dispatchers.IO) {
        val id = movementDao.insert(movement)
        val product = productDao.findById(movement.productId)
        if (product != null) {
            val newStock = product.stock + movement.quantityDelta
            productDao.updateStockAbsolute(product.id, newStock)
            val updatedProduct = product.copy(stock = newStock)
            firestoreManager.syncProduct(updatedProduct)
        }

        val insertedMovement = movement.copy(id = id)
        firestoreManager.syncStockMovement(insertedMovement)

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
            VolleyHelper.requestObject(context, Request.Method.POST, "sync/movements", payload)
            movementDao.updateSyncStatus(id, true)
            Log.d("SyncDebug", "✅ Stock movement synced via Volley")
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Volley error syncing stock movement: ${e.message}")
        }
    }
}
