package com.kopdes.kopdesjajar.data.db

import com.kopdes.kopdesjajar.data.model.Role

class Converters {
    fun roleToString(role: Role): String = role.name

    fun stringToRole(value: String): Role = Role.valueOf(value)
}
