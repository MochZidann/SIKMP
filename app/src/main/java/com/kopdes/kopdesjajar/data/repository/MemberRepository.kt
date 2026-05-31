package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.kopdes.kopdesjajar.data.db.*
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.MemberSyncPayload
import com.kopdes.kopdesjajar.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemberRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val memberDao = db.memberDao()
    private val firestoreManager = FirestoreManager()

    suspend fun insertMember(member: MemberEntity): Long = withContext(Dispatchers.IO) {
        val id = memberDao.insert(member)
        val insertedMember = member.copy(id = id)
        
        // Realtime Firebase
        firestoreManager.syncMember(insertedMember)
        
        // Sync Laravel
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
            val response = RetrofitClient.instance.syncMembers(payload)
            if (response.isSuccessful) {
                memberDao.updateSyncStatus(member.id, true)
                Log.d("SyncDebug", "✅ Member ${member.memberNo} synced to Laravel")
            } else {
                Log.e("SyncDebug", "❌ Gagal sync member: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Error sync member: ${e.message}")
        }
    }
}
