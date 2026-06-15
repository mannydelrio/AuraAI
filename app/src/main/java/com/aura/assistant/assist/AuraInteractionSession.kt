/*
 * Aura — minimal assistant session. When invoked (e.g. an assist gesture), it just makes
 * sure Aura's listener is running, then dismisses its own UI.
 */
package com.aura.assistant.assist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import androidx.core.content.ContextCompat
import com.aura.assistant.voice.VoiceService

class AuraInteractionSession(private val ctx: Context) : VoiceInteractionSession(ctx) {
  override fun onShow(args: Bundle?, showFlags: Int) {
    super.onShow(args, showFlags)
    ContextCompat.startForegroundService(ctx, Intent(ctx, VoiceService::class.java))
    hide()
  }
}
