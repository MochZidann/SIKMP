package com.example.myapplication.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.model.Role
import com.example.myapplication.databinding.ActivitySimpleListBinding
import com.example.myapplication.ui.adapters.TwoLineAdapter
import com.example.myapplication.ui.adapters.TwoLineRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class AuditTrailActivity : BaseAuthedActivity() {
    private lateinit var binding: ActivitySimpleListBinding
    private val adapter = TwoLineAdapter { row -> showDetail(row.id) }
    private var currentRange: Pair<Long, Long>? = null
    private var currentLogs: List<com.example.myapplication.data.db.AuditLogEntity> = emptyList()

    override fun allowedRoles(): Set<Role> = setOf(Role.OWNER_PENGAWAS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.title = "Audit Trail"
        binding.toolbar.subtitle = "Ketuk untuk filter"
        binding.recycler.adapter = adapter
        binding.fab.visibility = android.view.View.GONE

        binding.toolbar.setOnClickListener { pickFilter() }
    }

    override fun onResume() {
        super.onResume()
        currentRange = rangeDaysBack(7)
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun pickFilter() {
        AlertDialog.Builder(this)
            .setTitle("Filter")
            .setItems(arrayOf("Hari ini", "Minggu ini", "Bulan ini", "Semua")) { _, which ->
                currentRange = when (which) {
                    0 -> rangeDay()
                    1 -> rangeDaysBack(7)
                    2 -> rangeMonth()
                    else -> null
                }
                refresh()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun refresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(this@AuditTrailActivity)
            val range = currentRange
            val logs = if (range == null) db.auditLogDao().latest(200) else db.auditLogDao().between(range.first, range.second)
            currentLogs = logs
            val rows = logs.map {
                TwoLineRow(
                    id = it.id,
                    title = "${it.action} • ${it.entity}",
                    subtitle = "${UiFormat.dateTime(it.createdAtEpochMs)} • userId=${it.userId ?: "-"}"
                )
            }
            withContext(Dispatchers.Main) {
                adapter.submit(rows)
                binding.toolbar.subtitle = when (range) {
                    null -> "Semua • ${logs.size} log"
                    else -> "${UiFormat.dateOnly(range.first)} - ${UiFormat.dateOnly(range.second)} • ${logs.size} log"
                }
            }
        }
    }

    private fun showDetail(id: Long) {
        val log = currentLogs.firstOrNull { it.id == id } ?: return
        AlertDialog.Builder(this)
            .setTitle("Detail")
            .setMessage(
                "Waktu: ${UiFormat.dateTime(log.createdAtEpochMs)}\n" +
                    "UserId: ${log.userId ?: "-"}\n" +
                    "Aksi: ${log.action}\n" +
                    "Entitas: ${log.entity}\n" +
                    "EntityId: ${log.entityId ?: "-"}\n" +
                    "Detail: ${log.detail ?: "-"}"
            )
            .setPositiveButton("OK", null)
            .show()
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
