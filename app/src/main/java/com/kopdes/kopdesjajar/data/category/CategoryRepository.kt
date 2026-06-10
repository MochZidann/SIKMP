package com.kopdes.kopdesjajar.data.category

import android.content.Context
import com.android.volley.Request
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.db.CategoryEntity
import com.kopdes.kopdesjajar.data.network.CategorySyncPayload
import com.kopdes.kopdesjajar.data.network.VolleyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CategoryRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).categoryDao()

    suspend fun insert(category: CategoryEntity) = withContext(Dispatchers.IO) {
        dao.insert(category)
        
        try {
            val payload = listOf(CategorySyncPayload(
                name = category.name,
                createdAtEpochMs = category.createdAtEpochMs
            ))
            VolleyHelper.requestObject(context, Request.Method.POST, "sync/categories", payload)
            dao.updateSyncStatus(category.id, true)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun getAll(): List<CategoryEntity> = dao.getAll()
}
