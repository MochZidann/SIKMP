package com.example.myapplication.ui

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        // RecyclerView Setup
        binding.recyclerMain.layoutManager = LinearLayoutManager(requireContext())
        
        // Stats Cards are now purely informational as requested (no click-to-list)
    }

    private fun refreshData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val usersCount = db.userDao().getAll().size
            val membersCount = db.memberDao().getAll().size
            // Pagination: limit to 10 latest activities
            allLogs = db.auditLogDao().latest(10)
            val promosCount = db.promoDao().getAll().count { it.isActive }
            
            withContext(Dispatchers.Main) {
                binding.txtStatUsers.text = usersCount.toString()
                binding.txtStatMembers.text = membersCount.toString()
                binding.txtStatPromos.text = promosCount.toString()
                binding.summaryText.text = "Sistem Online • $usersCount Users • $membersCount Members"
                
                // Show logs with no edit/delete capabilities
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
            holder.b.textTitle.text = "${item.action} • ${item.entity}"
            holder.b.textSubtitle.text = "${UiFormat.dateTime(item.createdAtEpochMs)} • ${item.detail ?: ""}"
            // Hide action button (edit/delete) for audit logs
            holder.b.imgAction.visibility = View.GONE
        }
        override fun getItemCount() = items.size
    }
}
