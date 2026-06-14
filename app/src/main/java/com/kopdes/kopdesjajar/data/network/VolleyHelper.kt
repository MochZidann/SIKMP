package com.kopdes.kopdesjajar.data.network

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object VolleyHelper {
    private val gson = Gson()
    // Ensure this matches your current Ngrok URL
    const val BASE_URL = "https://species-hamlet-viewing.ngrok-free.dev/api/"

    suspend fun <T> requestList(
        context: Context,
        method: Int,
        endpoint: String,
        typeToken: TypeToken<List<T>>
    ): List<T> = suspendCancellableCoroutine { continuation ->
        val url = BASE_URL + endpoint
        val request = JsonArrayRequest(
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
        ).apply {
            setShouldCache(false) // Bypass Volley cache to always get fresh data
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
            override fun getBody(): ByteArray? = bodyString?.toByteArray(Charsets.UTF_8)
            override fun getBodyContentType(): String = "application/json; charset=utf-8"
        }.apply {
            setShouldCache(false)
        }
        VolleySingleton.getInstance(context).addToRequestQueue(request)
    }

    suspend fun requestDelete(context: Context, endpoint: String): JSONObject = suspendCancellableCoroutine { continuation ->
        val url = BASE_URL + endpoint
        val request = JsonObjectRequest(Request.Method.DELETE, url, null,
            { response -> continuation.resume(response) },
            { error -> continuation.resumeWithException(Exception(error.message ?: "Volley Error")) }
        ).apply {
            setShouldCache(false)
        }
        VolleySingleton.getInstance(context).addToRequestQueue(request)
    }
}
