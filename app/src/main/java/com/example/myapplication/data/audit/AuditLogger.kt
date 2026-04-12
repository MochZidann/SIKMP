package com.example.myapplication.data.audit

import android.content.Context
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.AuditLogEntity

object AuditLogger {
    suspend fun log(
        context: Context,
        userId: Long?,
        action: String,
        entity: String,
        entityId: Long?,
        detail: String? = null
    ) {
        try {
            AppDatabase.get(context).auditLogDao().insert(
                AuditLogEntity(
                    userId = userId,
                    action = action,
                    entity = entity,
                    entityId = entityId,
                    detail = detail
                )
            )
        } catch (_: Exception) {
        }
    }
}
