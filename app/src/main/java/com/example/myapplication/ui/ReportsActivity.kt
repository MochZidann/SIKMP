package com.example.myapplication.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.model.Role
import com.example.myapplication.databinding.ActivityReportsKasirBinding
import com.example.myapplication.databinding.ActivitySimpleListBinding
import com.example.myapplication.ui.adapters.KasirReportsAdapter
import com.example.myapplication.ui.adapters.KasirReportRow
import com.example.myapplication.ui.adapters.TwoLineAdapter
import com.example.myapplication.ui.adapters.TwoLineRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportsActivity : BaseAuthedActivity() {
    private lateinit var binding: ActivitySimpleListBinding
    private var kasirBinding: ActivityReportsKasirBinding? = null
    private val adapter = TwoLineAdapter { row -> onPeriodClicked(row.id) }
    private var kasirAdapter: KasirReportsAdapter? = null
    private var filterFromEpochMs: Long? = null
    private var filterToEpochMs: Long? = null
    private var kasirToggle: ActionBarDrawerToggle? = null

    override fun allowedRoles(): Set<Role> = setOf(Role.ADMIN_GUDANG, Role.OWNER_PENGAWAS, Role.KASIR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (session.role() == Role.KASIR) {
            val kb = ActivityReportsKasirBinding.inflate(layoutInflater)
            kasirBinding = kb
            setContentView(kb.root)
            setSupportActionBar(kb.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            setupKasirDrawer(kb)
            kasirAdapter = KasirReportsAdapter { saleId -> showSaleDetail(saleId) }
            kb.recyclerRows.adapter = kasirAdapter

            val today = rangeDay()
            filterFromEpochMs = today.first
            filterToEpochMs = today.second
            updateFilterButtons()

            kb.btnFrom.setOnClickListener { pickDate(true) }
            kb.btnTo.setOnClickListener { pickDate(false) }
            kb.btnApply.setOnClickListener { refreshKasir() }
            return
        }
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.title = "Laporan"
        binding.recycler.adapter = adapter
        binding.fab.visibility = android.view.View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (kasirBinding != null) {
            refreshKasir()
        } else {
            refresh()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val kb = kasirBinding
        if (kb != null) {
            if (kb.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                kb.drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
            return super.onSupportNavigateUp()
        }
        finish()
        return true
    }

    private fun setupKasirDrawer(kb: ActivityReportsKasirBinding) {
        val header = kb.navigationView.getHeaderView(0)
        header.findViewById<TextView>(R.id.title)?.text = getString(R.string.app_name)
        header.findViewById<TextView>(R.id.subtitle)?.text =
            listOfNotNull(session.name(), session.username()).joinToString(" • ").ifBlank { "Kasir" }

        kasirToggle = ActionBarDrawerToggle(
            this,
            kb.drawerLayout,
            kb.toolbar,
            R.string.app_name,
            R.string.app_name
        )
        kb.drawerLayout.addDrawerListener(kasirToggle!!)
        kasirToggle!!.syncState()
        kasirToggle!!.drawerArrowDrawable.color = ContextCompat.getColor(this, android.R.color.white)

        kb.navigationView.setCheckedItem(R.id.nav_kasir_reports)
        kb.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_kasir_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_kasir_pos -> {
                    startActivity(Intent(this, PosActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_kasir_reports -> true
                R.id.nav_kasir_products -> {
                    startActivity(Intent(this, ProductManagementActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_kasir_logout -> {
                    session.clear()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }.also {
                kb.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (kb.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        kb.drawerLayout.closeDrawer(GravityCompat.START)
                        return
                    }
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        )
    }

    private fun pickDate(isFrom: Boolean) {
        val cal = Calendar.getInstance()
        val initial = if (isFrom) filterFromEpochMs else filterToEpochMs
        if (initial != null) cal.timeInMillis = initial
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val c = Calendar.getInstance()
                c.set(Calendar.YEAR, year)
                c.set(Calendar.MONTH, month)
                c.set(Calendar.DAY_OF_MONTH, day)
                c.set(Calendar.HOUR_OF_DAY, 0)
                c.set(Calendar.MINUTE, 0)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                val from = c.timeInMillis
                c.add(Calendar.DAY_OF_MONTH, 1)
                val to = c.timeInMillis - 1
                if (isFrom) filterFromEpochMs = from else filterToEpochMs = to
                updateFilterButtons()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateFilterButtons() {
        val kb = kasirBinding ?: return
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("in", "ID"))
        val from = filterFromEpochMs?.let { fmt.format(Date(it)) } ?: "Mulai"
        val to = filterToEpochMs?.let { fmt.format(Date(it)) } ?: "Selesai"
        kb.btnFrom.text = from
        kb.btnTo.text = to
    }

    private fun refreshKasir() {
        val kb = kasirBinding ?: return
        val from = filterFromEpochMs ?: rangeDay().first
        val to = filterToEpochMs ?: rangeDay().second
        kb.toolbar.subtitle = "${UiFormat.dateOnly(from)} - ${UiFormat.dateOnly(to)}"

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@ReportsActivity)
            val sales = db.salesDao().listSalesBetween(from, to)
            val counts = db.salesDao().itemCountBySaleIds(sales.map { it.id }).associateBy({ it.saleId }, { it.itemCount })
            val timeFmt = SimpleDateFormat("dd/MM HH:mm", Locale("in", "ID"))

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

            AuditLogger.log(
                context = this@ReportsActivity,
                userId = session.userId(),
                action = "VIEW",
                entity = "report_kasir",
                entityId = null,
                detail = "from=$from to=$to"
            )

            withContext(Dispatchers.Main) {
                kb.txtTotalTrx.text = totalTrx.toString()
                kb.txtTotalPendapatan.text = UiFormat.money(totalPendapatan)
                kb.txtLabaKotor.text = UiFormat.money(labaKotor)
                kasirAdapter?.submit(rows)
            }
        }
    }

    private fun showSaleDetail(saleId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@ReportsActivity)
            val sale = db.salesDao().findSaleById(saleId)
            val items = db.salesDao().listItemsBySaleId(saleId)
            val text = buildString {
                append("Koperasi Merah Putih\n")
                append("Struk #").append(saleId).append('\n')
                if (sale != null) append("Tanggal: ").append(UiFormat.dateTime(sale.createdAtEpochMs)).append('\n')
                append("Kasir: ").append(session.username().orEmpty()).append('\n')
                append('\n')
                for (it in items) {
                    append(it.productName).append(" x").append(it.quantity).append(" = ").append(UiFormat.money(it.lineTotal)).append('\n')
                }
                append('\n')
                if (sale != null) {
                    append("Subtotal: ").append(UiFormat.money(sale.subtotal)).append('\n')
                    append("Diskon: ").append(UiFormat.money(sale.discount)).append('\n')
                    append("Pajak: ").append(UiFormat.money(sale.tax)).append('\n')
                    append("Total: ").append(UiFormat.money(sale.total)).append('\n')
                }
            }
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@ReportsActivity)
                    .setTitle("Detail Transaksi")
                    .setMessage(text)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun refresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@ReportsActivity)
            val role = session.role()
            val daily = rangeDay()
            val weekly = rangeDaysBack(7)
            val monthly = rangeMonth()

            val dailySum = db.salesDao().summary(daily.first, daily.second)
            val weeklySum = db.salesDao().summary(weekly.first, weekly.second)
            val monthlySum = db.salesDao().summary(monthly.first, monthly.second)
            val stockTotal = if (role == Role.OWNER_PENGAWAS) db.productDao().getAll().sumOf { it.stock } else null

            val rows = listOf(
                TwoLineRow(1, "Harian", "Transaksi: ${dailySum.txnCount} • Total: ${UiFormat.money(dailySum.total)}"),
                TwoLineRow(7, "Mingguan", "Transaksi: ${weeklySum.txnCount} • Total: ${UiFormat.money(weeklySum.total)}"),
                TwoLineRow(30, "Bulanan", "Transaksi: ${monthlySum.txnCount} • Total: ${UiFormat.money(monthlySum.total)}")
            )
            AuditLogger.log(
                context = this@ReportsActivity,
                userId = session.userId(),
                action = "VIEW",
                entity = "report",
                entityId = null,
                detail = "role=${role?.name}"
            )
            withContext(Dispatchers.Main) {
                adapter.submit(
                    if (stockTotal == null) rows
                    else rows.map { it.copy(subtitle = it.subtitle + " • Total stok: $stockTotal") }
                )
            }
        }
    }

    private fun onPeriodClicked(days: Long) {
        val (from, to) = when (days) {
            1L -> rangeDay()
            7L -> rangeDaysBack(7)
            else -> rangeMonth()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@ReportsActivity)
            val sum = db.salesDao().summary(from, to)
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@ReportsActivity)
                    .setTitle("Rekap")
                    .setMessage("Periode: ${UiFormat.dateOnly(from)} - ${UiFormat.dateOnly(to)}\nTransaksi: ${sum.txnCount}\nTotal: ${UiFormat.money(sum.total)}")
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
        return Pair(from, to)
    }

    private fun rangeDaysBack(days: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val to = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, -days + 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val from = cal.timeInMillis
        return Pair(from, to)
    }

    private fun rangeMonth(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val from = cal.timeInMillis
        val to = System.currentTimeMillis()
        return Pair(from, to)
    }
}
