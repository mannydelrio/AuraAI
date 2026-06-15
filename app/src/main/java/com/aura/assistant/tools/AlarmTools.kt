/*
 * Aura — tools for on-device timers, alarms, and reminders.
 */
package com.aura.assistant.tools

import android.content.Context
import android.content.Intent
import com.aura.assistant.alarm.AlarmScheduler
import com.aura.assistant.alarm.AlarmService
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject

private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

private fun clock(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(CLOCK)

/** set_timer — fire after N minutes from now. */
class SetTimerTool(private val context: Context) : Tool {
  override val name = "set_timer"
  override val description = "Start a countdown timer that goes off after a number of minutes."
  override fun parameters(): JSONObject =
      JSONObject(
          """{"type":"object","properties":{"duration_minutes":{"type":"number","description":"Minutes from now"},"label":{"type":"string","description":"Optional name"}},"required":["duration_minutes"]}""")

  override suspend fun execute(args: JSONObject): String {
    val minutes = args.optDouble("duration_minutes", 0.0)
    if (minutes <= 0) return "How many minutes should the timer run?"
    val fireAt = System.currentTimeMillis() + (minutes * 60_000).toLong()
    val label = args.optString("label").ifBlank { "Timer" }
    val s = AlarmScheduler.schedule(context, fireAt, label, "timer")
    val pretty = if (minutes >= 1) "${minutes.toInt()} min" else "${(minutes * 60).toInt()} sec"
    return "Timer set for $pretty from now (goes off at ${clock(fireAt)}). [id ${s.id}]"
  }
}

/** set_alarm — fire at a specific clock time (next occurrence). */
class SetAlarmTool(private val context: Context) : Tool {
  override val name = "set_alarm"
  override val description =
      "Set an alarm for a specific time of day. Pass time as 24-hour HH:MM (e.g. 07:30 or 18:00)."
  override fun parameters(): JSONObject =
      JSONObject(
          """{"type":"object","properties":{"time_24h":{"type":"string","description":"Time in 24-hour HH:MM"},"label":{"type":"string","description":"Optional name"}},"required":["time_24h"]}""")

  override suspend fun execute(args: JSONObject): String {
    val raw = args.optString("time_24h").trim()
    val parts = raw.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()
    val minute = parts.getOrNull(1)?.toIntOrNull()
    if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
      return "I need a valid time like 07:30."
    }
    val now = ZonedDateTime.now()
    var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!target.isAfter(now)) target = target.plusDays(1)
    val fireAt = target.toInstant().toEpochMilli()
    val label = args.optString("label").ifBlank { "Alarm" }
    val s = AlarmScheduler.schedule(context, fireAt, label, "alarm")
    return "Alarm set for ${clock(fireAt)}. [id ${s.id}]"
  }
}

/** set_reminder — fire after N minutes with a message. */
class SetReminderTool(private val context: Context) : Tool {
  override val name = "set_reminder"
  override val description = "Set a reminder that notifies you after a number of minutes."
  override fun parameters(): JSONObject =
      JSONObject(
          """{"type":"object","properties":{"in_minutes":{"type":"number","description":"Minutes from now"},"text":{"type":"string","description":"What to be reminded about"}},"required":["in_minutes","text"]}""")

  override suspend fun execute(args: JSONObject): String {
    val minutes = args.optDouble("in_minutes", 0.0)
    val text = args.optString("text").ifBlank { "Reminder" }
    if (minutes <= 0) return "When should I remind you (in how many minutes)?"
    val fireAt = System.currentTimeMillis() + (minutes * 60_000).toLong()
    val s = AlarmScheduler.schedule(context, fireAt, text, "reminder")
    return "Reminder set for ${clock(fireAt)}: \"$text\". [id ${s.id}]"
  }
}

/** list_alarms — what's currently scheduled. */
class ListAlarmsTool : Tool {
  override val name = "list_alarms"
  override val description = "List all currently scheduled timers, alarms, and reminders."
  override fun parameters(): JSONObject = JSONObject("""{"type":"object","properties":{}}""")

  override suspend fun execute(args: JSONObject): String {
    val items = AlarmScheduler.list()
    if (items.isEmpty()) return "Nothing is scheduled right now."
    return items.joinToString("\n") { "• [${it.id}] ${it.kind} at ${clock(it.timeMillis)} — ${it.label}" }
  }
}

/** stop_alarm — silence a currently ringing alert. */
class StopAlarmTool(private val context: Context) : Tool {
  override val name = "stop_alarm"
  override val description = "Stop or silence an alarm, timer, or reminder that is currently going off."
  override fun parameters(): JSONObject = JSONObject("""{"type":"object","properties":{}}""")

  override suspend fun execute(args: JSONObject): String {
    context.startService(Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
    return "Stopped the alert."
  }
}

/** cancel_alarm — cancel by id. */
class CancelAlarmTool(private val context: Context) : Tool {
  override val name = "cancel_alarm"
  override val description = "Cancel a scheduled timer, alarm, or reminder by its id (from list_alarms)."
  override fun parameters(): JSONObject =
      JSONObject(
          """{"type":"object","properties":{"id":{"type":"integer","description":"The id to cancel"}},"required":["id"]}""")

  override suspend fun execute(args: JSONObject): String {
    val id = args.optInt("id", -1)
    return if (AlarmScheduler.cancel(context, id)) "Canceled [$id]." else "I couldn't find item $id."
  }
}
