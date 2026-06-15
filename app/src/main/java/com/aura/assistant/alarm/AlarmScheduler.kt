/*
 * Aura — schedules timers/alarms/reminders with AlarmManager and tracks the pending ones.
 */
package com.aura.assistant.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

object AlarmScheduler {

  data class Scheduled(val id: Int, val timeMillis: Long, val label: String, val kind: String)

  private val items = CopyOnWriteArrayList<Scheduled>()
  private val idGen = AtomicInteger(1)

  fun schedule(context: Context, timeMillis: Long, label: String, kind: String): Scheduled {
    val id = idGen.getAndIncrement()
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent(context, id, label, kind))
    return Scheduled(id, timeMillis, label, kind).also { items.add(it) }
  }

  fun list(): List<Scheduled> = items.sortedBy { it.timeMillis }

  fun cancel(context: Context, id: Int): Boolean {
    val s = items.firstOrNull { it.id == id } ?: return false
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    am.cancel(pendingIntent(context, id, s.label, s.kind))
    items.remove(s)
    return true
  }

  /** Called by the receiver when an alarm fires so it drops out of the active list. */
  fun remove(id: Int) {
    items.removeAll { it.id == id }
  }

  private fun pendingIntent(context: Context, id: Int, label: String, kind: String): PendingIntent {
    val intent =
        Intent(context, AlarmReceiver::class.java).apply {
          putExtra("id", id)
          putExtra("label", label)
          putExtra("kind", kind)
        }
    return PendingIntent.getBroadcast(
        context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  }
}
