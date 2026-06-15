/*
 * Aura — plays a stoppable alarm/timer/reminder alert.
 *
 * A foreground service that loops the alarm tone (via a MediaPlayer we hold a reference to)
 * and shows a notification with a STOP action. Auto-stops after 60s as a safety net so an
 * alert can never get "stuck" again.
 */
package com.aura.assistant.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.aura.assistant.MainActivity
import com.aura.assistant.R

class AlarmService : Service() {

  private var player: MediaPlayer? = null
  private val handler = Handler(Looper.getMainLooper())
  private val autoStop = Runnable { stopSelf() }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    val label = intent?.getStringExtra("label").orEmpty()
    val kind = intent?.getStringExtra("kind") ?: "alarm"
    startForeground(NOTIFICATION_ID, buildNotification(label, kind))
    startTone()
    handler.removeCallbacks(autoStop)
    handler.postDelayed(autoStop, 60_000) // safety auto-stop
    return START_STICKY
  }

  private fun startTone() {
    if (player != null) return
    val uri =
        RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
    player =
        MediaPlayer().apply {
          setAudioAttributes(
              AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_ALARM)
                  .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                  .build())
          setDataSource(this@AlarmService, uri)
          isLooping = true
          prepare()
          start()
        }
  }

  private fun buildNotification(label: String, kind: String): android.app.Notification {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      // No channel sound — we play (and can stop) the tone ourselves.
      val channel =
          NotificationChannel(CHANNEL_ID, "AuraAI Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(true)
          }
      mgr.createNotificationChannel(channel)
    }

    val title =
        when (kind) {
          "timer" -> "Timer"
          "reminder" -> "Reminder"
          else -> "Alarm"
        }
    val text = label.ifBlank { "$title is going off" }

    val stopPi =
        PendingIntent.getService(
            this,
            1,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val openPi =
        PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setOngoing(true)
        .setContentIntent(openPi)
        .addAction(0, "Stop", stopPi)
        .setFullScreenIntent(openPi, true)
        .build()
  }

  override fun onDestroy() {
    super.onDestroy()
    handler.removeCallbacks(autoStop)
    runCatching {
      player?.stop()
      player?.release()
    }
    player = null
  }

  companion object {
    const val ACTION_STOP = "com.aura.assistant.alarm.STOP"
    private const val CHANNEL_ID = "aura_alarms"
    private const val NOTIFICATION_ID = 3001
  }
}
