package com.kopdes.kopdesjajar.data.network

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object VolleyHelper {
    private val gson = Gson()
    // Ensure this matches your current Ngrok URL
    const val BASE_URL = "https://kopdes-jajar.up.railway.app/api/"

    // Headers required on every request:
    // - ngrok-skip-browser-warning: bypasses the ngrok free-tier HTML warning page
    // - Accept: application/json: ensures server returns JSON, not HTML
    private val COMMON_HEADERS = mapOf(
        "ngrok-skip-browser-warning" to "true",
        "Accept" to "application/json",
        "Content-Type" to "application/json; charset=utf-8"
    )

    suspend fun <T> requestList(
        context: Context,
        method: Int,
        endpoint: String,
        typeToken: TypeToken<List<T>>
    ): List<T> = suspendCancellableCoroutine { continuation ->
        val url = BASE_URL + endpoint
        val request = object : JsonArrayRequest(
            method, url, null,
            { response ->
                try {
                    val list: List<T> = gson.fromJson(response.toString(), typeToken.type)
                    continuation.resume(list)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            { error ->
                continuation.resumeWithException(Exception(error.message ?: "Volley Error"))
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> = COMMON_HEADERS.toMutableMap()
        }.apply {
            setShouldCache(false)
            retryPolicy = com.android.volley.DefaultRetryPolicy(
                30000,
                com.android.volley.DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        }
        VolleySingleton.getInstance(context).addToRequestQueue(request)
    }

    suspend fun requestObject(
        context: Context,
        method: Int,
        endpoint: String,
        body: Any? = null
    ): JSONObject? = suspendCancellableCoroutine { continuation ->
        val url = BASE_URL + endpoint
        val bodyString = if (body != null) gson.toJson(body) else null

        val request = object : JsonObjectRequest(
            method, url, null,
            { response -> continuation.resume(response) },
            { error -> continuation.resumeWithException(Exception(error.message ?: "Volley Error")) }
        ) {
            override fun getHeaders(): MutableMap<String, String> = COMMON_HEADERS.toMutableMap()
            override fun getBody(): ByteArray? = bodyString?.toByteArray(Charsets.UTF_8)
            override fun getBodyContentType(): String = "application/json; charset=utf-8"
        }.apply {
            setShouldCache(false)
            retryPolicy = com.android.volley.DefaultRetryPolicy(
                30000,
                0, // 0 retries to prevent duplicate uploads
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        }
        VolleySingleton.getInstance(context).addToRequestQueue(request)
    }

    suspend fun requestDelete(context: Context, endpoint: String): JSONObject = suspendCancellableCoroutine { continuation ->
        val url = BASE_URL + endpoint
        val request = object : JsonObjectRequest(Request.Method.DELETE, url, null,
            { response -> continuation.resume(response) },
            { error -> continuation.resumeWithException(Exception(error.message ?: "Volley Error")) }
        ) {
            override fun getHeaders(): MutableMap<String, String> = COMMON_HEADERS.toMutableMap()
        }.apply {
            setShouldCache(false)
            retryPolicy = com.android.volley.DefaultRetryPolicy(
                30000,
                com.android.volley.DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        }
        VolleySingleton.getInstance(context).addToRequestQueue(request)
    }
}
