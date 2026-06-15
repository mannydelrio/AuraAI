/*
 * Aura — relaunch the assistant overlay after a device reboot (if the user opted in).
 */
package com.aura.assistant.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aura.assistant.data.SettingsRepository
import com.aura.assistant.overlay.OverlayService
import com.aura.assistant.voice.VoiceService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    val settings = runBlocking { SettingsRepository(context).settings.first() }
    if (settings.autoStartOnBoot) {
      ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
      ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java))
    }
  }
}
