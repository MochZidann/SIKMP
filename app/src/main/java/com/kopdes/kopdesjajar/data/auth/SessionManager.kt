package com.kopdes.kopdesjajar.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.kopdes.kopdesjajar.data.model.Role

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun setSession(userId: Long, role: Role, username: String, name: String) {
        prefs.edit()
            .putLong("userId", userId)
            .putString("role", role.name)
            .putString("username", username)
            .putString("name", name)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove("userId")
            .remove("role")
            .remove("username")
            .remove("name")
            .apply()
    }

    fun isLoggedIn(): Boolean = prefs.contains("userId") && !prefs.getString("role", null).isNullOrBlank()

    fun userId(): Long? {
        val id = prefs.getLong("userId", -1L)
        return if (id >= 0) id else null
    }

    fun role(): Role? {
        val value = prefs.getString("role", null) ?: return null
        return runCatching { Role.valueOf(value) }.getOrNull()
    }

    fun username(): String? = prefs.getString("username", null)
    fun name(): String? = prefs.getString("name", null)

    fun getTextSize(): String {
        val currentUserId = userId()
        val key = if (currentUserId != null) "textSize_$currentUserId" else "textSize_default"
        return prefs.getString(key, null) ?: prefs.getString("textSize_default", "standar") ?: "standar"
    }

    fun setTextSize(size: String) {
        val currentUserId = userId()
        if (currentUserId != null) {
            prefs.edit()
                .putString("textSize_$currentUserId", size)
                .putString("textSize_default", size)
                .apply()
        } else {
            prefs.edit().putString("textSize_default", size).apply()
        }
    }

    fun getTextSizeScale(): Float {
        return when (getTextSize()) {
            "besar" -> 1.25f
            "sangat_besar" -> 1.50f
            else -> 1.00f
        }
    }
}
