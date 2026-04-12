package com.example.myapplication.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.myapplication.data.model.Role

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
        prefs.edit().clear().apply()
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
}
