package com.example.myapplication.data.db

import android.content.Context
import com.example.myapplication.data.model.Role
import com.example.myapplication.data.security.PasswordHasher

object DatabaseSeeder {
    suspend fun ensureSeeded(context: Context) {
        val db = AppDatabase.get(context)
        val userDao = db.userDao()
        val settingsDao = db.settingsDao()

        if (settingsDao.get() == null) {
            settingsDao.upsert(SettingsEntity())
        }

        val defaults = listOf(
            Triple("Admin Sistem", "admin", Role.ADMIN_SISTEM),
            Triple("Kasir", "kasir", Role.KASIR),
            Triple("Admin Gudang", "gudang", Role.ADMIN_GUDANG),
            Triple("Owner", "owner", Role.OWNER_PENGAWAS)
        )

        for ((name, username, role) in defaults) {
            if (userDao.findByUsername(username) == null) {
                val salt = PasswordHasher.generateSalt()
                val hash = PasswordHasher.hash("123456", salt)
                userDao.insert(
                    UserEntity(
                        name = name,
                        username = username,
                        passwordHash = hash,
                        salt = salt,
                        role = role
                    )
                )
            }
        }
    }
}
