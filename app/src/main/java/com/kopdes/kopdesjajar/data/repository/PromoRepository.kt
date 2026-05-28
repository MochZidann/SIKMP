package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.kopdes.kopdesjajar.data.db.*
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.PromoSyncPayload
import com.kopdes.kopdesjajar.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PromoRepository(context: Context) {
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
            val response = RetrofitClient.instance.syncPromos(payload)
            if (response.isSuccessful) {
                promoDao.updateSyncStatus(promo.id, true)
                Log.d("SyncDebug", "✅ Promo ${promo.code} synced to Laravel")
            } else {
                Log.e("SyncDebug", "❌ Gagal sync promo ${promo.code}: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Error sync promo: ${e.message}")
        }
    }
}
