package com.example.myapplication.ui.admin

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.PromoEntity
import com.example.myapplication.databinding.DialogPromoFormBinding
import com.example.myapplication.databinding.FragmentPromoConfigBinding
import com.example.myapplication.databinding.ItemPromoBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class PromoConfigFragment : Fragment() {
    private var _binding: FragmentPromoConfigBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: Long = System.currentTimeMillis()
    private var selectedHour: Int = 23
    private var selectedMinute: Int = 59

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPromoConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.recyclerPromos.layoutManager = GridLayoutManager(requireContext(), 2)
        refreshData()

        binding.btnAddPromo.setOnClickListener {
            showPromoForm()
        }
    }

    private fun refreshData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val promos = db.promoDao().getAll()
            withContext(Dispatchers.Main) {
                if (promos.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerPromos.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerPromos.visibility = View.VISIBLE
                    binding.recyclerPromos.adapter = PromoAdapter(promos)
                }
            }
        }
    }

    private fun showPromoForm() {
        val dialogBinding = DialogPromoFormBinding.inflate(layoutInflater)
        val calendar = Calendar.getInstance()
        selectedDate = calendar.timeInMillis
        selectedHour = 23
        selectedMinute = 59
        
        updateDateTimeText(dialogBinding)

        dialogBinding.btnPromoDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Pilih Tanggal")
                .setSelection(selectedDate)
                .build()
            picker.addOnPositiveButtonClickListener {
                selectedDate = it
                updateDateTimeText(dialogBinding)
            }
            picker.show(childFragmentManager, "DATE_PICKER")
        }

        dialogBinding.btnPromoTime.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setTitleText("Pilih Waktu")
                .build()
            picker.addOnPositiveButtonClickListener {
                selectedHour = picker.hour
                selectedMinute = picker.minute
                updateDateTimeText(dialogBinding)
            }
            picker.show(childFragmentManager, "TIME_PICKER")
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tambah Promo Baru")
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan", null) // Set null to override later
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogBinding.etPromoName.text.toString().trim()
                val description = dialogBinding.etPromoDescription.text.toString().trim()
                val discountStr = dialogBinding.etDiscount.text.toString().trim()
                val discount = discountStr.toDoubleOrNull()
                val isActive = dialogBinding.cbIsActive.isChecked
                
                val finalCalendar = Calendar.getInstance()
                finalCalendar.timeInMillis = selectedDate
                // MaterialDatePicker uses UTC, but we need to combine it with local time
                // Let's use a cleaner approach to combine date and time
                val dateCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                dateCal.timeInMillis = selectedDate
                
                finalCalendar.set(dateCal.get(Calendar.YEAR), dateCal.get(Calendar.MONTH), dateCal.get(Calendar.DAY_OF_MONTH))
                finalCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                finalCalendar.set(Calendar.MINUTE, selectedMinute)
                finalCalendar.set(Calendar.SECOND, 0)
                finalCalendar.set(Calendar.MILLISECOND, 0)
                
                if (name.isBlank()) {
                    dialogBinding.etPromoName.error = "Nama promo wajib diisi"
                    return@setOnClickListener
                }
                
                if (discount == null || discount <= 0 || discount > 100) {
                    dialogBinding.etDiscount.error = "Diskon harus antara 1-100"
                    return@setOnClickListener
                }

                savePromo(PromoEntity(
                    name = name,
                    description = if (description.isEmpty()) null else description,
                    discountPercent = discount,
                    validUntilEpochMs = finalCalendar.timeInMillis,
                    isActive = isActive
                ))
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun updateDateTimeText(db: DialogPromoFormBinding) {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val cal = Calendar.getInstance()
        // We use UTC for date from picker but need to display in local
        val dateCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        dateCal.timeInMillis = selectedDate
        
        cal.set(dateCal.get(Calendar.YEAR), dateCal.get(Calendar.MONTH), dateCal.get(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, selectedHour)
        cal.set(Calendar.MINUTE, selectedMinute)
        db.tvPromoDateTime.text = "Berlaku hingga: ${sdf.format(cal.time)}"
    }

    private fun savePromo(promo: PromoEntity) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.get(requireContext())
                db.promoDao().insert(promo)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Promo berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                    refreshData()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun togglePromo(promo: PromoEntity) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            db.promoDao().update(promo.copy(isActive = !promo.isActive))
            withContext(Dispatchers.Main) {
                refreshData()
            }
        }
    }

    private fun deletePromo(promo: PromoEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Promo")
            .setMessage("Apakah Anda yakin ingin menghapus promo '${promo.name}'?")
            .setPositiveButton("Hapus") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.get(requireContext())
                    db.promoDao().delete(promo)
                    withContext(Dispatchers.Main) {
                        refreshData()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    inner class PromoAdapter(private val items: List<PromoEntity>) : RecyclerView.Adapter<PromoAdapter.VH>() {
        inner class VH(val b: ItemPromoBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemPromoBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.b.tvPromoName.text = item.name
            holder.b.tvPromoDescription.text = item.description ?: ""
            holder.b.tvPromoDescription.visibility = if (item.description.isNullOrEmpty()) View.GONE else View.VISIBLE

            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            holder.b.tvPromoDetail.text = "Diskon ${item.discountPercent}% â€¢ S/d ${sdf.format(Date(item.validUntilEpochMs))}"
            
            holder.b.btnToggleActive.text = if (item.isActive) "Nonaktifkan" else "Aktifkan"
            holder.b.btnToggleActive.setOnClickListener { togglePromo(item) }
            
            holder.b.btnDelete.setOnClickListener { deletePromo(item) }
        }
        override fun getItemCount() = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


