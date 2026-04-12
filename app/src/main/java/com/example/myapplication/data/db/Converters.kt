package com.example.myapplication.data.db

import com.example.myapplication.data.model.Role

class Converters {
    fun roleToString(role: Role): String = role.name

    fun stringToRole(value: String): Role = Role.valueOf(value)
}
