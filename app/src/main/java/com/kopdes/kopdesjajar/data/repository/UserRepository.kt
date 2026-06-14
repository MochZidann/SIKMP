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

    suspend fun saveUser(user: UserEntity, isNew: Boolean) = withContext(Dispatchers.IO) {
        if (isNew) {
            userDao.insert(user)
        } else {
            userDao.update(user)
        }
        
        // Push ke Firestore
        firestoreManager.syncUser(user)

        // Push ke Laravel
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
        } catch (e: Exception) {
            Log.e("SyncDebug", "💥 Gagal sync user ke Laravel: ${e.message}")
        }
    }

    suspend fun deleteUser(user: UserEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Hapus dari Firestore
            val firestoreOk = firestoreManager.deleteUser(user.username)
            
            // 2. Hapus dari Laravel
            try {
                VolleyHelper.requestDelete(context, "sync/users/${user.username}")
            } catch (e: Exception) {
                Log.e("SyncDebug", "Gagal hapus di Laravel: ${e.message}")
            }
            
            // 3. Hapus lokal
            userDao.delete(user)
            
            return@withContext firestoreOk
        } catch (e: Exception) {
            Log.e("SyncDebug", "Fatal error deleteUser: ${e.message}")
            return@withContext false
        }
    }

    suspend fun findByUsername(username: String): UserEntity? = withContext(Dispatchers.IO) {
        return@withContext userDao.findByUsername(username)
    }
}
