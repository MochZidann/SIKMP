package com.example.myapplication.ui.admin_gudang

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.category.CategoryRepository
import com.example.myapplication.data.db.CategoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CategoryState(
    val loading: Boolean,
    val items: List<CategoryEntity>
)

class CategoryViewModel : ViewModel() {
    private val _state = MutableStateFlow(CategoryState(loading = false, items = emptyList()))
    val state: StateFlow<CategoryState> = _state

    fun refresh(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true)
            val repo = CategoryRepository(context.applicationContext)
            val items = repo.getAll()
            _state.value = CategoryState(loading = false, items = items)
        }
    }
}

