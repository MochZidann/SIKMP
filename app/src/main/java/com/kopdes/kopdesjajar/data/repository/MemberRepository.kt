package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.kopdes.kopdesjajar.data.db.*
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.MemberSyncPayload
import com.kopdes.kopdesjajar.data.network.VolleyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemberRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val memberDao = db.memberDao()
    private val firestoreManager = FirestoreManager()

    suspend fun insertMember(member: MemberEntity): Long = withContext(Dispatchers.IO) {
        val id = memberDao.insert(member)
        val insertedMember = member.copy(id = id)
        
        firestoreManager.syncMember(insertedMember)
        syncToLaravel(insertedMember)
        
        return@withContext id
    }

    suspend fun updateMember(member: MemberEntity) = withContext(Dispatchers.IO) {
        memberDao.update(member)
        firestoreManager.syncMember(member)
        syncToLaravel(member)
    }

    private suspend fun syncToLaravel(member: MemberEntity) {
        try {
            val payload = listOf(MemberSyncPayload(
                id = member.id,
                memberNo = member.memberNo,
                name = member.name,
                phone = member.phone,
                address = member.address,
                isActive = if (member.isActive) 1 else 0,
                createdAtEpochMs = member.createdAtEpochMs
            ))
            VolleyHelper.requestObject(context, Request.Method.POST, "sync/members", payload)
            memberDao.updateSyncStatus(member.id, true)
            Log.d("SyncDebug", "✅ Member ${member.memberNo} synced to Laravel via Volley")
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Volley error syncing member: ${e.message}")
        }
    }
}
