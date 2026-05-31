package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.kopdes.kopdesjajar.data.db.*
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.RetrofitClient
import com.kopdes.kopdesjajar.data.network.SettingsSyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val settingsDao = db.settingsDao()
    private val firestoreManager = FirestoreManager()

    suspend fun getSettings(): SettingsEntity? = withContext(Dispatchers.IO) {
        return@withContext settingsDao.get()
    }

    suspend fun saveSettings(settings: SettingsEntity) = withContext(Dispatchers.IO) {
        // 1. Simpan Lokal
        settingsDao.upsert(settings)

        // 2. Realtime Firebase
        firestoreManager.syncSettings(settings)

        // 3. Sync Laravel
        try {
            val payload = SettingsSyncPayload(
                koperasiName = settings.koperasiName,
                koperasiAddress = settings.koperasiAddress,
                koperasiPhone = settings.koperasiPhone,
                taxPercent = settings.taxPercent,
                discountPercent = settings.discountPercent,
                shuParameter = settings.shuParameter,
                latitude = settings.latitude,
                longitude = settings.longitude,
                updatedAtEpochMs = settings.updatedAtEpochMs
            )
            val response = RetrofitClient.instance.syncSettings(payload)
            if (response.isSuccessful) {
                settingsDao.updateSyncStatus(true)
                Log.d("SyncDebug", "✅ Settings synced to Laravel")
            } else {
                Log.e("SyncDebug", "❌ Gagal sync settings: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Error sync settings: ${e.message}")
        }
    }
}
