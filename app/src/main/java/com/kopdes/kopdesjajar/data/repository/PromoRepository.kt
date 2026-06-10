package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.kopdes.kopdesjajar.data.db.*
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.PromoSyncPayload
import com.kopdes.kopdesjajar.data.network.VolleyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PromoRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val promoDao = db.promoDao()
    private val firestoreManager = FirestoreManager()

    suspend fun insertPromo(promo: PromoEntity) = withContext(Dispatchers.IO) {
        val id = promoDao.insert(promo)
        val insertedPromo = promo.copy(id = id)

        // Realtime Firebase
        firestoreManager.syncPromo(insertedPromo)

        // Sync Laravel
        syncToLaravel(insertedPromo)
    }

    suspend fun updatePromo(promo: PromoEntity) = withContext(Dispatchers.IO) {
        promoDao.update(promo)
        firestoreManager.syncPromo(promo)
        syncToLaravel(promo)
    }

    private suspend fun syncToLaravel(promo: PromoEntity) {
        try {
            val payload = listOf(PromoSyncPayload(
                id = promo.id,
                code = promo.code,
                name = promo.name,
                description = promo.description,
                discountPercent = promo.discountPercent,
                validUntilEpochMs = promo.validUntilEpochMs,
                isActive = if (promo.isActive) 1 else 0
            ))
            VolleyHelper.requestObject(context, Request.Method.POST, "sync/promos", payload)
            promoDao.updateSyncStatus(promo.id, true)
            Log.d("SyncDebug", "✅ Promo ${promo.code} synced to Laravel via Volley")
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Volley error sync promo: ${e.message}")
        }
    }
}
