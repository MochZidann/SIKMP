package com.kopdes.kopdesjajar.ui.admin

import com.kopdes.kopdesjajar.ui.UiFormat
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kopdes.kopdesjajar.R
import com.kopdes.kopdesjajar.data.auth.SessionManager
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.db.AuditLogEntity
import com.kopdes.kopdesjajar.data.model.Role
import com.kopdes.kopdesjajar.data.pref.PreferenceManager
import com.kopdes.kopdesjajar.util.UiHelper
import com.kopdes.kopdesjajar.databinding.FragmentAdminDashboardBinding
import com.kopdes.kopdesjajar.databinding.ItemSimpleRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminDashboardFragment : Fragment() {
    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    private lateinit var prefManager: PreferenceManager
    
    private var allLogs = listOf<AuditLogEntity>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        prefManager = PreferenceManager(requireContext())
        
        setupUI()
        refreshData()
    }

    private fun setupUI() {
        binding.recyclerMain.layoutManager = LinearLayoutManager(requireContext())
        setupShortcutNavigation()
        
        binding.btnTextSettings.setOnClickListener {
            (activity as? com.kopdes.kopdesjajar.ui.DashboardActivity)?.showTextSizeDialog()
        }
        
        // Terapkan ukuran teks yang disimpan
        UiHelper.applyTextSize(binding.root, prefManager)
    }

    private fun setupShortcutNavigation() {
        binding.cardUsers.setOnClickListener {
            (activity as? com.kopdes.kopdesjajar.ui.DashboardActivity)?.navigateTo(R.id.nav_admin_users)
        }
        binding.cardMembers.setOnClickListener {
            (activity as? com.kopdes.kopdesjajar.ui.DashboardActivity)?.navigateTo(R.id.nav_admin_members)
        }
        binding.cardPromos.setOnClickListener {
            (activity as? com.kopdes.kopdesjajar.ui.DashboardActivity)?.navigateTo(R.id.nav_admin_promo)
        }
    }

    private fun refreshData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val usersCount = db.userDao().getAll().size
            val membersCount = db.memberDao().getAll().size
            allLogs = db.auditLogDao().latest(10)
            val promosCount = db.promoDao().getAll().count { it.isActive }
            
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.txtStatUsers.text = usersCount.toString()
                binding.txtStatMembers.text = membersCount.toString()
                binding.txtStatPromos.text = promosCount.toString()
                binding.summaryText.text = "Sistem Online | $usersCount Users | $membersCount Members"
                
                binding.recyclerMain.adapter = LogAdapter(allLogs)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class LogAdapter(private val items: List<AuditLogEntity>) : RecyclerView.Adapter<LogAdapter.VH>() {
        inner class VH(val b: ItemSimpleRowBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemSimpleRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            
            val cleanAction = when(item.action.uppercase()) {
                "CREATE" -> "Tambah"
                "UPDATE" -> "Ubah"
                "DELETE" -> "Hapus"
                "LOGIN" -> "Login"
                else -> item.action.replaceFirstChar { it.uppercase() }
            }
            
            holder.b.textTitle.text = "$cleanAction ${item.entity}"
            holder.b.textSubtitle.text = "${UiFormat.dateTime(item.createdAtEpochMs)} \u2022 ${item.detail ?: ""}"
            
            val iconRes = when(item.action.uppercase()) {
                "CREATE" -> android.R.drawable.ic_input_add
                "DELETE" -> android.R.drawable.ic_delete
                "LOGIN" -> android.R.drawable.ic_lock_lock
                else -> android.R.drawable.ic_dialog_info
            }
            holder.b.imgIcon.setImageResource(iconRes)
            
            val bgColor = when(item.action.uppercase()) {
                "CREATE" -> 0xFFE8F5E9.toInt() // Green
                "DELETE" -> 0xFFFFEBEE.toInt() // Red
                "UPDATE" -> 0xFFE3F2FD.toInt() // Blue
                else -> 0xFFF5F5F5.toInt()      // Gray
            }
            holder.b.cardIcon.setCardBackgroundColor(bgColor)
            holder.b.imgAction.visibility = View.GONE
            
            // Terapkan ukuran teks ke item list
            UiHelper.applyTextSize(holder.itemView, prefManager)
        }
        override fun getItemCount() = items.size
    }
}
