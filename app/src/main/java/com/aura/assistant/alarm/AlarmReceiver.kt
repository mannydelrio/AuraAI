/*
 * Aura — fires when a timer/alarm/reminder is due and hands off to the (stoppable) AlarmService.
 */
package com.aura.assistant.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val id = intent.getIntExtra("id", 0)
    val label = intent.getStringExtra("label").orEmpty()
    val kind = intent.getStringExtra("kind") ?: "alarm"
    AlarmScheduler.remove(id)

    ContextCompat.startForegroundService(
        context,
        Intent(context, AlarmService::class.java).apply {
          putExtra("label", label)
          putExtra("kind", kind)
        })
  }
}
