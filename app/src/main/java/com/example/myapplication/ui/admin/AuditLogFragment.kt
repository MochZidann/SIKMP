package com.example.myapplication.ui.admin

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.util.Pair
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.AuditLogEntity
import com.example.myapplication.databinding.FragmentAuditLogBinding
import com.example.myapplication.databinding.ItemAuditLogRowBinding
import com.example.myapplication.ui.UiFormat
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.util.*

class AuditLogFragment : Fragment() {
    private var _binding: FragmentAuditLogBinding? = null
    private val binding get() = _binding!!
    
    private var startDate: Long? = null
    private var endDate: Long? = null

    private val excelExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri: Uri? ->
        uri?.let { performExcelExport(it) }
    }

    private val pdfExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        uri?.let { performPdfExport(it) }
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
            excelExportLauncher.launch(fileName)
        }

        binding.btnExportPdf.setOnClickListener {
            val fileName = "Audit_Log_${System.currentTimeMillis()}.pdf"
            pdfExportLauncher.launch(fileName)
        }
        
        binding.etSearch.addTextChangedListener {
            refreshData()
        }

        binding.btnDateFilter.setOnClickListener {
            val picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Pilih Rentang Tanggal")
                .setSelection(Pair(startDate ?: System.currentTimeMillis(), endDate ?: System.currentTimeMillis()))
                .build()
            
            picker.addOnPositiveButtonClickListener { selection ->
                startDate = selection.first
                val cal = Calendar.getInstance()
                cal.timeInMillis = selection.second ?: System.currentTimeMillis()
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                endDate = cal.timeInMillis
                
                binding.btnDateFilter.text = "${UiFormat.dateOnly(startDate!!)} - ${UiFormat.dateOnly(endDate!!)}"
                refreshData()
            }
            picker.show(childFragmentManager, "DATE_PICKER")
        }
        
        refreshData()
    }

    private fun refreshData() {
        val query = binding.etSearch.text?.toString().orEmpty()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val from = startDate ?: 0L
            val to = endDate ?: Long.MAX_VALUE
            
            val logs = if (query.isBlank() && startDate == null) {
                db.auditLogDao().latest(500)
            } else {
                db.auditLogDao().search(query, from, to)
            }
            
            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    binding.recyclerLogs.adapter = LogAdapter(logs)
                }
            }
        }
    }

    private fun performExcelExport(uri: Uri) {
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
                
                val headerRow = sheet.createRow(0)
                headerRow.createCell(0).setCellValue("Waktu")
                headerRow.createCell(1).setCellValue("Aksi")
                headerRow.createCell(2).setCellValue("Entitas")
                headerRow.createCell(3).setCellValue("Detail")

                logs.forEachIndexed { index, log ->
                    val row = sheet.createRow(index + 1)
                    row.createCell(0).setCellValue(UiFormat.dateTime(log.createdAtEpochMs))
                    row.createCell(1).setCellValue(log.action)
                    row.createCell(2).setCellValue(log.entity)
                    row.createCell(3).setCellValue(log.detail)
                }

                val outputStream: OutputStream? = requireContext().contentResolver.openOutputStream(uri)
                outputStream?.use { workbook.write(it) }
                workbook.close()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Audit Log berhasil diekspor ke Excel", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal export Excel: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun performPdfExport(uri: Uri) {
        val adapter = binding.recyclerLogs.adapter as? LogAdapter ?: return
        val logs = adapter.getItems()
        if (logs.isEmpty()) {
            Toast.makeText(requireContext(), "Tidak ada data untuk diexport", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pdfDocument = PdfDocument()
                val paint = Paint()
                val titlePaint = Paint().apply {
                    textSize = 18f
                    isFakeBoldText = true
                    color = Color.BLACK
                }
                val headerPaint = Paint().apply {
                    textSize = 12f
                    isFakeBoldText = true
                    color = Color.BLACK
                }
                val textPaint = Paint().apply {
                    textSize = 10f
                    color = Color.DKGRAY
                }

                // A4 Size: 595 x 842 points
                val pageWidth = 595
                val pageHeight = 842
                var pageNumber = 1
                
                var myPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                var myPage = pdfDocument.startPage(myPageInfo)
                var canvas: Canvas = myPage.canvas
                
                var y = 50f
                canvas.drawText("Laporan Audit Log SIKMP", 40f, y, titlePaint)
                y += 20f
                canvas.drawText("Dicetak pada: ${UiFormat.dateTime(System.currentTimeMillis())}", 40f, y, textPaint)
                y += 40f

                // Draw Table Headers
                canvas.drawText("WAKTU", 40f, y, headerPaint)
                canvas.drawText("AKSI", 180f, y, headerPaint)
                canvas.drawText("ENTITAS", 300f, y, headerPaint)
                canvas.drawText("DETAIL", 400f, y, headerPaint)
                y += 10f
                canvas.drawLine(40f, y, 555f, y, paint)
                y += 20f

                logs.forEach { log ->
                    if (y > pageHeight - 50) {
                        pdfDocument.finishPage(myPage)
                        pageNumber++
                        myPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        myPage = pdfDocument.startPage(myPageInfo)
                        canvas = myPage.canvas
                        y = 50f
                        
                        // Re-draw headers on new page
                        canvas.drawText("WAKTU", 40f, y, headerPaint)
                        canvas.drawText("AKSI", 180f, y, headerPaint)
                        canvas.drawText("ENTITAS", 300f, y, headerPaint)
                        canvas.drawText("DETAIL", 400f, y, headerPaint)
                        y += 10f
                        canvas.drawLine(40f, y, 555f, y, paint)
                        y += 20f
                    }

                    canvas.drawText(UiFormat.dateTime(log.createdAtEpochMs), 40f, y, textPaint)
                    canvas.drawText(log.action, 180f, y, textPaint)
                    canvas.drawText(log.entity, 300f, y, textPaint)
                    
                    val detailText = log.detail ?: ""
                    val detail = if (detailText.length > 30) detailText.substring(0, 27) + "..." else detailText
                    canvas.drawText(detail, 400f, y, textPaint)
                    
                    y += 20f
                }

                pdfDocument.finishPage(myPage)

                val outputStream: OutputStream? = requireContext().contentResolver.openOutputStream(uri)
                outputStream?.use { pdfDocument.writeTo(it) }
                pdfDocument.close()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Audit Log berhasil diekspor ke PDF", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal export PDF: ${e.message}", Toast.LENGTH_SHORT).show()
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
        inner class VH(val b: ItemAuditLogRowBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemAuditLogRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.b.txtTime.text = UiFormat.dateTime(item.createdAtEpochMs)
            holder.b.txtAction.text = item.action
            holder.b.txtEntity.text = item.entity
            holder.b.txtDetail.text = item.detail
        }
        override fun getItemCount() = items.size
    }
}
