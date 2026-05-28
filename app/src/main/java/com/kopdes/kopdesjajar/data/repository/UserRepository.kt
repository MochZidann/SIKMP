package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.db.UserEntity
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.RetrofitClient
import com.kopdes.kopdesjajar.data.network.UserSyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val userDao = db.userDao()
    private val firestoreManager = FirestoreManager()

    suspend fun updateUser(user: UserEntity) = withContext(Dispatchers.IO) {
        // 1. Update Lokal
        userDao.update(user)

        // 2. Realtime ke Firebase
        firestoreManager.syncUser(user)

        // 3. Sync ke Laravel (MySQL)
        try {
            val payload = listOf(UserSyncPayload(
                id = user.id,
                name = user.name,
                username = user.username,
                passwordHash = user.passwordHash,
                salt = user.salt,
                role = user.role.name,
                isActive = if (user.isActive) 1 else 0,
                needsPasswordReset = if (user.needsPasswordReset) 1 else 0,
                createdAtEpochMs = user.createdAtEpochMs
            ))
            val response = RetrofitClient.instance.syncUsers(payload)
            if (response.isSuccessful) {
                userDao.updateSyncStatus(user.id, true)
                Log.d("SyncDebug", "✅ User ${user.username} synced to Laravel")
            } else {
                Log.e("SyncDebug", "❌ Gagal sync user ${user.username}: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Error sync user: ${e.message}")
        }
    }

    suspend fun findByUsername(username: String): UserEntity? = withContext(Dispatchers.IO) {
        return@withContext userDao.findByUsername(username)
    }
}
