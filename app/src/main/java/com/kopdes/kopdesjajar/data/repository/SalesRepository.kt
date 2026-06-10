package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.kopdes.kopdesjajar.data.db.*
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.VolleyHelper
import com.kopdes.kopdesjajar.data.network.SaleSyncPayload
import com.kopdes.kopdesjajar.data.network.SaleItemSyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SalesRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val salesDao = db.salesDao()
    private val firestoreManager = FirestoreManager()

    suspend fun insertSale(sale: SaleEntity, items: List<SaleItemEntity>) = withContext(Dispatchers.IO) {
        val id = salesDao.insertSaleWithItems(sale, items)
        firestoreManager.syncSale(sale, items)
        
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
            VolleyHelper.requestObject(context, Request.Method.POST, "sync/sales", payload)
            salesDao.updateSyncStatus(id, true)
            Log.d("SyncDebug", "✅ Sale ${sale.transactionId} synced via Volley")
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Volley error syncing sale: ${e.message}")
        }
        
        return@withContext id
    }
}
