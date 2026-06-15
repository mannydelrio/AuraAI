/*
 * Aura — OpenAI-compatible provider.
 *
 * Works for OpenAI itself and for any local/self-hosted LLM that speaks the
 * OpenAI Chat Completions API: Ollama (http://host:11434/v1), LM Studio
 * (http://host:1234/v1), llama.cpp server, vLLM, LiteLLM, etc.
 */
package com.aura.assistant.ai

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenAiCompatibleProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient,
    override val name: String = "OpenAI-compatible",
) : AiProvider {

  override suspend fun complete(messages: List<ChatMessage>): String =
      withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/chat/completions"

        val payload =
            JSONObject().apply {
              put("model", model)
              put("stream", false)
              put(
                  "messages",
                  JSONArray().apply {
                    messages.forEach { m ->
                      put(JSONObject().put("role", m.role).put("content", m.content))
                    }
                  },
              )
            }

        val requestBuilder =
            Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON))
        // Local servers often need no key; only send the header when one is configured.
        if (apiKey.isNotBlank()) requestBuilder.header("Authorization", "Bearer $apiKey")

        client.newCall(requestBuilder.build()).execute().use { resp ->
          val body = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) {
            throw IOException("HTTP ${resp.code} from $url: ${body.take(500)}")
          }
          val json = JSONObject(body)
          json.getJSONArray("choices")
              .getJSONObject(0)
              .getJSONObject("message")
              .getString("content")
              .trim()
        }
      }

  private companion object {
    val JSON = "application/json; charset=utf-8".toMediaType()
  }
}
