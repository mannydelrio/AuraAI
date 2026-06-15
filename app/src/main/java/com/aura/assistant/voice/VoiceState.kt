/*
 * Aura — voice pipeline state, shared across the overlay UI and the voice service.
 */
package com.aura.assistant.voice

enum class ListeningState {
  OFF, // voice service not running
  IDLE, // mic open, waiting for speech
  LISTENING, // capturing an utterance
  TRANSCRIBING, // sending audio to STT
  THINKING, // waiting on the AI provider
  SPEAKING, // playing the spoken reply
  ERROR,
}
