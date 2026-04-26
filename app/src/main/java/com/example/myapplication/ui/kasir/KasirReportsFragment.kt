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
import com.example.myapplication.data.db.SaleEntity
import com.example.myapplication.data.db.SaleItemEntity
import com.example.myapplication.databinding.FragmentKasirReportsBinding
import com.example.myapplication.databinding.DialogReceiptDetailBinding
import com.example.myapplication.databinding.ItemReceiptProductBinding
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
            val selected = c.timeInMillis

            if (isFrom) {
                filterFromEpochMs = selected
            } else {
                c.set(year, month, day, 23, 59, 59)
                c.set(Calendar.MILLISECOND, 999)
                filterToEpochMs = c.timeInMillis
            }
            updateFilterButtons()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateFilterButtons() {
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale("in", "ID"))
        binding.btnFrom.text = filterFromEpochMs?.let { fmt.format(Date(it)) } ?: "Mulai"
        binding.btnTo.text = filterToEpochMs?.let { fmt.format(Date(it)) } ?: "Selesai"
    }

    private fun refresh() {
        val from = filterFromEpochMs ?: rangeDay().first
        val to = filterToEpochMs ?: rangeDay().second

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val sales = db.salesDao().listSalesBetween(from, to)
            val timeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("in", "ID"))
            val dayFmt = SimpleDateFormat("dd/MM", Locale("in", "ID"))

            val rows = sales.map { s ->
                KasirReportRow(
                    saleId = s.id,
                    displayId = generateReceiptId(s.id, s.createdAtEpochMs),
                    dateTimeText = timeFmt.format(Date(s.createdAtEpochMs)),
                    cashierText = "Kasir: ${session.username().orEmpty()}",
                    itemCountText = "", 
                    totalText = UiFormat.money(s.total),
                    methodText = s.paymentMethod,
                    statusText = s.status
                )
            }

            val totalTrx = sales.size.toLong()
            val totalPendapatan = sales.sumOf { it.total }
            val labaKotor = sales.sumOf { (it.subtotal - it.discount).coerceAtLeast(0) }

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

            AuditLogger.log(requireContext(), session.userId(), "VIEW", "report_kasir", null, "from=$from to=$to")

            withContext(Dispatchers.Main) {
                binding.txtTotalTrx.text = totalTrx.toString()
                binding.txtTotalPendapatan.text = UiFormat.money(totalPendapatan)
                binding.txtLabaKotor.text = UiFormat.money(labaKotor)
                adapter?.submit(rows)

                chartLabels = labels
                chartPendapatan = pendapatanPerHari
                chartMargin = marginPerHari

                val checkedId = binding.toggleChartType.checkedButtonId
                when (checkedId) {
                    R.id.btnChartPendapatan -> binding.chartPenjualan.setData(labels, pendapatanPerHari, R.color.accent_teal)
                    R.id.btnChartMargin -> binding.chartPenjualan.setData(labels, marginPerHari, R.color.accent_blue)
                    else -> binding.chartPenjualan.setData(labels, pendapatanPerHari, R.color.accent_teal)
                }
            }
        }
    }

    private fun generateReceiptId(saleId: Long, timestamp: Long): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(timestamp))
        val sequence = (saleId % 10000).toString().padStart(4, '0')
        return "64174$datePart$sequence"
    }

    private fun showSaleDetail(saleId: Long) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val sale = db.salesDao().findSaleById(saleId) ?: return@launch
            val items = db.salesDao().listItemsBySaleId(saleId)
            val settings = db.settingsDao().get()

            withContext(Dispatchers.Main) {
                val b = DialogReceiptDetailBinding.inflate(layoutInflater)

                b.txtKoperasiName.text = settings?.koperasiName ?: "Koperasi Merah Putih"
                b.txtKoperasiAddress.text = settings?.koperasiAddress ?: "Alamat Koperasi"
                b.txtKoperasiPhone.text = settings?.koperasiPhone ?: "No. Telp"
                
                val code = generateReceiptId(sale.id, sale.createdAtEpochMs)
                b.txtReceiptCode.text = code
                b.txtTransactionId.text = "#$code"

                b.txtDate.text = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sale.createdAtEpochMs))
                b.txtTime.text = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(sale.createdAtEpochMs))
                b.txtCashier.text = session.username()
                b.txtNo.text = "No.${sale.id % 1000}"

                var totalQty = 0L
                items.forEachIndexed { index, item ->
                    val ib = ItemReceiptProductBinding.inflate(layoutInflater, b.itemsContainer, true)
                    ib.txtProductName.text = "${index + 1}. ${item.productName}"
                    ib.txtProductQtyPrice.text = "   ${item.quantity} x ${UiFormat.money(item.unitPrice).replace("Rp", "").trim()}"
                    ib.txtProductLineTotal.text = UiFormat.money(item.lineTotal)
                    totalQty += item.quantity
                }

                b.txtTotalQty.text = "Total QTY : $totalQty"
                b.txtSubtotal.text = UiFormat.money(sale.subtotal)
                b.txtTotal.text = UiFormat.money(sale.total)
                b.txtLabelPay.text = "Bayar (${sale.paymentMethod})"
                // Note: paid amount isn't in SaleEntity, using total as placeholder for history
                b.txtPay.text = UiFormat.money(sale.total) 
                b.txtChange.text = "Rp0"
                
                b.txtLink.text = "sikmp.com/e-receipt/$code"

                AlertDialog.Builder(requireContext())
                    .setView(b.root)
                    .setPositiveButton("Tutup", null)
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
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val to = cal.timeInMillis
        return from to to
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
