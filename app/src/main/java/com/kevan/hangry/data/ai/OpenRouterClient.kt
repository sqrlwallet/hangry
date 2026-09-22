package com.kevan.hangry.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What went wrong calling OpenRouter, so the UI can show something more useful than "failed". */
sealed class OpenRouterException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidApiKey : OpenRouterException("This OpenRouter API key was rejected. Check it in Settings.")
    class RateLimited : OpenRouterException("OpenRouter rate limit or credit limit reached. Try again shortly.")
    class NoNetwork(cause: Throwable) : OpenRouterException("Couldn't reach OpenRouter - check your connection.", cause)
    class ServerError(code: Int) : OpenRouterException("OpenRouter returned an error (HTTP $code). Try again.")
    class EmptyResponse : OpenRouterException("The model returned an empty response. Try again.")
    class MalformedResponse(cause: Throwable) : OpenRouterException("Couldn't read the model's response.", cause)
}

@Serializable
private data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList()
) {
    @Serializable
    data class Choice(val message: Message? = null)

    @Serializable
    data class Message(val content: String? = null)
}

data class OpenRouterMessage(
    val role: String,
    val content: String
)

/**
 * Thin client for OpenRouter's OpenAI-compatible chat completions endpoint - the only endpoint
 * this app calls. Every call is initiated by an explicit user action (tapping Analyze or sending a coach prompt);
 * nothing here runs automatically or in the background.
 */
class OpenRouterClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * @param imagesBase64 raw base64 (no data-URI prefix) JPEG bytes, one entry per image.
     * @return the model's raw text response (expected to be parsed as JSON by the caller).
     */
    suspend fun chatCompletion(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
        imagesBase64: List<String> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", userText)
                        }
                        imagesBase64.forEach { base64 ->
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:image/jpeg;base64,$base64")
                                }
                            }
                        }
                    }
                }
            }
        }
        executeRequest(apiKey, requestBody.toString())
    }

    /**
     * Multi-turn chat completion for conversational features (e.g. AI Coach).
     */
    suspend fun chatCompletionMessages(
        apiKey: String,
        model: String,
        messages: List<OpenRouterMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                messages.forEach { msg ->
                    addJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    }
                }
            }
        }
        executeRequest(apiKey, requestBody.toString())
    }

    private fun executeRequest(apiKey: String, requestBodyJson: String): Result<String> {
        return runCatching {
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://github.com/hangry-app")
                .header("X-Title", "Hangry")
                .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: IOException) {
                throw OpenRouterException.NoNetwork(e)
            }

            response.use {
                val bodyText = it.body?.string().orEmpty()
                when {
                    it.code == 401 || it.code == 403 -> throw OpenRouterException.InvalidApiKey()
                    it.code == 429 -> throw OpenRouterException.RateLimited()
                    !it.isSuccessful -> throw OpenRouterException.ServerError(it.code)
                    else -> {
                        val parsed = try {
                            json.decodeFromString(ChatCompletionResponse.serializer(), bodyText)
                        } catch (e: Exception) {
                            throw OpenRouterException.MalformedResponse(e)
                        }
                        parsed.choices.firstOrNull()?.message?.content?.takeIf { text -> text.isNotBlank() }
                            ?: throw OpenRouterException.EmptyResponse()
                    }
                }
            }
        }
    }
}

// Small DSL helpers kept local to this file - kotlinx.serialization's JsonObjectBuilder doesn't
// ship nested-array/object convenience functions out of the box.
private inline fun kotlinx.serialization.json.JsonObjectBuilder.putJsonArray(
    key: String,
    builderAction: kotlinx.serialization.json.JsonArrayBuilder.() -> Unit
) {
    put(key, buildJsonArray(builderAction))
}

private inline fun kotlinx.serialization.json.JsonObjectBuilder.putJsonObject(
    key: String,
    builderAction: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit
) {
    put(key, buildJsonObject(builderAction))
}

private inline fun kotlinx.serialization.json.JsonArrayBuilder.addJsonObject(
    builderAction: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit
) {
    add(buildJsonObject(builderAction))
}
