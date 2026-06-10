package com.kopdes.kopdesjajar.data.pref

import android.content.Context
import android.content.SharedPreferences
import com.kopdes.kopdesjajar.data.model.Role

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "sikmp_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_LAST_SYNC_PRODUCT = "last_sync_product"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_TEXT_SIZE_PREFIX = "text_size_"
    }

    fun saveSession(userId: Long, role: Role) {
        prefs.edit().apply {
            putLong(KEY_USER_ID, userId)
            putString(KEY_USER_ROLE, role.name)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    fun getUserId(): Long = prefs.getLong(KEY_USER_ID, -1L)

    fun getUserRole(): Role? {
        val roleName = prefs.getString(KEY_USER_ROLE, null)
        return if (roleName != null) Role.valueOf(roleName) else null
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun clearSession() {
        prefs.edit().apply {
            remove(KEY_USER_ID)
            remove(KEY_USER_ROLE)
            putBoolean(KEY_IS_LOGGED_IN, false)
            apply()
        }
    }

    fun saveLastProductSync(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_PRODUCT, timestamp).apply()
    }

    fun getLastProductSync(): Long = prefs.getLong(KEY_LAST_SYNC_PRODUCT, 0L)

    /**
     * Menyimpan ukuran teks per Role.
     */
    fun saveTextSize(role: Role, size: Float) {
        prefs.edit().putFloat(KEY_TEXT_SIZE_PREFIX + role.name, size).apply()
    }

    /**
     * Mengambil ukuran teks per Role. Default 14f.
     */
    fun getTextSize(role: Role): Float {
        return prefs.getFloat(KEY_TEXT_SIZE_PREFIX + role.name, 14f)
    }
}
