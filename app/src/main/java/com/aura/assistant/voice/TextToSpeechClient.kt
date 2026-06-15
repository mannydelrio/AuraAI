/*
 * Aura — text-to-speech via an OpenAI-compatible /audio/speech endpoint.
 *
 * Fetches MP3 audio for the reply and plays it through MediaPlayer, suspending until
 * playback finishes so the caller can resume listening afterward.
 */
package com.aura.assistant.voice

import android.content.Context
import android.media.MediaPlayer
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class TextToSpeechClient(
    private val appContext: Context,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String = "tts-1",
    private val voice: String = "alloy",
    private val client: OkHttpClient,
) {
  suspend fun speak(text: String) {
    if (text.isBlank()) return
    val audio = fetchAudio(text)
    playToCompletion(audio)
  }

  private suspend fun fetchAudio(text: String): ByteArray =
      withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/audio/speech"
        val payload =
            JSONObject()
                .put("model", model)
                .put("input", text)
                .put("voice", voice)
                .put("response_format", "mp3")
        val request =
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
        client.newCall(request).execute().use { resp ->
          if (!resp.isSuccessful) {
            throw IOException("TTS HTTP ${resp.code}: ${resp.body?.string()?.take(300)}")
          }
          resp.body!!.bytes()
        }
      }

  private suspend fun playToCompletion(audio: ByteArray) {
    val file = File(appContext.cacheDir, "aura_tts.mp3")
    file.writeBytes(audio)
    suspendCancellableCoroutine<Unit> { cont ->
      val player = MediaPlayer()
      cont.invokeOnCancellation { runCatching { player.release() } }
      try {
        player.setDataSource(file.absolutePath)
        player.setOnCompletionListener {
          it.release()
          if (cont.isActive) cont.resume(Unit)
        }
        player.setOnErrorListener { mp, _, _ ->
          mp.release()
          if (cont.isActive) cont.resume(Unit)
          true
        }
        player.prepare()
        player.start()
      } catch (e: Exception) {
        runCatching { player.release() }
        if (cont.isActive) cont.resume(Unit)
      }
    }
  }
}
