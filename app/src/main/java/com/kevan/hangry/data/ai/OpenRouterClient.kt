package com.kevan.hangry.data.ai

import com.kevan.hangry.util.resultOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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

/** One server-sent event from a streamed completion. */
@Serializable
private data class StreamChunk(
    val choices: List<Choice> = emptyList(),
    val error: StreamError? = null
) {
    @Serializable
    data class Choice(val delta: Delta? = null)

    @Serializable
    data class Delta(val content: String? = null)

    @Serializable
    data class StreamError(val code: Int? = null, val message: String? = null)
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
    val content: String,
    /** Raw base64 JPEGs attached to this turn (user messages only). */
    val imagesBase64: List<String> = emptyList()
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
        // Built off the main thread: the body carries the base64 photos.
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
        executeRequest(apiKey, messagesBody(model, messages, stream = false).toString())
    }

    /**
     * Same as [chatCompletionMessages] but streamed: [onDelta] receives each text fragment as
     * the model writes it (on the IO thread). Returns the full text once the stream ends.
     * Cancelling the caller cancels the HTTP call and stops [onDelta] immediately.
     */
    suspend fun streamChatCompletionMessages(
        apiKey: String,
        model: String,
        messages: List<OpenRouterMessage>,
        onDelta: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) { streamMessages(apiKey, model, messages, onDelta) }

    private suspend fun streamMessages(
        apiKey: String,
        model: String,
        messages: List<OpenRouterMessage>,
        onDelta: (String) -> Unit
    ): Result<String> = resultOf {
        execute(buildRequest(apiKey, messagesBody(model, messages, stream = true).toString())) { response ->
            throwForStatus(response.code, response.isSuccessful)
            val source = response.body?.source() ?: throw OpenRouterException.EmptyResponse()
            val full = StringBuilder()
            try {
                while (true) {
                    // Leaving the chat cancels the call; stop before handing out another fragment.
                    ensureActive()
                    val line = source.readUtf8Line() ?: break
                    // SSE: "data: {...}" chunks; ":" lines are keep-alive comments.
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    val chunk = runCatching { json.decodeFromString(StreamChunk.serializer(), payload) }.getOrNull() ?: continue
                    if (chunk.error != null) throw OpenRouterException.ServerError(chunk.error.code ?: 500)
                    val delta = chunk.choices.firstOrNull()?.delta?.content ?: continue
                    if (delta.isNotEmpty()) {
                        full.append(delta)
                        onDelta(delta)
                    }
                }
            } catch (e: IOException) {
                ensureActive()
                throw OpenRouterException.NoNetwork(e)
            }
            full.toString().takeIf { text -> text.isNotBlank() } ?: throw OpenRouterException.EmptyResponse()
        }
    }

    private fun messagesBody(model: String, messages: List<OpenRouterMessage>, stream: Boolean) = buildJsonObject {
        put("model", model)
        if (stream) put("stream", true)
        putJsonArray("messages") {
            messages.forEach { msg ->
                addJsonObject {
                    put("role", msg.role)
                    if (msg.imagesBase64.isEmpty()) {
                        put("content", msg.content)
                    } else {
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", msg.content)
                            }
                            msg.imagesBase64.forEach { base64 ->
                                addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$base64") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildRequest(apiKey: String, requestBodyJson: String): Request = Request.Builder()
        .url("https://openrouter.ai/api/v1/chat/completions")
        .header("Authorization", "Bearer $apiKey")
        .header("Content-Type", "application/json")
        .header("HTTP-Referer", "https://github.com/hangry-app")
        .header("X-Title", "Hangry")
        .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
        .build()

    private fun throwForStatus(code: Int, successful: Boolean) {
        when {
            code == 401 || code == 403 -> throw OpenRouterException.InvalidApiKey()
            code == 429 -> throw OpenRouterException.RateLimited()
            !successful -> throw OpenRouterException.ServerError(code)
        }
    }

    private suspend fun executeRequest(apiKey: String, requestBodyJson: String): Result<String> = resultOf {
        execute(buildRequest(apiKey, requestBodyJson)) { response ->
            val bodyText = try {
                response.body?.string().orEmpty()
            } catch (e: IOException) {
                ensureActive()
                throw OpenRouterException.NoNetwork(e)
            }
            throwForStatus(response.code, response.isSuccessful)
            val parsed = try {
                json.decodeFromString(ChatCompletionResponse.serializer(), bodyText)
            } catch (e: Exception) {
                throw OpenRouterException.MalformedResponse(e)
            }
            parsed.choices.firstOrNull()?.message?.content?.takeIf { text -> text.isNotBlank() }
                ?: throw OpenRouterException.EmptyResponse()
        }
    }

    /**
     * Runs [request] on the IO dispatcher and hands the open response to [handle]. Cancelling the
     * calling coroutine (the user left the screen) cancels the HTTP call itself, which unblocks
     * any read in progress, so nothing keeps downloading or streaming for up to the read timeout.
     */
    private suspend fun <T> execute(request: Request, handle: CoroutineScope.(Response) -> T): T = withContext(Dispatchers.IO) {
        val call = httpClient.newCall(request)
        val canceller = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            val response = try {
                call.execute()
            } catch (e: IOException) {
                ensureActive()
                throw OpenRouterException.NoNetwork(e)
            }
            response.use { handle(it) }
        } finally {
            canceller.cancel()
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
