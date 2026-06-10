package com.kopdes.kopdesjajar.data.repository

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.kopdes.kopdesjajar.data.db.AppDatabase
import com.kopdes.kopdesjajar.data.db.UserEntity
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.network.VolleyHelper
import com.kopdes.kopdesjajar.data.network.UserSyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val userDao = db.userDao()
    private val firestoreManager = FirestoreManager()

    suspend fun updateUser(user: UserEntity) = withContext(Dispatchers.IO) {
        userDao.update(user)
        firestoreManager.syncUser(user)

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
            VolleyHelper.requestObject(context, Request.Method.POST, "sync/users", payload)
            userDao.updateSyncStatus(user.id, true)
            Log.d("SyncDebug", "✅ User ${user.username} synced via Volley")
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Volley error syncing user: ${e.message}")
        }
    }

    suspend fun findByUsername(username: String): UserEntity? = withContext(Dispatchers.IO) {
        return@withContext userDao.findByUsername(username)
    }
}
