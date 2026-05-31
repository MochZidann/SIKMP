package com.kopdes.kopdesjajar.ui.kasir

import com.kopdes.kopdesjajar.ui.UiFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kopdes.kopdesjajar.R
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.databinding.FragmentKasirDashboardBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class KasirDashboardFragment : Fragment() {
    private var _binding: FragmentKasirDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKasirDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupShortcutNavigation()
        refresh()
    }

    private fun setupShortcutNavigation() {
        binding.cardRevenue.setOnClickListener {
            (activity as? com.kopdes.kopdesjajar.ui.DashboardActivity)?.navigateTo(R.id.nav_kasir_reports)
        }
        binding.cardTransactions.setOnClickListener {
            (activity as? com.kopdes.kopdesjajar.ui.DashboardActivity)?.navigateTo(R.id.nav_kasir_reports)
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
        val (from, to) = todayRange()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val summary = db.salesDao().summary(from, to)
            withContext(Dispatchers.Main) {
                binding.txtTotalToday.text = UiFormat.money(summary.total)
                binding.txtTxnToday.text = summary.txnCount.toString()
            }
        }
    }

    private fun todayRange(): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val from = c.timeInMillis
        c.add(Calendar.DAY_OF_MONTH, 1)
        val to = c.timeInMillis - 1
        return from to to
    }
}
