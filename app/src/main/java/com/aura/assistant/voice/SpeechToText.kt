/*
 * Aura — speech-to-text via an OpenAI-compatible /audio/transcriptions endpoint.
 *
 * Works with OpenAI and local servers that implement the same API (LocalAI, etc.).
 */
package com.aura.assistant.voice

import android.util.Log
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SpeechToText(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String = "whisper-1",
    private val client: OkHttpClient,
) {
  suspend fun transcribe(wav: ByteArray): String =
      withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/audio/transcriptions"
        val body =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addFormDataPart("file", "audio.wav", wav.toRequestBody("audio/wav".toMediaType()))
                .build()
        val request =
            Request.Builder().url(url).header("Authorization", "Bearer $apiKey").post(body).build()
        client.newCall(request).execute().use { resp ->
          val text = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) throw IOException("STT HTTP ${resp.code}: ${text.take(300)}")
          val parsed = JSONObject(text).optString("text").trim()
          if (parsed.isEmpty()) Log.w("SpeechToText", "empty transcription; model=$model raw=${text.take(300)}")
          parsed
        }
      }
}
