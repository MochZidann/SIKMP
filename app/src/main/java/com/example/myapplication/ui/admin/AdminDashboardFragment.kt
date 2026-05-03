package com.example.myapplication.ui.admin

import com.example.myapplication.ui.UiFormat
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.AuditLogEntity
import com.example.myapplication.databinding.FragmentAdminDashboardBinding
import com.example.myapplication.databinding.ItemSimpleRowBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminDashboardFragment : Fragment() {
    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    
    private var allLogs = listOf<AuditLogEntity>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        
        setupUI()
        refreshData()
    }

    private fun setupUI() {
        binding.recyclerMain.layoutManager = LinearLayoutManager(requireContext())
        setupShortcutNavigation()
    }

    private fun setupShortcutNavigation() {
        binding.cardUsers.setOnClickListener {
            (activity as? com.example.myapplication.ui.DashboardActivity)?.navigateTo(R.id.nav_admin_users)
        }
        binding.cardMembers.setOnClickListener {
            (activity as? com.example.myapplication.ui.DashboardActivity)?.navigateTo(R.id.nav_admin_members)
        }
        binding.cardPromos.setOnClickListener {
            (activity as? com.example.myapplication.ui.DashboardActivity)?.navigateTo(R.id.nav_admin_promo)
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
            
            // Clean action text: "CREATE" -> "Tambah", "UPDATE" -> "Ubah", "DELETE" -> "Hapus", "LOGIN" -> "Login"
            val cleanAction = when(item.action.uppercase()) {
                "CREATE" -> "Tambah"
                "UPDATE" -> "Ubah"
                "DELETE" -> "Hapus"
                "LOGIN" -> "Login"
                else -> item.action.replaceFirstChar { it.uppercase() }
            }
            
            holder.b.textTitle.text = "$cleanAction ${item.entity}"
            holder.b.textSubtitle.text = "${UiFormat.dateTime(item.createdAtEpochMs)} \u2022 ${item.detail ?: ""}"
            
            // Dynamic icons based on action
            val iconRes = when(item.action.uppercase()) {
                "CREATE" -> android.R.drawable.ic_input_add
                "DELETE" -> android.R.drawable.ic_delete
                "LOGIN" -> android.R.drawable.ic_lock_lock
                else -> android.R.drawable.ic_dialog_info
            }
            holder.b.imgIcon.setImageResource(iconRes)
            
            // Icon Background Colors
            val bgColor = when(item.action.uppercase()) {
                "CREATE" -> 0xFFE8F5E9.toInt() // Green
                "DELETE" -> 0xFFFFEBEE.toInt() // Red
                "UPDATE" -> 0xFFE3F2FD.toInt() // Blue
                else -> 0xFFF5F5F5.toInt()      // Gray
            }
            holder.b.cardIcon.setCardBackgroundColor(bgColor)

            holder.b.imgAction.visibility = View.GONE
        }
        override fun getItemCount() = items.size
    }
}
