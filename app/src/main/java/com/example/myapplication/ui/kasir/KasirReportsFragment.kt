package com.example.myapplication.ui.kasir

import com.example.myapplication.ui.UiFormat
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.databinding.FragmentKasirReportsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class KasirReportsFragment : Fragment() {
    private var _binding: FragmentKasirReportsBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    private var adapter: KasirReportsAdapter? = null
    private var filterFromEpochMs: Long? = null
    private var filterToEpochMs: Long? = null

    // Simpan data chart agar bisa di-toggle
    private var chartLabels: List<String> = emptyList()
    private var chartPendapatan: List<Long> = emptyList()
    private var chartMargin: List<Long> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKasirReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        adapter = KasirReportsAdapter { saleId -> showSaleDetail(saleId) }
        binding.recyclerRows.adapter = adapter

        val today = rangeDay()
        filterFromEpochMs = today.first
        filterToEpochMs = today.second
        updateFilterButtons()

        binding.btnFrom.setOnClickListener { pickDate(true) }
        binding.btnTo.setOnClickListener { pickDate(false) }
        binding.btnApply.setOnClickListener { refresh() }

        // ✅ Setup toggle chart Pendapatan / Margin
        binding.toggleChartType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnChartPendapatan -> binding.chartPenjualan.setData(
                        chartLabels, chartPendapatan, R.color.accent_teal
                    )
                    R.id.btnChartMargin -> binding.chartPenjualan.setData(
                        chartLabels, chartMargin, R.color.accent_blue
                    )
                }
            }
        }
        // ✅ Default pilih Pendapatan
        binding.toggleChartType.check(R.id.btnChartPendapatan)

        refresh()
    }

    private fun pickDate(isFrom: Boolean) {
        val cal = Calendar.getInstance()
        val initial = if (isFrom) filterFromEpochMs else filterToEpochMs
        if (initial != null) cal.timeInMillis = initial
        DatePickerDialog(requireContext(), { _, year, month, day ->
            val c = Calendar.getInstance()
            c.set(year, month, day, 0, 0, 0)
            c.set(Calendar.MILLISECOND, 0)
            val from = c.timeInMillis
            c.add(Calendar.DAY_OF_MONTH, 1)
            val to = c.timeInMillis - 1
            if (isFrom) filterFromEpochMs = from else filterToEpochMs = to
            updateFilterButtons()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateFilterButtons() {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("in", "ID"))
        binding.btnFrom.text = filterFromEpochMs?.let { fmt.format(Date(it)) } ?: "Mulai"
        binding.btnTo.text = filterToEpochMs?.let { fmt.format(Date(it)) } ?: "Selesai"
    }

    private fun refresh() {
        val from = filterFromEpochMs ?: rangeDay().first
        val to = filterToEpochMs ?: rangeDay().second

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val sales = db.salesDao().listSalesBetween(from, to)
            val counts = db.salesDao().itemCountBySaleIds(sales.map { it.id })
                .associateBy({ it.saleId }, { it.itemCount })
            val timeFmt = SimpleDateFormat("dd/MM HH:mm", Locale("in", "ID"))
            val dayFmt = SimpleDateFormat("dd/MM", Locale("in", "ID"))

            val rows = sales.map { s ->
                KasirReportRow(
                    saleId = s.id,
                    dateTimeText = timeFmt.format(Date(s.createdAtEpochMs)),
                    cashierText = session.username().orEmpty(),
                    itemCountText = (counts[s.id] ?: 0L).toString(),
                    totalText = UiFormat.money(s.total),
                    methodText = "Tunai",
                    statusText = "SUKSES"
                )
            }

            val totalTrx = sales.size.toLong()
            val totalPendapatan = sales.sumOf { it.total }
            val labaKotor = sales.sumOf { (it.subtotal - it.discount).coerceAtLeast(0) }

            // ✅ Kelompokkan data per hari untuk chart
            val grouped = sales.groupBy { dayFmt.format(Date(it.createdAtEpochMs)) }
            val labels = grouped.keys.toList().sorted()
            val pendapatanPerHari = labels.map { label ->
                (grouped[label]?.sumOf { it.total } ?: 0L) / 1000L
            }
            val marginPerHari = labels.map { label ->
                (grouped[label]?.sumOf {
                    (it.subtotal - it.discount).coerceAtLeast(0)
                } ?: 0L) / 1000L
            }

            AuditLogger.log(
                requireContext(), session.userId(),
                "VIEW", "report_kasir", null, "from=$from to=$to"
            )

            withContext(Dispatchers.Main) {
                binding.txtTotalTrx.text = totalTrx.toString()
                binding.txtTotalPendapatan.text = UiFormat.money(totalPendapatan)
                binding.txtLabaKotor.text = UiFormat.money(labaKotor)
                adapter?.submit(rows)

                // ✅ Update chart
                chartLabels = labels
                chartPendapatan = pendapatanPerHari
                chartMargin = marginPerHari

                // ✅ Tampilkan sesuai toggle yang aktif
                val checkedId = binding.toggleChartType.checkedButtonId
                when (checkedId) {
                    R.id.btnChartPendapatan -> binding.chartPenjualan.setData(
                        labels, pendapatanPerHari, R.color.accent_teal
                    )
                    R.id.btnChartMargin -> binding.chartPenjualan.setData(
                        labels, marginPerHari, R.color.accent_blue
                    )
                    else -> binding.chartPenjualan.setData(
                        labels, pendapatanPerHari, R.color.accent_teal
                    )
                }
            }
        }
    }

    private fun showSaleDetail(saleId: Long) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val sale = db.salesDao().findSaleById(saleId)
            val items = db.salesDao().listItemsBySaleId(saleId)
            val text = buildString {
                append("Koperasi Merah Putih\nStruk #").append(saleId).append('\n')
                if (sale != null) append("Tanggal: ")
                    .append(UiFormat.dateTime(sale.createdAtEpochMs)).append('\n')
                append("Kasir: ").append(session.username().orEmpty()).append("\n\n")
                for (it in items) {
                    append(it.productName).append(" x").append(it.quantity)
                        .append(" = ").append(UiFormat.money(it.lineTotal)).append('\n')
                }
                if (sale != null) {
                    append("\nSubtotal: ").append(UiFormat.money(sale.subtotal))
                    append("\nDiskon: ").append(UiFormat.money(sale.discount))
                    append("\nPajak: ").append(UiFormat.money(sale.tax))
                    append("\nTotal: ").append(UiFormat.money(sale.total)).append('\n')
                }
            }
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Detail Transaksi")
                    .setMessage(text)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun rangeDay(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val from = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val to = cal.timeInMillis - 1
        return from to to
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}