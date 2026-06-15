/*
 * Aura — basic keyless web lookup via DuckDuckGo's Instant Answer API.
 *
 * Good for definitions, facts, and well-known entities. It's limited (no full web crawl);
 * a key-based search API (Brave/Tavily) can be swapped in later for richer results.
 */
package com.aura.assistant.tools

import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WebSearchTool(private val client: OkHttpClient) : Tool {
  override val name = "web_search"
  override val description =
      "Look up current facts, definitions, people, places, or things that may be outside your training data."

  override fun parameters(): JSONObject =
      JSONObject(
          """{"type":"object","properties":{"query":{"type":"string","description":"The search query"}},"required":["query"]}""")

  override suspend fun execute(args: JSONObject): String =
      withContext(Dispatchers.IO) {
        val query = args.optString("query").trim()
        if (query.isEmpty()) return@withContext "What should I search for?"

        val json =
            JSONObject(
                httpGet(
                    "https://api.duckduckgo.com/?q=${URLEncoder.encode(query, "UTF-8")}&format=json&no_html=1&skip_disambig=1"))

        val abstract = json.optString("AbstractText")
        if (abstract.isNotBlank()) {
          val src = json.optString("AbstractSource")
          return@withContext if (src.isNotBlank()) "$abstract (source: $src)" else abstract
        }
        val answer = json.optString("Answer")
        if (answer.isNotBlank()) return@withContext answer

        val related = json.optJSONArray("RelatedTopics")
        if (related != null) {
          val sb = StringBuilder()
          var n = 0
          var i = 0
          while (i < related.length() && n < 3) {
            val text = related.optJSONObject(i)?.optString("Text").orEmpty()
            if (text.isNotBlank()) {
              sb.append("• ").append(text).append('\n')
              n++
            }
            i++
          }
          if (sb.isNotEmpty()) return@withContext sb.toString().trim()
        }
        "I couldn't find a quick answer for \"$query\"."
      }

  private fun httpGet(url: String): String {
    client.newCall(Request.Builder().url(url).header("User-Agent", "Aura/1.0").build()).execute().use {
      if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
      return it.body!!.string()
    }
  }
}
