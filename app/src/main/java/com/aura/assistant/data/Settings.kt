/*
 * Aura — user settings, persisted with Preferences DataStore.
 *
 * Everything the assistant needs to reach any AI backend lives here, plus how it
 * should activate (wake word vs continuous listening).
 */
package com.aura.assistant.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Which AI backend to talk to. */
enum class ProviderType {
  CLAUDE,
  OPENAI,
  CUSTOM;

  /** Default API base URL when the user leaves the field blank. */
  fun defaultBaseUrl(): String =
      when (this) {
        CLAUDE -> "https://api.anthropic.com"
        OPENAI -> "https://api.openai.com/v1"
        CUSTOM -> "" // e.g. http://192.168.1.50:11434/v1 (Ollama), :1234/v1 (LM Studio)
      }

  /** Sensible default model when the user leaves the field blank. */
  fun defaultModel(): String =
      when (this) {
        CLAUDE -> "claude-sonnet-4-6"
        OPENAI -> "gpt-4o-mini"
        CUSTOM -> ""
      }
}

/** How the assistant starts listening for a command. */
enum class ActivationMode {
  WAKE_WORD,
  CONTINUOUS,
}

/** Immutable snapshot of all user settings. */
data class AppSettings(
    val providerType: ProviderType = ProviderType.CLAUDE,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val activationMode: ActivationMode = ActivationMode.WAKE_WORD,
    val wakePhrase: String = "Hey Aura",
    val autoStartOnBoot: Boolean = false,
    val defaultLocation: String = "",
    val haUrl: String = "",
    val haToken: String = "",
) {
  fun effectiveBaseUrl(): String = baseUrl.ifBlank { providerType.defaultBaseUrl() }

  fun effectiveModel(): String = model.ifBlank { providerType.defaultModel() }

  /**
   * Base URL for voice (STT/TTS) endpoints, which follow the OpenAI audio API.
   * Only OpenAI-compatible providers expose these; Claude does not, so voice is null there.
   */
  fun voiceBaseUrl(): String? =
      when (providerType) {
        ProviderType.OPENAI, ProviderType.CUSTOM -> effectiveBaseUrl().ifBlank { null }
        ProviderType.CLAUDE -> null
      }

  /** Enough config present to actually call the backend. */
  fun isUsable(): Boolean {
    if (effectiveBaseUrl().isBlank() || effectiveModel().isBlank()) return false
    // Cloud providers require a key; a local/custom endpoint may not.
    return when (providerType) {
      ProviderType.CLAUDE, ProviderType.OPENAI -> apiKey.isNotBlank()
      ProviderType.CUSTOM -> true
    }
  }

  companion object {
    const val DEFAULT_SYSTEM_PROMPT =
        "You are Aura, a friendly voice assistant running on a Meta Portal device in the user's " +
            "home. Keep answers short, natural, and conversational — you will often be heard aloud. " +
            "If you are unsure, say so briefly."
  }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aura_settings")

class SettingsRepository(private val context: Context) {

  private object Keys {
    val PROVIDER = stringPreferencesKey("provider_type")
    val BASE_URL = stringPreferencesKey("base_url")
    val API_KEY = stringPreferencesKey("api_key")
    val MODEL = stringPreferencesKey("model")
    val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
    val ACTIVATION = stringPreferencesKey("activation_mode")
    val WAKE_PHRASE = stringPreferencesKey("wake_phrase")
    val AUTO_START = booleanPreferencesKey("auto_start_on_boot")
    val DEFAULT_LOCATION = stringPreferencesKey("default_location")
    val HA_URL = stringPreferencesKey("ha_url")
    val HA_TOKEN = stringPreferencesKey("ha_token")
  }

  val settings: Flow<AppSettings> =
      context.dataStore.data.map { p ->
        AppSettings(
            providerType =
                runCatching { ProviderType.valueOf(p[Keys.PROVIDER] ?: "") }
                    .getOrDefault(ProviderType.CLAUDE),
            baseUrl = p[Keys.BASE_URL] ?: "",
            apiKey = p[Keys.API_KEY] ?: "",
            model = p[Keys.MODEL] ?: "",
            systemPrompt = p[Keys.SYSTEM_PROMPT] ?: AppSettings.DEFAULT_SYSTEM_PROMPT,
            activationMode =
                runCatching { ActivationMode.valueOf(p[Keys.ACTIVATION] ?: "") }
                    .getOrDefault(ActivationMode.WAKE_WORD),
            wakePhrase = p[Keys.WAKE_PHRASE] ?: "Hey Aura",
            autoStartOnBoot = p[Keys.AUTO_START] ?: false,
            defaultLocation = p[Keys.DEFAULT_LOCATION] ?: "",
            haUrl = p[Keys.HA_URL] ?: "",
            haToken = p[Keys.HA_TOKEN] ?: "",
        )
      }

  suspend fun save(s: AppSettings) {
    context.dataStore.edit { p ->
      p[Keys.PROVIDER] = s.providerType.name
      p[Keys.BASE_URL] = s.baseUrl
      p[Keys.API_KEY] = s.apiKey
      p[Keys.MODEL] = s.model
      p[Keys.SYSTEM_PROMPT] = s.systemPrompt
      p[Keys.ACTIVATION] = s.activationMode.name
      p[Keys.WAKE_PHRASE] = s.wakePhrase
      p[Keys.AUTO_START] = s.autoStartOnBoot
      p[Keys.DEFAULT_LOCATION] = s.defaultLocation
      p[Keys.HA_URL] = s.haUrl
      p[Keys.HA_TOKEN] = s.haToken
    }
  }
}
