/*
 * Aura — minimal Home Assistant REST client (states + service calls).
 */
package com.aura.assistant.ha

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class HomeAssistantClient(
    baseUrl: String,
    private val token: String,
    private val client: OkHttpClient,
) {
  private val base = baseUrl.trimEnd('/')

  suspend fun states(): JSONArray =
      withContext(Dispatchers.IO) {
        val req =
            Request.Builder().url("$base/api/states").header("Authorization", "Bearer $token").build()
        client.newCall(req).execute().use { resp ->
          val body = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) throw IOException("HA /states HTTP ${resp.code}: ${body.take(200)}")
          JSONArray(body)
        }
      }

  /** POST /api/services/{domain}/{service} with the given payload (must include entity_id). */
  suspend fun callService(domain: String, service: String, payload: JSONObject): String =
      withContext(Dispatchers.IO) {
        val req =
            Request.Builder()
                .url("$base/api/services/$domain/$service")
                .header("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(JSON))
                .build()
        client.newCall(req).execute().use { resp ->
          val body = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) throw IOException("HA service HTTP ${resp.code}: ${body.take(200)}")
          body
        }
      }

  private companion object {
    val JSON = "application/json; charset=utf-8".toMediaType()
  }
}
