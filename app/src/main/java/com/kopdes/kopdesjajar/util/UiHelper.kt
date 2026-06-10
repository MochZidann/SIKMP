package com.kopdes.kopdesjajar.util

import android.view.View
import com.kopdes.kopdesjajar.data.pref.PreferenceManager

object UiHelper {
    /**
     * Mengatur ukuran font secara rekursif untuk semua TextView di dalam sebuah View.
     * Catatan: Karena sekarang kita menggunakan system fontScale di BaseAuthedActivity
     * untuk menerapkan ukuran teks secara global, pemanggilan metode ini tidak lagi diperlukan
     * karena Android secara otomatis akan menskalakan ukuran teks (SP) berdasarkan konfigurasi.
     * Metode ini diubah menjadi no-op untuk mencegah double-scaling.
     */
    fun applyTextSize(view: View, pref: PreferenceManager) {
        // Handled globally via native Configuration.fontScale in BaseAuthedActivity
    }
}
