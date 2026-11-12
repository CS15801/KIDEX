package com.sitecouncil.data

import com.sitecouncil.domain.AnalysisError
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ApiReply(
    val text: String,
    val inputTokens: Int,
    val outputTokens: Int,
)

/** Direkter Aufruf der Anthropic Messages API, ohne eigenes Backend. */
@Singleton
class AnthropicClient @Inject constructor(
    private val http: OkHttpClient,
    private val settings: SettingsStore,
) {
    /**
     * @param images JPEG-Bilder als Base64, in der Reihenfolge von oben nach unten.
     */
    suspend fun message(
        system: String,
        user: String,
        images: List<String>,
        maxTokens: Int,
    ): ApiReply {
        val apiKey = settings.apiKey
        if (apiKey.isBlank()) throw AnalysisError.NoApiKey()

        val payload = buildJsonObject {
            put("model", settings.model)
            put("max_tokens", maxTokens)
            put("system", system)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        for (image in images) {
                            addJsonObject {
                                put("type", "image")
                                putJsonObject("source") {
                                    put("type", "base64")
                                    put("media_type", "image/jpeg")
                                    put("data", image)
                                }
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", user)
                        }
                    }
                }
            }
        }.toString()

        var attempt = 0
        while (true) {
            val request = Request.Builder()
                .url(ENDPOINT)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()

            val response = try {
                http.newCall(request).await()
            } catch (e: IOException) {
                if (attempt < MAX_RETRIES) {
                    attempt++
                    delay(2000L * attempt)
                    continue
                }
                throw AnalysisError.Network(e.message ?: e.javaClass.simpleName)
            }

            val code = response.code
            val retryAfter = response.header("retry-after")?.toLongOrNull()
            val body = response.use {
                try {
                    it.body?.string().orEmpty()
                } catch (e: IOException) {
                    ""
                }
            }

            if (code in 200..299) return parseReply(body)

            val retryable = code == 429 || code >= 500
            if (retryable && attempt < MAX_RETRIES) {
                attempt++
                val waitSeconds = (retryAfter ?: (3L * attempt)).coerceIn(1L, 20L)
                delay(waitSeconds * 1000L)
                continue
            }

            throw when (code) {
                401, 403 -> AnalysisError.Unauthorized()
                429 -> AnalysisError.RateLimit()
                else -> AnalysisError.Api("HTTP $code: ${errorMessage(body)}")
            }
        }
    }

    private fun parseReply(body: String): ApiReply {
        val root: JsonObject = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw AnalysisError.Api("Antwort der API ist kein JSON")
        }
        val text = buildString {
            val content = root["content"] as? JsonArray ?: return@buildString
            for (block in content) {
                val obj = block as? JsonObject ?: continue
                if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                    append(obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                }
            }
        }
        val usage = root["usage"] as? JsonObject
        return ApiReply(
            text = text,
            inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0,
        )
    }

    private fun errorMessage(body: String): String = try {
        val error = Json.parseToJsonElement(body).jsonObject["error"] as? JsonObject
        error?.get("message")?.jsonPrimitive?.contentOrNull ?: body.take(200)
    } catch (e: Exception) {
        body.take(200)
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private const val MAX_RETRIES = 2
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
