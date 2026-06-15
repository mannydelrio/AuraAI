/*
 * Aura — supplies the voice-interaction session (required companion to the service).
 */
package com.aura.assistant.assist

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class AuraInteractionSessionService : VoiceInteractionSessionService() {
  override fun onNewSession(args: Bundle?): VoiceInteractionSession = AuraInteractionSession(this)
}
