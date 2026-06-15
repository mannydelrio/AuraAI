/*
 * Aura — the shared assistant brain.
 *
 * One conversation + provider, observed by Compose (overlay UI) and driven by both the
 * typed input and the voice service. Holding this as a process singleton means voice and
 * text share the same history and the same listening-state machine.
 */
package com.aura.assistant

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.aura.assistant.ai.AiProvider
import com.aura.assistant.ai.ChatMessage
import com.aura.assistant.ai.OpenAiAgent
import com.aura.assistant.ai.ProviderFactory
import com.aura.assistant.data.AppSettings
import com.aura.assistant.data.ProviderType
import com.aura.assistant.data.SettingsRepository
import com.aura.assistant.ha.HomeAssistantClient
import com.aura.assistant.tools.CancelAlarmTool
import com.aura.assistant.tools.HaCallServiceTool
import com.aura.assistant.tools.HaGetStatesTool
import com.aura.assistant.tools.ListAlarmsTool
import com.aura.assistant.tools.SetAlarmTool
import com.aura.assistant.tools.SetReminderTool
import com.aura.assistant.tools.SetTimerTool
import com.aura.assistant.tools.StopAlarmTool
import com.aura.assistant.tools.ToolRegistry
import com.aura.assistant.tools.WeatherTool
import com.aura.assistant.tools.WebSearchTool
import com.aura.assistant.voice.ListeningState
import com.aura.assistant.voice.SpeechToText
import com.aura.assistant.voice.TextToSpeechClient
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

object AssistantSession {

  private var appContext: Context? = null
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private val httpClient: OkHttpClient =
      OkHttpClient.Builder()
          .connectTimeout(20, TimeUnit.SECONDS)
          .readTimeout(90, TimeUnit.SECONDS)
          .callTimeout(120, TimeUnit.SECONDS)
          .build()

  // --- Observable state (read from Compose) ---
  val messages: SnapshotStateList<ChatMessage> = mutableStateListOf()
  var thinking by mutableStateOf(false)
    private set

  var status by mutableStateOf<String?>(null)
    private set

  var listeningState by mutableStateOf(ListeningState.OFF)

  // --- Backing config ---
  private var settings: AppSettings? = null
  private var provider: AiProvider? = null
  private var agent: OpenAiAgent? = null
  var stt: SpeechToText? = null
    private set

  var tts: TextToSpeechClient? = null
    private set

  fun init(context: Context) {
    appContext = context.applicationContext
    refresh()
  }

  /** Reload settings and rebuild the provider + voice clients. */
  fun refresh() {
    val ctx = appContext ?: return
    scope.launch {
      val s = SettingsRepository(ctx).settings.first()
      settings = s
      provider = if (s.isUsable()) ProviderFactory.create(s) else null

      // Tool-calling agent for OpenAI-compatible providers (they support function calling).
      agent =
          if (provider != null &&
              (s.providerType == ProviderType.OPENAI || s.providerType == ProviderType.CUSTOM)) {
            val tools = buildList {
              add(WeatherTool(httpClient, s.defaultLocation))
              add(WebSearchTool(httpClient))
              add(SetTimerTool(ctx))
              add(SetAlarmTool(ctx))
              add(SetReminderTool(ctx))
              add(ListAlarmsTool())
              add(CancelAlarmTool(ctx))
              add(StopAlarmTool(ctx))
              if (s.haUrl.isNotBlank() && s.haToken.isNotBlank()) {
                val ha = HomeAssistantClient(s.haUrl, s.haToken, httpClient)
                add(HaGetStatesTool(ha))
                add(HaCallServiceTool(ha))
              }
            }
            OpenAiAgent(s.effectiveBaseUrl(), s.apiKey, s.effectiveModel(), httpClient, ToolRegistry(tools))
          } else {
            null
          }

      val voiceBase = s.voiceBaseUrl()
      if (voiceBase != null && s.apiKey.isNotBlank()) {
        stt = SpeechToText(voiceBase, s.apiKey, client = httpClient)
        tts = TextToSpeechClient(ctx, voiceBase, s.apiKey, client = httpClient)
      } else {
        stt = null
        tts = null
      }

      status =
          when {
            provider == null -> "No provider configured — open Aura settings."
            else -> "Connected to ${provider!!.name} · ${s.effectiveModel()}"
          }
    }
  }

  private fun currentTimeContext(): String {
    val now = ZonedDateTime.now()
    val formatted =
        now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a zzz", Locale.US))
    val sb =
        StringBuilder(
            "For reference, the current local date and time is $formatted. " +
                "Answer time/date questions directly using this.")
    settings?.defaultLocation?.takeIf { it.isNotBlank() }?.let {
      sb.append(
          " The user's default location is $it; use it for weather and other location-based " +
              "questions whenever they don't name a place.")
    }
    return sb.toString()
  }

  fun currentSettings(): AppSettings? = settings

  fun activationMode() = settings?.activationMode

  fun wakePhrase(): String = settings?.wakePhrase?.trim().orEmpty()

  /** Voice round-trip is possible only with an OpenAI-compatible STT/TTS + a provider. */
  fun voiceAvailable(): Boolean = stt != null && tts != null && provider != null

  /**
   * Send user text to the provider, append the reply, and optionally speak it.
   * Drives listeningState through THINKING/SPEAKING when voice is active so the
   * capture loop knows to pause.
   */
  fun sendUserText(text: String, speak: Boolean) {
    val prompt = text.trim()
    if (prompt.isEmpty() || thinking) return
    val p = provider
    if (p == null) {
      status = "No provider configured — open Aura settings."
      return
    }
    messages.add(ChatMessage.user(prompt))
    thinking = true
    status = null
    if (listeningState != ListeningState.OFF) listeningState = ListeningState.THINKING

    scope.launch {
      val convo = buildList {
        settings?.systemPrompt?.takeIf { it.isNotBlank() }?.let { add(ChatMessage.system(it)) }
        // The model has no clock; give it the real local date/time each turn.
        add(ChatMessage.system(currentTimeContext()))
        addAll(messages)
      }
      try {
        // Use the tool-calling agent when available; otherwise a plain completion.
        val reply = agent?.run(convo) ?: p.complete(convo)
        messages.add(ChatMessage.assistant(reply))
        if (speak) {
          tts?.let { engine ->
            if (listeningState != ListeningState.OFF) listeningState = ListeningState.SPEAKING
            runCatching { engine.speak(reply) }
          }
        }
      } catch (e: Exception) {
        status = "Error: ${e.message}"
      } finally {
        thinking = false
        if (listeningState != ListeningState.OFF) listeningState = ListeningState.IDLE
      }
    }
  }
}
