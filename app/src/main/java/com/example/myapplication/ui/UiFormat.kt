package com.example.myapplication.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiFormat {
    private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID")).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }
    private val dateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("in", "ID"))
    private val dateOnly = SimpleDateFormat("dd/MM/yyyy", Locale("in", "ID"))

    fun money(value: Long): String = rupiah.format(value)
    fun dateTime(epochMs: Long): String = dateTime.format(Date(epochMs))
    fun dateOnly(epochMs: Long): String = dateOnly.format(Date(epochMs))
}

