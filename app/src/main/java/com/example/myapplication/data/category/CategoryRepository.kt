package com.example.myapplication.data.category

import android.content.Context
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.CategoryEntity

class CategoryRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).categoryDao()

    fun getAll(): List<CategoryEntity> = dao.getAll()
}

