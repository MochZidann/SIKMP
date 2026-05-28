package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.kopdes.kopdesjajar.data.db.*
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.RetrofitClient
import com.kopdes.kopdesjajar.data.network.SaleSyncPayload
import com.kopdes.kopdesjajar.data.network.SaleItemSyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SalesRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val salesDao = db.salesDao()
    private val firestoreManager = FirestoreManager()

    suspend fun insertSale(sale: SaleEntity, items: List<SaleItemEntity>) = withContext(Dispatchers.IO) {
        // 1. Simpan Lokal
        val id = salesDao.insertSaleWithItems(sale, items)
        
        // 2. Realtime ke Firebase
        firestoreManager.syncSale(sale, items)
        
        // 3. Sync ke Laravel
        try {
            val payload = listOf(SaleSyncPayload(
                id = id,
                transactionId = sale.transactionId,
                cashierId = sale.cashierId,
                subtotal = sale.subtotal,
                discount = sale.discount,
                tax = sale.tax,
                total = sale.total,
                paymentMethod = sale.paymentMethod,
                status = sale.status,
                createdAtEpochMs = sale.createdAtEpochMs,
                items = items.map { 
                    SaleItemSyncPayload(id = it.id, productId = it.productId, productName = it.productName, unitPrice = it.unitPrice, quantity = it.quantity, lineTotal = it.lineTotal)
                }
            ))
            val response = RetrofitClient.instance.syncSales(payload)
            if (response.isSuccessful) {
                salesDao.updateSyncStatus(id, true)
                Log.d("SyncDebug", "✅ Sale ${sale.transactionId} synced to Laravel")
            } else {
                Log.e("SyncDebug", "❌ Gagal sync sale: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Error sync sale: ${e.message}")
        }
        
        return@withContext id
    }
}
