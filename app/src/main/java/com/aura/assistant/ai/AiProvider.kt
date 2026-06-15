/*
 * Aura — provider-agnostic AI interface.
 *
 * Any backend (Claude, OpenAI, or a local LLM exposed over an OpenAI-compatible URL)
 * implements this. The rest of the app only ever talks to AiProvider.
 */
package com.aura.assistant.ai

/** A single turn in the conversation. role is one of: "system", "user", "assistant". */
data class ChatMessage(val role: String, val content: String) {
  companion object {
    fun system(text: String) = ChatMessage("system", text)
    fun user(text: String) = ChatMessage("user", text)
    fun assistant(text: String) = ChatMessage("assistant", text)
  }
}

interface AiProvider {
  /** Human-readable provider name, for UI/logs. */
  val name: String

  /**
   * Send the full message list and return the assistant's reply text.
   * Suspends on a background dispatcher; throws on network/HTTP/parse failure.
   */
  suspend fun complete(messages: List<ChatMessage>): String
}
