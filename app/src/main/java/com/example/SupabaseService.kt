package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SupabaseService {
    private const val TAG = "SupabaseService"
    const val SUPABASE_URL = "https://ltnlmgtoqecvctyeetma.supabase.co"
    const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx0bmxtZ3RvcWVjdmN0eWVldG1hIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQwNjM4MTAsImV4cCI6MjA4OTYzOTgxMH0.OB5An1HAkEiASsE_cV1KoFCWBcyYQGUPa6BKsM6LwaI"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Active session details
    var sessionToken: String? = null
    var userId: String? = null
    var userEmail: String? = null

    val isLoggedIn: Boolean
        get() = sessionToken != null && userId != null

    /**
     * Authenticates user with email and password.
     * Maps admin -> admin@yanye.com
     */
    suspend fun login(emailInput: String, passwordInput: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var email = emailInput.trim()
            if (email.lowercase() == "admin") {
                email = "admin@yanye.com"
            } else if (!email.contains("@")) {
                email = "$email@yanye.local"
            }

            val url = "$SUPABASE_URL/auth/v1/token?grant_type=password"
            val bodyJson = JSONObject().apply {
                put("email", email)
                put("password", passwordInput)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (!response.isSuccessful) {
                    val errMsg = parseErrorMessage(bodyStr) ?: "登录获批失败 (${response.code})"
                    return@withContext Result.failure(Exception(errMsg))
                }

                if (bodyStr.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("空认证响应"))
                }

                val jsonObj = JSONObject(bodyStr)
                sessionToken = jsonObj.getString("access_token")
                val userObj = jsonObj.getJSONObject("user")
                userId = userObj.getString("id")
                userEmail = userObj.optString("email", email)

                return@withContext Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login exception", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Clears local cache session tokens
     */
    fun logout() {
        sessionToken = null
        userId = null
        userEmail = null
    }

    /**
     * Fetches all dictation items saved for the logged user
     */
    suspend fun fetchDictationItems(): Result<List<DictationItem>> = withContext(Dispatchers.IO) {
        try {
            val token = sessionToken ?: SUPABASE_KEY
            // We use simple select endpoint
            val url = "$SUPABASE_URL/rest/v1/dictation_items?select=*"
            val request = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("读取词条失败: ${response.code}"))
                }

                val list = mutableListOf<DictationItem>()
                if (!bodyStr.isNullOrEmpty()) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(DictationItem.fromJSON(obj))
                    }
                }
                // Filter by user id if available
                val currentUid = userId
                val filtered = if (currentUid != null) {
                    list.filter { it.userId == currentUid || it.userId.isNullOrEmpty() }
                } else {
                    list
                }

                return@withContext Result.success(filtered)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch exception", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Saves list of items into dictation_items database table
     */
    suspend fun insertDictationItems(items: List<DictationItem>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionToken ?: return@withContext Result.failure(Exception("用户未登录"))
            val url = "$SUPABASE_URL/rest/v1/dictation_items"
            
            val arr = JSONArray()
            for (item in items) {
                val dbObj = JSONObject().apply {
                    put("q", item.question)
                    put("a", item.answer)
                    put("cat", item.category)
                    put("group_name", item.groupName)
                    put("user_id", userId)
                }
                arr.put(dbObj)
            }

            val request = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(arr.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("保存数据失败: ${response.code}"))
                }
                return@withContext Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Insert exception", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Clears specific group name's items for active user
     */
    suspend fun deleteDictationGroup(groupName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionToken ?: return@withContext Result.failure(Exception("用户未登录"))
            val url = "$SUPABASE_URL/rest/v1/dictation_items?user_id=eq.$userId&group_name=eq.${groupName}"
            
            val request = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("删除词库失败: ${response.code}"))
                }
                return@withContext Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete exception", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Fetches all shared resources from the Shared Resource Center
     */
    suspend fun fetchSharedResources(): Result<List<SharedResource>> = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_URL/rest/v1/resource_center?select=*&order=created_at.desc"
            val request = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("读取共享资源失败: ${response.code}"))
                }

                val list = mutableListOf<SharedResource>()
                if (!bodyStr.isNullOrEmpty()) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(SharedResource.fromJSON(obj))
                    }
                }
                return@withContext Result.success(list)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch resources exception", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Share/Upload a custom deck to Shared Resource Center
     */
    suspend fun publishSharedResource(title: String, desc: String, jsonData: JSONArray, coverUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionToken ?: return@withContext Result.failure(Exception("用户未登录"))
            val url = "$SUPABASE_URL/rest/v1/resource_center"
            
            val bodyObj = JSONObject().apply {
                put("title", title)
                put("description", desc)
                put("cover_url", coverUrl)
                put("json_data", jsonData)
                put("uploader_id", userId)
                put("uploader_email", userEmail)
            }

            val request = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("发布分享失败: ${response.code}"))
                }
                return@withContext Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Publish custom deck exception", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Removes shared resource from the catalog
     */
    suspend fun deleteSharedResource(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionToken ?: return@withContext Result.failure(Exception("用户未登录"))
            val url = "$SUPABASE_URL/rest/v1/resource_center?id=eq.$id"
            
            val request = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("删除共享失败: ${response.code}"))
                }
                return@withContext Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete shared exception", e)
            return@withContext Result.failure(e)
        }
    }

    private fun parseErrorMessage(jsonStr: String?): String? {
        if (jsonStr.isNullOrEmpty()) return null
        return try {
            val obj = JSONObject(jsonStr)
            obj.optString("error_description", obj.optString("message", null))
        } catch (e: Exception) {
            null
        }
    }
}

data class DictationItem(
    val id: String?,
    val question: String,
    val answer: String,
    val category: String,
    val groupName: String,
    val userId: String?
) {
    companion object {
        fun fromJSON(obj: JSONObject): DictationItem {
            return DictationItem(
                id = obj.optString("id", null),
                question = obj.optString("q", ""),
                answer = obj.optString("a", ""),
                category = obj.optString("cat", "综合"),
                groupName = obj.optString("group_name", "默认分组"),
                userId = obj.optString("user_id", null)
            )
        }
    }
}

data class SharedResource(
    val id: String,
    val title: String,
    val description: String,
    val coverUrl: String,
    val jsonDataText: String, // Keep data array as string or structure
    val uploaderId: String,
    val uploaderEmail: String,
    val rawJsonData: JSONArray
) {
    companion object {
        fun fromJSON(obj: JSONObject): SharedResource {
            val rawArr = obj.optJSONArray("json_data") ?: JSONArray()
            return SharedResource(
                id = obj.optString("id", ""),
                title = obj.optString("title", ""),
                description = obj.optString("description", "暂无简介"),
                coverUrl = obj.optString("cover_url", ""),
                jsonDataText = rawArr.toString(),
                uploaderId = obj.optString("uploader_id", ""),
                uploaderEmail = obj.optString("uploader_email", ""),
                rawJsonData = rawArr
            )
        }
    }
}
