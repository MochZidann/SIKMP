package com.kopdes.kopdesjajar.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kopdes.kopdesjajar.data.network.SyncManager

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("SyncWorker", "Memulai Auto Sync ke Cloud...")
            val syncManager = SyncManager(applicationContext)

            // Jalankan push data semua tabel
            syncManager.pushAllDataToServer()

            Log.d("SyncWorker", "Auto Sync Berhasil")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Auto Sync Gagal: ${e.message}")
            Result.retry()
        }
    }
}
