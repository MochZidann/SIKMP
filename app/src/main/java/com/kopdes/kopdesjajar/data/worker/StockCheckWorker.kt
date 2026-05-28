package com.kopdes.kopdesjajar.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StockCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.get(applicationContext)
            val lowStockCount = db.productDao().countLowStock(null)
            
            if (lowStockCount > 0) {
                NotificationHelper.showLowStockNotification(applicationContext, lowStockCount.toInt())
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
