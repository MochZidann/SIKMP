package com.kopdes.kopdesjajar

import android.app.Application
import android.util.Log
import androidx.work.*
import coil.Coil
import coil.ImageLoader
import com.kopdes.kopdesjajar.data.worker.SyncWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class KoperasiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Configure Coil with a custom OkHttpClient that adds headers required
        // for ngrok free tier (bypasses the HTML warning page) and forces JSON
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("ngrok-skip-browser-warning", "true")
                    .addHeader("Accept", "image/*, */*")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()

        Coil.setImageLoader(imageLoader)

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
