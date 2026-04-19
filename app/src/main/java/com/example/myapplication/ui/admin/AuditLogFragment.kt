package com.example.myapplication.ui.admin

import com.example.myapplication.ui.UiFormat
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.AuditLogEntity
import com.example.myapplication.databinding.FragmentAuditLogBinding
import com.example.myapplication.databinding.ItemSimpleRowBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream

class AuditLogFragment : Fragment() {
    private var _binding: FragmentAuditLogBinding? = null
    private val binding get() = _binding!!

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri: Uri? ->
        uri?.let { performExport(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAuditLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerLogs.layoutManager = LinearLayoutManager(requireContext())
        
        binding.btnExport.setOnClickListener {
            val fileName = "Audit_Log_${System.currentTimeMillis()}.xlsx"
            exportLauncher.launch(fileName)
        }
        
        refreshData()
    }

    private fun refreshData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val logs = db.auditLogDao().latest(200)
            withContext(Dispatchers.Main) {
                binding.recyclerLogs.adapter = LogAdapter(logs)
            }
        }
    }

    private fun performExport(uri: Uri) {
        val adapter = binding.recyclerLogs.adapter as? LogAdapter ?: return
        val logs = adapter.getItems()
        if (logs.isEmpty()) {
            Toast.makeText(requireContext(), "Tidak ada data untuk diexport", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val workbook = XSSFWorkbook()
                val sheet = workbook.createSheet("Audit Logs")
                
                // Header
                val headerRow = sheet.createRow(0)
                headerRow.createCell(0).setCellValue("Waktu")
                headerRow.createCell(1).setCellValue("User ID")
                headerRow.createCell(2).setCellValue("Aksi")
                headerRow.createCell(3).setCellValue("Entitas")
                headerRow.createCell(4).setCellValue("Detail")

                // Data
                logs.forEachIndexed { index, log ->
                    val row = sheet.createRow(index + 1)
                    row.createCell(0).setCellValue(UiFormat.dateTime(log.createdAtEpochMs))
                    row.createCell(1).setCellValue(log.userId?.toDouble() ?: 0.0)
                    row.createCell(2).setCellValue(log.action)
                    row.createCell(3).setCellValue(log.entity)
                    row.createCell(4).setCellValue(log.detail)
                }

                val outputStream: OutputStream? = requireContext().contentResolver.openOutputStream(uri)
                outputStream?.use { workbook.write(it) }
                workbook.close()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Audit Log berhasil diekspor", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal export: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class LogAdapter(private val items: List<AuditLogEntity>) : RecyclerView.Adapter<LogAdapter.VH>() {
        fun getItems() = items
        inner class VH(val b: ItemSimpleRowBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemSimpleRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.b.textTitle.text = "${item.action} â€¢ ${item.entity}"
            holder.b.textSubtitle.text = "${UiFormat.dateTime(item.createdAtEpochMs)} â€¢ ${item.detail}"
            holder.b.imgAction.visibility = View.GONE
        }
        override fun getItemCount() = items.size
    }
}
