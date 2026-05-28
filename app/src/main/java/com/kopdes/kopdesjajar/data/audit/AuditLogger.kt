package com.kopdes.kopdesjajar.data.audit

import android.content.Context
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.db.AuditLogEntity
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.AuditLogSyncPayload
import com.kopdes.kopdesjajar.data.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AuditLogger {
    private val firestoreManager = FirestoreManager()

    suspend fun log(
        context: Context,
        userId: Long?,
        action: String,
        entity: String,
        entityId: Long?,
        detail: String? = null
    ) {
        try {
            val db = AppDatabase.get(context)
            val logEntry = AuditLogEntity(
                userId = userId,
                action = action,
                entity = entity,
                entityId = entityId,
                detail = detail
            )
            
            // 1. Simpan Lokal
            val id = db.auditLogDao().insert(logEntry)
            val insertedLog = logEntry.copy(id = id)

            // 2. Realtime Firebase
            firestoreManager.syncAuditLog(insertedLog)

            // 3. Sync Laravel
            syncToLaravel(insertedLog)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun syncToLaravel(log: AuditLogEntity) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = listOf(AuditLogSyncPayload(
                    id = log.id,
                    userId = log.userId,
                    action = log.action,
                    entity = log.entity,
                    entityId = log.entityId,
                    detail = log.detail,
                    createdAtEpochMs = log.createdAtEpochMs
                ))
                RetrofitClient.instance.syncAuditLogs(payload)
            } catch (e: Exception) {
                e.printStackTrace();
            }
        }
    }
}
