/*
 * Aura — builds the right AiProvider from the user's settings.
 */
package com.aura.assistant.ai

import com.aura.assistant.data.AppSettings
import com.aura.assistant.data.ProviderType
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object ProviderFactory {

  private val client: OkHttpClient =
      OkHttpClient.Builder()
          .connectTimeout(20, TimeUnit.SECONDS)
          .readTimeout(60, TimeUnit.SECONDS)
          .callTimeout(90, TimeUnit.SECONDS)
          .build()

  fun create(settings: AppSettings): AiProvider {
    val baseUrl = settings.effectiveBaseUrl()
    val model = settings.effectiveModel()
    return when (settings.providerType) {
      ProviderType.CLAUDE -> AnthropicProvider(baseUrl, settings.apiKey, model, client)
      ProviderType.OPENAI ->
          OpenAiCompatibleProvider(baseUrl, settings.apiKey, model, client, name = "OpenAI")
      ProviderType.CUSTOM ->
          OpenAiCompatibleProvider(baseUrl, settings.apiKey, model, client, name = "Local LLM")
    }
  }
}
