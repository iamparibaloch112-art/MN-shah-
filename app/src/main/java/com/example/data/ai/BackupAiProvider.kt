package com.example.data.ai

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

interface BackupAiProvider {
    fun isConfigured(): Boolean
    fun dailyQuotaLimit(): Int
    suspend fun generateJsonCompletion(systemPrompt: String, userPrompt: String): String
    suspend fun generateTextCompletion(systemPrompt: String, userPrompt: String): String
}

class OkHttpBackupAiProvider(
    private val apiKey: String = readOptionalBuildConfig("BACKUP_AI_API_KEY"),
    private val baseUrl: String = readOptionalBuildConfig("BACKUP_AI_BASE_URL")
        .ifBlank { DEFAULT_BACKUP_BASE_URL },
    private val modelName: String = readOptionalBuildConfig("BACKUP_AI_MODEL")
        .ifBlank { DEFAULT_BACKUP_MODEL },
    private val dailyLimit: Int = readOptionalBuildConfig("BACKUP_AI_DAILY_LIMIT")
        .toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_DAILY_LIMIT,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) : BackupAiProvider {

    override fun isConfigured(): Boolean {
        val key = apiKey.trim()
        if (key.isEmpty()) return false
        if (key.equals("YOUR_BACKUP_AI_API_KEY", ignoreCase = true)) return false
        if (key.startsWith("YOUR_", ignoreCase = true)) return false
        if (key.contains("PLACEHOLDER", ignoreCase = true)) return false
        if (key.length < 12) return false
        val url = baseUrl.trim()
        return url.startsWith("https://") && modelName.isNotBlank()
    }

    override fun dailyQuotaLimit(): Int = dailyLimit

    override suspend fun generateJsonCompletion(systemPrompt: String, userPrompt: String): String {
        return executeCompletion(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            expectJson = true
        )
    }

    override suspend fun generateTextCompletion(systemPrompt: String, userPrompt: String): String {
        return executeCompletion(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            expectJson = false
        )
    }

    private suspend fun executeCompletion(
        systemPrompt: String,
        userPrompt: String,
        expectJson: Boolean
    ): String = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            throw IllegalStateException("Backup AI provider is not configured with a valid API key.")
        }

        val payload = JSONObject().apply {
            put("model", modelName.trim())
            put("temperature", if (expectJson) 0.25 else 0.35)
            if (expectJson) {
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }

        val request = Request.Builder()
            .url(baseUrl.trim())
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val safeCode = response.code
                val msg = extractErrorMessage(rawBody)
                Log.w(TAG, "Backup AI HTTP $safeCode: $msg")
                if (safeCode == 429) {
                    throw IOException("HTTP 429 RESOURCE_EXHAUSTED on backup provider: $msg")
                }
                throw IOException("Backup AI HTTP $safeCode: $msg")
            }

            val root = JSONObject(rawBody)
            val choices = root.optJSONArray("choices")
            val content = choices?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()

            if (content.isBlank()) {
                throw IllegalStateException("Backup AI provider returned an empty response.")
            }
            content
        }
    }

    private fun extractErrorMessage(rawBody: String): String {
        if (rawBody.isBlank()) return "Empty error response"
        return runCatching {
            val json = JSONObject(rawBody)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: rawBody.take(180)
        }.getOrElse { rawBody.take(180) }
    }

    private companion object {
        const val TAG = "BackupAiProvider"
        const val DEFAULT_BACKUP_BASE_URL = "https://api.openai.com/v1/chat/completions"
        const val DEFAULT_BACKUP_MODEL = "gpt-4o-mini"
        const val DEFAULT_DAILY_LIMIT = 50
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun readOptionalBuildConfig(fieldName: String): String {
            return runCatching {
                BuildConfig::class.java.getField(fieldName).get(null)?.toString()?.trim().orEmpty()
            }.getOrDefault("")
        }
    }
}
