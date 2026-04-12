package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentOwnerDashboardBinding
import com.example.myapplication.ui.owner.OwnerStockReportActivity

class OwnerDashboardFragment : Fragment() {
    private var _binding: FragmentOwnerDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOwnerDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnStockReport.setOnClickListener { startActivity(Intent(requireContext(), OwnerStockReportActivity::class.java)) }
        binding.btnSalesReport.setOnClickListener { startActivity(Intent(requireContext(), ReportsActivity::class.java)) }
        binding.btnAudit.setOnClickListener { startActivity(Intent(requireContext(), AuditTrailActivity::class.java)) }
        binding.btnUsers.setOnClickListener { startActivity(Intent(requireContext(), UserManagementActivity::class.java)) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

