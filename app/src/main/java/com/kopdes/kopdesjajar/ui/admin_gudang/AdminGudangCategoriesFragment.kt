package com.kopdes.kopdesjajar.ui.admin_gudang

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.kopdes.kopdesjajar.data.audit.AuditLogger
import com.kopdes.kopdesjajar.data.auth.SessionManager
import com.kopdes.kopdesjajar.data.db.CategoryEntity
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.network.SyncManager
import com.kopdes.kopdesjajar.data.network.VolleyHelper
import com.kopdes.kopdesjajar.databinding.DialogCategoryFormBinding
import com.kopdes.kopdesjajar.databinding.FragmentAdminGudangCategoriesBinding
import com.kopdes.kopdesjajar.databinding.ItemCategoryRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminGudangCategoriesFragment : Fragment() {
    private var _binding: FragmentAdminGudangCategoriesBinding? = null
    private val binding get() = _binding!!

    private val adapter = CategoryAdapter(
        onClick = { category -> showCategoryOptions(category) }
    )

    private lateinit var viewModel: CategoryViewModel

    private var query: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[CategoryViewModel::class.java]
        binding.recycler.adapter = adapter

        binding.etSearch.addTextChangedListener {
            query = it?.toString().orEmpty()
            render()
        }

        binding.fabAdd.setOnClickListener {
            showCategoryForm(existing = null)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { render() }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refresh() {
        viewModel.refresh(requireContext())
    }

    private fun render() {
        val state = viewModel.state.value
        binding.progress.visibility = if (state.loading) View.VISIBLE else View.GONE

        val q = query.trim().lowercase()
        val filtered = state.items
            .filter { q.isBlank() || it.name.lowercase().contains(q) }
            .sortedBy { it.name }

        adapter.submit(filtered)
        val empty = filtered.isEmpty() && !state.loading
        binding.recycler.visibility = if (empty) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.txtEmpty.text = if (q.isBlank()) "Belum ada kategori" else "Tidak ditemukan"
    }

    private fun showCategoryOptions(category: CategoryEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(category.name)
            .setItems(arrayOf("Edit", "Hapus")) { _, which ->
                when (which) {
                    0 -> showCategoryForm(existing = category)
                    1 -> confirmDelete(category)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showCategoryForm(existing: CategoryEntity?) {
        val b = DialogCategoryFormBinding.inflate(layoutInflater)
        b.txtTitle.text = if (existing == null) "Tambah Kategori" else "Edit Kategori"
        b.etName.setText(existing?.name.orEmpty())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(b.root)
            .setCancelable(true)
            .show()

        b.btnCancel.setOnClickListener { dialog.dismiss() }
        b.btnSave.setOnClickListener {
            val name = b.etName.text?.toString()?.trim().orEmpty()
            b.nameLayout.error = null
            if (name.isBlank()) {
                b.nameLayout.error = "Wajib diisi"
                return@setOnClickListener
            }
            dialog.dismiss()
            if (existing == null) {
                createCategory(name)
            } else {
                updateCategory(existing, name)
            }
        }
    }

    private fun createCategory(name: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val created = CategoryEntity(name = name)
            val id = try {
                db.categoryDao().insert(created)
            } catch (_: Exception) {
                -1L
            }
            if (id > 0L) {
                try {
                    SyncManager(requireContext()).pushAllDataToServer()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                if (id <= 0L) {
                    Snackbar.make(binding.root, "Kategori sudah ada", Snackbar.LENGTH_SHORT).show()
                } else {
                    AuditLogger.log(
                        context = requireContext(),
                        userId = SessionManager(requireContext()).userId(),
                        action = "CREATE",
                        entity = "category",
                        entityId = id,
                        detail = "Tambah kategori: $name"
                    )
                    Snackbar.make(binding.root, "Kategori ditambahkan", Snackbar.LENGTH_SHORT).show()
                    refresh()
                }
            }
        }
    }

    private fun updateCategory(existing: CategoryEntity, newName: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val oldName = existing.name
            try {
                db.categoryDao().update(existing.copy(name = newName))
                try {
                    SyncManager(requireContext()).pushAllDataToServer()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "Gagal menyimpan kategori", Snackbar.LENGTH_SHORT).show()
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                AuditLogger.log(
                    context = requireContext(),
                    userId = SessionManager(requireContext()).userId(),
                    action = "UPDATE",
                    entity = "category",
                    entityId = existing.id,
                    detail = "Edit kategori: dari $oldName menjadi $newName"
                )
                Snackbar.make(binding.root, "Kategori diperbarui", Snackbar.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    private fun confirmDelete(category: CategoryEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus kategori?")
            .setMessage("Kategori: ${category.name}")
            .setPositiveButton("Hapus") { _, _ -> deleteCategory(category) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteCategory(category: CategoryEntity) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val inUse = db.productDao().totalProducts(category = category.name) > 0L
            if (inUse) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "Tidak bisa hapus: kategori masih dipakai produk", Snackbar.LENGTH_SHORT).show()
                }
                return@launch
            }
            db.categoryDao().delete(category)
            try {
                com.kopdes.kopdesjajar.data.firebase.FirestoreManager().deleteCategory(category.name)
                VolleyHelper.requestDelete(requireContext(), "sync/categories/${category.name}")
                SyncManager(requireContext()).pushAllDataToServer()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.Main) {
                AuditLogger.log(
                    context = requireContext(),
                    userId = SessionManager(requireContext()).userId(),
                    action = "DELETE",
                    entity = "category",
                    entityId = category.id,
                    detail = "Hapus kategori: ${category.name}"
                )
                Snackbar.make(binding.root, "Kategori dihapus", Snackbar.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    private class CategoryAdapter(
        private val onClick: (CategoryEntity) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {
        private val items = mutableListOf<CategoryEntity>()

        fun submit(newItems: List<CategoryEntity>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemCategoryRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b, onClick)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        class VH(
            private val b: ItemCategoryRowBinding,
            private val onClick: (CategoryEntity) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: CategoryEntity) {
                b.txtName.text = item.name
                b.root.setOnClickListener { onClick(item) }
            }
        }
    }
}
