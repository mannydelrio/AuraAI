/*
 * Aura — agentic loop over an OpenAI-compatible chat/completions endpoint.
 *
 * Sends the conversation plus the tool definitions; if the model asks to call tools, runs
 * them, feeds the results back, and repeats until the model produces a final answer.
 */
package com.aura.assistant.ai

import android.util.Log
import com.aura.assistant.tools.ToolRegistry
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenAiAgent(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient,
    private val registry: ToolRegistry,
) {

  suspend fun run(messages: List<ChatMessage>, maxRounds: Int = 6): String =
      withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val msgs = JSONArray()
        messages.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.content)) }

        repeat(maxRounds) {
          val body = JSONObject().put("model", model).put("messages", msgs)
          if (!registry.isEmpty()) {
            body.put("tools", registry.openAiTools())
            body.put("tool_choice", "auto")
          }

          val request =
              Request.Builder()
                  .url(url)
                  .header("Authorization", "Bearer $apiKey")
                  .post(body.toString().toRequestBody(JSON))
                  .build()

          val responseText =
              client.newCall(request).execute().use { resp ->
                val t = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${t.take(400)}")
                t
              }

          val message =
              JSONObject(responseText).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
          val toolCalls = message.optJSONArray("tool_calls")

          if (toolCalls == null || toolCalls.length() == 0) {
            return@withContext message.optString("content").trim()
          }

          // Record the assistant's tool-call message verbatim, then answer each call.
          msgs.put(message)
          for (i in 0 until toolCalls.length()) {
            val call = toolCalls.getJSONObject(i)
            val fn = call.getJSONObject("function")
            val toolName = fn.getString("name")
            val args =
                runCatching { JSONObject(fn.optString("arguments", "{}")) }.getOrDefault(JSONObject())
            Log.d("OpenAiAgent", "tool call: $toolName $args")
            val result = registry.execute(toolName, args)
            msgs.put(
                JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", call.getString("id"))
                    .put("content", result))
          }
        }
        "Sorry — I wasn't able to finish that request."
      }

  private companion object {
    val JSON = "application/json; charset=utf-8".toMediaType()
  }
}
