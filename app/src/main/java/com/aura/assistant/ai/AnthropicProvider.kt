/*
 * Aura — Anthropic (Claude) provider.
 *
 * Uses the Messages API. Note Anthropic takes the system prompt as a top-level
 * "system" field, not as a message with role "system" — so we split it out here.
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

class AnthropicProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient,
    private val maxTokens: Int = 1024,
) : AiProvider {

  override val name: String = "Anthropic (Claude)"

  override suspend fun complete(messages: List<ChatMessage>): String =
      withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/v1/messages"

        // Collapse any system turns into the top-level system field.
        val systemPrompt =
            messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }
        val turns = messages.filter { it.role == "user" || it.role == "assistant" }

        val payload =
            JSONObject().apply {
              put("model", model)
              put("max_tokens", maxTokens)
              if (systemPrompt.isNotBlank()) put("system", systemPrompt)
              put(
                  "messages",
                  JSONArray().apply {
                    turns.forEach { m ->
                      put(JSONObject().put("role", m.role).put("content", m.content))
                    }
                  },
              )
            }

        val request =
            Request.Builder()
                .url(url)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(payload.toString().toRequestBody(JSON))
                .build()

        client.newCall(request).execute().use { resp ->
          val body = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) {
            throw IOException("HTTP ${resp.code} from $url: ${body.take(500)}")
          }
          // content is an array of blocks; concatenate the text blocks.
          val json = JSONObject(body)
          val content = json.getJSONArray("content")
          buildString {
                for (i in 0 until content.length()) {
                  val block = content.getJSONObject(i)
                  if (block.optString("type") == "text") append(block.getString("text"))
                }
              }
              .trim()
        }
      }

  private companion object {
    val JSON = "application/json; charset=utf-8".toMediaType()
  }
}
