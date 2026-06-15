/*
 * Aura — stub RecognitionService. Required by the voice-interaction metadata; we don't use
 * the system recognizer (Aura does its own STT), so the callbacks are intentionally empty.
 */
package com.aura.assistant.assist

import android.content.Intent
import android.speech.RecognitionService

class AuraRecognitionService : RecognitionService() {
  override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {}

  override fun onStopListening(listener: Callback?) {}

  override fun onCancel(listener: Callback?) {}
}
