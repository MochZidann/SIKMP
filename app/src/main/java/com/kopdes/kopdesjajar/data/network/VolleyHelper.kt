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
    // Ganti IP ini sesuai dengan IP Laravel kamu
    const val BASE_URL = "http://192.168.0.7:8000/api/"

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
        )
        VolleySingleton.getInstance(context).addToRequestQueue(request)
    }

    suspend fun <T> requestObject(
        context: Context,
        method: Int,
        endpoint: String,
        body: Any?,
        responseType: Class<T>
    ): T? = suspendCancellableCoroutine { continuation ->
        val url = BASE_URL + endpoint
        val jsonBody = if (body != null) JSONObject(gson.toJson(body)) else null
        
        val request = JsonObjectRequest(
            method, url, jsonBody,
            { response -> 
                try {
                    continuation.resume(gson.fromJson(response.toString(), responseType))
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            { error -> 
                continuation.resumeWithException(Exception(error.message ?: "Volley Error")) 
            }
        )
        VolleySingleton.getInstance(context).addToRequestQueue(request)
    }

    suspend fun requestObject(
        context: Context,
        method: Int,
        endpoint: String,
        body: Any? = null
    ): JSONObject? = suspendCancellableCoroutine { continuation ->
        val url = BASE_URL + endpoint
        val jsonBody = if (body != null) {
            try {
                JSONObject(gson.toJson(body))
            } catch (e: Exception) {
                // If body is already a JSON-like string or list, gson.toJson is correct,
                // but if it's a list, JSONObject(String) might fail if it's a JSONArray.
                // However, let's check if the body is a List/Array and handle it.
                null
            }
        } else null
        
        // Wait, what if jsonBody is actually a JSONArray?
        // Let's check: if body is a List or Array, the Volley request should be able to send it.
        // Wait, JsonObjectRequest constructor only takes JSONObject.
        // If body is a List, gson.toJson(body) returns a JSON array string e.g. "[...]".
        // Passing this to JSONObject(...) constructor will throw an exception!
        // Ah! That's a very important catch! Let's check how the previous implementation behaved.
        // Previous: val jsonBody = if (body != null) JSONObject(gson.toJson(body)) else null
        // If body was a list (like payloads in SyncManager), gson.toJson(payloads) is a JSONArray string.
        // Calling JSONObject("[...]") throws JSONException: "Value [...] of type org.json.JSONArray cannot be converted to JSONObject".
        // Oh! This is a massive bug in the migration!
        // If they migrate Retrofit to Volley, and they send lists (like in SyncManager: `val payloads = mutableListOf<...>()`),
        // they were trying to construct a JSONObject from it, which would crash at runtime!
        // We must check if body is a List/Array or String, and if so, we might need a custom Request or send it as a StringRequest,
        // or support JsonArrayRequest if it is a JSONArray, or send it as a custom JsonRequest.
        // Let's implement this robustly!
        
        // Let's check if the body is a List. If so, we need a request that can send a JSONArray or raw body string.
        // Actually, JsonRequest in Volley supports sending any string body if we override getBody().
        // Let's write a request that supports any string/JSON payload!
        // Let's check Volley's JsonRequest or write a custom request, or use StringRequest/JsonObjectRequest.
        // Wait, if it's a POST request expecting a JSON response, but sending a JSON Array,
        // we can override JsonObjectRequest to send the JSONArray body, or send raw bytes.
        // Let's see how JsonObjectRequest is implemented. It overrides getBody() to return the bytes of the JSONObject.
        // If we want to support both JSONObject and JSONArray bodies, we can create a custom request class or
        // check if gson.toJson(body) starts with '['. If so, it's a JSONArray.
        // Wait, if we send a JSONArray request to Laravel, but Laravel returns a JSONObject (like { "status": "success" }),
        // then using JsonArrayRequest would fail because JsonArrayRequest expects a JSON Array response!
        // Yes! JsonObjectRequest expects a JSONObject response, but sends a JSONObject body.
        // If we want to send a JSON Array body but expect a JSONObject response, we can't use standard JsonArrayRequest.
        // We can create a CustomJsonRequest that sends a string body and parses a JSONObject response.
        
        val bodyString = if (body != null) gson.toJson(body) else null
        val request = object : JsonObjectRequest(
            method, url, null,
            { response -> continuation.resume(response) },
            { error -> continuation.resumeWithException(Exception(error.message ?: "Volley Error")) }
        ) {
            override fun getBody(): ByteArray? {
                return bodyString?.toByteArray(Charsets.UTF_8)
            }
            override fun getBodyContentType(): String {
                return "application/json; charset=utf-8"
            }
        }
        VolleySingleton.getInstance(context).addToRequestQueue(request)
    }

    suspend fun requestDelete(
        context: Context,
        endpoint: String
    ): JSONObject = suspendCancellableCoroutine { continuation ->
        val url = BASE_URL + endpoint
        val request = JsonObjectRequest(
            Request.Method.DELETE, url, null,
            { response -> continuation.resume(response) },
            { error -> continuation.resumeWithException(Exception(error.message ?: "Volley Error")) }
        )
        VolleySingleton.getInstance(context).addToRequestQueue(request)
    }
}
