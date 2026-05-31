package com.kopdes.kopdesjajar

import android.app.Application
import android.util.Log
import androidx.work.*
import com.kopdes.kopdesjajar.data.worker.SyncWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

class KoperasiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        
        // Login Anonim agar aturan Firestore 'auth != null' terpenuhi
        FirebaseAuth.getInstance().signInAnonymously()
            .addOnSuccessListener { Log.d("FirebaseSync", "✅ Auth Anonim Berhasil") }
            .addOnFailureListener { Log.e("FirebaseSync", "❌ Auth Gagal: ${it.message}") }

        setupSyncWorker()
    }

    private fun setupSyncWorker() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "FirebaseSync",
            ExistingPeriodicWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
