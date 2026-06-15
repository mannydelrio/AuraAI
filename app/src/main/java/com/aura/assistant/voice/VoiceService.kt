/*
 * Aura — always-listening microphone service.
 *
 * Captures mic audio, uses a simple energy VAD to find spoken utterances, transcribes
 * each via cloud STT, applies the activation rule (wake-word prefix vs continuous), and
 * hands the command to AssistantSession (which calls the AI provider and speaks the reply).
 *
 * Capture pauses whenever the assistant is transcribing / thinking / speaking so it never
 * hears itself. True low-power on-device wake-word (Porcupine/Vosk) is a future upgrade;
 * this v1 is VAD-gated so audio only leaves the device when someone actually speaks.
 */
package com.aura.assistant.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aura.assistant.AssistantSession
import com.aura.assistant.R
import com.aura.assistant.data.ActivationMode
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  @Volatile private var running = false
  private var captureThread: Thread? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(NOTIFICATION_ID, buildNotification())
    if (!running) start()
    return START_STICKY
  }

  private fun start() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "RECORD_AUDIO not granted; stopping.")
      AssistantSession.listeningState = ListeningState.ERROR
      stopSelf()
      return
    }
    running = true
    captureThread = Thread { captureLoop() }.apply { start() }
  }

  private fun captureLoop() {
    val minBuf =
        AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val bufferSize = maxOf(minBuf, FRAME_SAMPLES * 2 * 4)
    val record =
        AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize)

    if (record.state != AudioRecord.STATE_INITIALIZED) {
      Log.e(TAG, "AudioRecord failed to initialize")
      AssistantSession.listeningState = ListeningState.ERROR
      record.release()
      return
    }

    val frame = ShortArray(FRAME_SAMPLES)
    val utterance = ByteArrayOutputStream()
    var collecting = false
    var silenceFrames = 0
    var peak = 0.0
    var lastLog = System.currentTimeMillis()

    record.startRecording()
    AssistantSession.listeningState = ListeningState.IDLE
    Log.d(TAG, "capture started: source=VOICE_RECOGNITION rate=$SAMPLE_RATE minBuf=$minBuf")

    try {
      while (running) {
        val n = record.read(frame, 0, frame.size)
        if (n <= 0) continue

        // While the assistant is busy (transcribing/thinking/speaking), drop audio so we
        // don't capture our own TTS or stack commands.
        if (isAssistantBusy()) {
          collecting = false
          silenceFrames = 0
          utterance.reset()
          continue
        }

        val level = rms(frame, n)
        if (level > peak) peak = level
        val now = System.currentTimeMillis()
        if (now - lastLog >= 1000) {
          Log.d(TAG, "mic peak RMS=${peak.toInt()} (threshold=$START_THRESHOLD)")
          peak = 0.0
          lastLog = now
        }
        if (level > START_THRESHOLD) {
          if (!collecting) {
            collecting = true
            utterance.reset()
            AssistantSession.listeningState = ListeningState.LISTENING
          }
          appendLE(utterance, frame, n)
          silenceFrames = 0
          // Cap runaway captures (continuous noise) so we don't send 10s+ of audio.
          if (utterance.size() >= MAX_UTTERANCE_BYTES) {
            collecting = false
            val pcm = utterance.toByteArray()
            utterance.reset()
            processUtterance(pcm)
          }
        } else if (collecting) {
          appendLE(utterance, frame, n) // keep a little trailing audio
          silenceFrames++
          if (silenceFrames >= SILENCE_FRAMES) {
            collecting = false
            val pcm = utterance.toByteArray()
            utterance.reset()
            if (pcm.size >= MIN_UTTERANCE_BYTES) {
              processUtterance(pcm)
            } else {
              AssistantSession.listeningState = ListeningState.IDLE
            }
          }
        } else if (AssistantSession.listeningState == ListeningState.LISTENING) {
          AssistantSession.listeningState = ListeningState.IDLE
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "capture loop error", e)
    } finally {
      runCatching { record.stop() }
      record.release()
    }
  }

  private fun processUtterance(pcm: ByteArray) {
    AssistantSession.listeningState = ListeningState.TRANSCRIBING
    Log.d(TAG, "utterance captured: ${pcm.size} bytes -> transcribing")
    scope.launch {
      try {
        val stt = AssistantSession.stt
        if (stt == null) {
          AssistantSession.listeningState = ListeningState.IDLE
          return@launch
        }
        val wav = WavUtil.pcm16ToWav(pcm, SAMPLE_RATE)
        runCatching {
          val f = java.io.File(getExternalFilesDir(null), "last_utterance.wav")
          f.writeBytes(wav)
          Log.d(TAG, "saved ${f.absolutePath} (${wav.size} bytes)")
        }
        val text = stt.transcribe(wav)
        val command = applyActivation(text)
        Log.d(TAG, "STT='$text' command='${command ?: "<ignored>"}'")
        withContext(Dispatchers.Main) {
          if (command.isNullOrBlank()) {
            AssistantSession.listeningState = ListeningState.IDLE
          } else {
            AssistantSession.sendUserText(command, speak = true)
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "transcription error", e)
        AssistantSession.listeningState = ListeningState.IDLE
      }
    }
  }

  /** Returns the command to act on, or null to ignore this utterance. */
  private fun applyActivation(transcript: String): String? {
    val text = transcript.trim()
    if (text.isEmpty()) return null
    return when (AssistantSession.activationMode()) {
      ActivationMode.CONTINUOUS,
      null -> text
      ActivationMode.WAKE_WORD ->
          matchWakeWord(text, AssistantSession.wakePhrase().ifBlank { "Hey Aura" })
    }
  }

  /**
   * Forgiving wake-word match. STT routinely mishears "Hey Aura" as "Hey Yara", "Hey Ora",
   * etc., so we accept anything phonetically close rather than requiring an exact substring.
   */
  private fun matchWakeWord(transcript: String, phrase: String): String? {
    val words = tokenize(transcript)
    val phraseWords = tokenize(phrase)
    if (words.isEmpty() || phraseWords.isEmpty()) return null
    val k = phraseWords.size

    // 1) Fuzzy-match the first k words against the whole phrase ("hey yara" ~ "hey aura").
    if (words.size >= k) {
      val head = words.take(k).joinToString(" ")
      if (similarity(head, phraseWords.joinToString(" ")) >= 0.6) {
        return words.drop(k).joinToString(" ").trim().ifBlank { null }
      }
    }
    // 2) Fallback: the salient last word of the phrase (e.g. "aura") appears near the start.
    val key = phraseWords.last()
    val scan = minOf(words.size, k + 1)
    for (i in 0 until scan) {
      if (similarity(words[i], key) >= 0.6) {
        return words.drop(i + 1).joinToString(" ").trim().ifBlank { null }
      }
    }
    return null
  }

  private fun tokenize(s: String): List<String> =
      s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }

  private fun similarity(a: String, b: String): Double {
    val maxLen = maxOf(a.length, b.length)
    return if (maxLen == 0) 1.0 else 1.0 - levenshtein(a, b).toDouble() / maxLen
  }

  private fun levenshtein(a: String, b: String): Int {
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
      var prev = dp[0]
      dp[0] = i
      for (j in 1..b.length) {
        val tmp = dp[j]
        dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
        prev = tmp
      }
    }
    return dp[b.length]
  }

  private fun isAssistantBusy(): Boolean =
      AssistantSession.listeningState == ListeningState.TRANSCRIBING ||
          AssistantSession.listeningState == ListeningState.THINKING ||
          AssistantSession.listeningState == ListeningState.SPEAKING

  private fun rms(buf: ShortArray, n: Int): Double {
    var sum = 0.0
    for (i in 0 until n) {
      val s = buf[i].toDouble()
      sum += s * s
    }
    return Math.sqrt(sum / n)
  }

  private fun appendLE(out: ByteArrayOutputStream, buf: ShortArray, n: Int) {
    for (i in 0 until n) {
      val v = buf[i].toInt()
      out.write(v and 0xFF)
      out.write((v shr 8) and 0xFF)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    running = false
    captureThread?.join(500)
    scope.cancel()
    AssistantSession.listeningState = ListeningState.OFF
  }

  private fun buildNotification(): Notification {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      mgr.createNotificationChannel(
          NotificationChannel(CHANNEL_ID, "Aura Listening", NotificationManager.IMPORTANCE_LOW))
    }
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.listen_notification_title))
        .setContentText(getString(R.string.listen_notification_text))
        .setSmallIcon(R.mipmap.ic_launcher)
        .setOngoing(true)
        .build()
  }

  private companion object {
    const val TAG = "VoiceService"
    const val CHANNEL_ID = "aura_listening"
    const val NOTIFICATION_ID = 1002

    const val SAMPLE_RATE = 16000
    const val FRAME_SAMPLES = 1600 // 100 ms
    const val START_THRESHOLD = 1000.0 // RMS over 16-bit samples; tune on device
    const val SILENCE_FRAMES = 8 // ~800 ms of silence ends an utterance
    const val MIN_UTTERANCE_BYTES = 9600 // ~0.3 s of 16 kHz 16-bit mono
    const val MAX_UTTERANCE_BYTES = 256000 // ~8 s; flush so STT never gets a runaway clip
  }
}
