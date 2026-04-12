package com.example.myapplication.ui.admin_gudang

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentAdminGudangMasterBinding
import com.example.myapplication.ui.MemberManagementFragment
import com.example.myapplication.ui.PromoConfigFragment

class AdminGudangMasterFragment : Fragment() {
    private var _binding: FragmentAdminGudangMasterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGudangMasterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.cardMembers.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.content, MemberManagementFragment())
                .addToBackStack("master_members")
                .commit()
        }
        binding.cardPromos.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.content, PromoConfigFragment())
                .addToBackStack("master_promos")
                .commit()
        }
        binding.cardProducts.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.content, AdminGudangProductsFragment())
                .addToBackStack("master_products")
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
