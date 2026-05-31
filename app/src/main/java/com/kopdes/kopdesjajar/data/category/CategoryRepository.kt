package com.kopdes.kopdesjajar.data.category

import android.content.Context
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.db.CategoryEntity
import com.kopdes.kopdesjajar.data.network.CategorySyncPayload
import com.kopdes.kopdesjajar.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CategoryRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).categoryDao()

    suspend fun insert(category: CategoryEntity) = withContext(Dispatchers.IO) {
        dao.insert(category)
        
        // Sync ke Laravel
        try {
            val payload = listOf(CategorySyncPayload(
                name = category.name,
                createdAtEpochMs = category.createdAtEpochMs
            ))
            RetrofitClient.instance.syncCategories(payload)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun getAll(): List<CategoryEntity> = dao.getAll()
}
