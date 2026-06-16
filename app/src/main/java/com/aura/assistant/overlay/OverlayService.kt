/*
 * Aura — the always-on overlay.
 *
 * A foreground service that adds a floating bubble on top of whatever else is on screen
 * (TYPE_APPLICATION_OVERLAY). Tapping the bubble expands a chat panel backed by the shared
 * AssistantSession, so typed and spoken turns share one conversation. The bubble color
 * reflects the live voice state (listening / thinking / speaking).
 */
package com.aura.assistant.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aura.assistant.AssistantSession
import com.aura.assistant.R
import com.aura.assistant.ai.ChatMessage
import com.aura.assistant.ui.theme.AuraTheme
import com.aura.assistant.voice.ListeningState

class OverlayService : Service() {

  private lateinit var windowManager: WindowManager
  private var overlayView: ComposeView? = null
  private val lifecycleOwner = OverlayLifecycleOwner()
  private lateinit var params: WindowManager.LayoutParams

  private var expanded by mutableStateOf(false)

  private val handler = Handler(Looper.getMainLooper())

  // Re-checks every few seconds that the bubble is still attached, re-adding it if the
  // window got dropped (low-RAM kill on Gen 1 Portals, launcher transitions, etc.).
  private val watchdog =
      object : Runnable {
        override fun run() {
          ensureOverlay()
          handler.postDelayed(this, WATCHDOG_MS)
        }
      }

  // The Superframe screensaver is a system "dream" that draws above any app overlay, so the
  // bubble can't show during it. The moment it ends (or the screen turns on / the user
  // returns) we re-assert the bubble so it's back instantly.
  private val systemReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = ensureOverlay()
      }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    lifecycleOwner.onCreate()
    val filter =
        IntentFilter().apply {
          addAction(Intent.ACTION_SCREEN_ON)
          addAction(Intent.ACTION_USER_PRESENT)
          addAction(Intent.ACTION_DREAMING_STOPPED)
        }
    runCatching { registerReceiver(systemReceiver, filter) }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // START_STICKY so the system relaunches the overlay if the process is killed (e.g. low
    // memory when another app opens) — this keeps the bubble above every app, persistently.
    startForeground(NOTIFICATION_ID, buildNotification())
    AssistantSession.refresh()
    ensureOverlay()
    handler.removeCallbacks(watchdog)
    handler.postDelayed(watchdog, WATCHDOG_MS)
    return START_STICKY
  }

  /** Add the overlay if missing, or re-add it if its window got detached. */
  private fun ensureOverlay() {
    val v = overlayView
    if (v == null) {
      addOverlay()
      return
    }
    if (!v.isAttachedToWindow) {
      runCatching { windowManager.removeViewImmediate(v) }
      overlayView = null
      addOverlay()
    }
  }

  private fun addOverlay() {
    params =
        WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                collapsedFlags(),
                PixelFormat.TRANSLUCENT,
            )
            .apply {
              gravity = Gravity.TOP or Gravity.START
              x = 32
              y = 180 // clear of Portal's top 64dp system overlay strip
            }

    val view =
        ComposeView(this).apply {
          setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
          setViewTreeLifecycleOwner(lifecycleOwner)
          setViewTreeViewModelStoreOwner(lifecycleOwner)
          setViewTreeSavedStateRegistryOwner(lifecycleOwner)
          setContent { OverlayRoot() }
        }
    overlayView = view

    try {
      windowManager.addView(view, params)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to add overlay — is SYSTEM_ALERT_WINDOW granted?", e)
      stopSelf()
    }
  }

  // FLAG_KEEP_SCREEN_ON: the overlay sits over every app, so this keeps the Portal screen
  // awake at all times while Aura is running (prevents sleep + the Superframe screensaver).
  private fun collapsedFlags() =
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
          WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
          WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

  private fun expandedFlags() =
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
          WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
          WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

  private fun applyExpanded(value: Boolean) {
    expanded = value
    params.flags = if (value) expandedFlags() else collapsedFlags()
    if (value) params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    overlayView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
  }

  private fun moveBy(dx: Float, dy: Float) {
    params.x += dx.toInt()
    params.y += dy.toInt()
    overlayView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
  }

  private fun submit(text: String) {
    if (text.isBlank()) return
    // Speak typed replies only when voice mode is active.
    AssistantSession.sendUserText(text, speak = AssistantSession.listeningState != ListeningState.OFF)
  }

  override fun onDestroy() {
    super.onDestroy()
    handler.removeCallbacks(watchdog)
    runCatching { unregisterReceiver(systemReceiver) }
    overlayView?.let { runCatching { windowManager.removeView(it) } }
    overlayView = null
    lifecycleOwner.onDestroy()
  }

  // ---------------------------------------------------------------------------
  // Compose UI
  // ---------------------------------------------------------------------------

  @Composable
  private fun OverlayRoot() {
    AuraTheme { if (expanded) ChatPanel() else Bubble() }
  }

  @Composable
  private fun Bubble() {
    val state = AssistantSession.listeningState
    val color =
        when (state) {
          ListeningState.LISTENING -> Color(0xFF31A24C) // green: hearing you
          ListeningState.TRANSCRIBING,
          ListeningState.THINKING -> Color(0xFFE5A000) // amber: working
          ListeningState.SPEAKING -> Color(0xFF1B74E4) // bright blue: replying
          ListeningState.ERROR -> Color(0xFFD0021B)
          else -> MaterialTheme.colorScheme.primary
        }
    Box(
        modifier =
            Modifier.size(64.dp)
                .background(color, CircleShape)
                .clickable { applyExpanded(true) }
                .pointerInput(Unit) {
                  detectDragGestures { change, dragAmount ->
                    change.consume()
                    moveBy(dragAmount.x, dragAmount.y)
                  }
                },
        contentAlignment = Alignment.Center,
    ) {
      Text(
          text = "AuraAI",
          color = Color(0xFFF0F0F0),
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.labelMedium,
      )
    }
  }

  @Composable
  private fun ChatPanel() {
    var input by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val messages = AssistantSession.messages
    val thinking = AssistantSession.thinking
    val status = AssistantSession.status

    Card(
        modifier = Modifier.width(360.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("AuraAI", style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.width(8.dp))
          Text(stateLabel(AssistantSession.listeningState),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.weight(1f))
          TextButton(onClick = { applyExpanded(false) }) { Text("Hide") }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (messages.isEmpty() && !thinking) {
            Text(
                status ?: "Ask me anything.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
          }
          messages.forEach { MessageBubble(it) }
          if (thinking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
              Spacer(Modifier.width(8.dp))
              Text("Thinking…", style = MaterialTheme.typography.bodyMedium)
            }
          }
        }

        status?.takeIf { messages.isNotEmpty() }?.let {
          Text(
              it,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(top = 8.dp),
          )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
              value = input,
              onValueChange = { input = it },
              modifier = Modifier.weight(1f),
              placeholder = { Text("Type a message") },
              singleLine = true,
              keyboardActions = KeyboardActions(onSend = { submit(input); input = "" }),
          )
          Spacer(Modifier.width(8.dp))
          Button(onClick = { submit(input); input = "" }, enabled = !thinking) { Text("Send") }
        }
      }
    }

    androidx.compose.runtime.LaunchedEffect(messages.size, thinking) {
      scrollState.animateScrollTo(scrollState.maxValue)
    }
  }

  private fun stateLabel(state: ListeningState): String =
      when (state) {
        ListeningState.OFF -> ""
        ListeningState.IDLE -> "listening…"
        ListeningState.LISTENING -> "hearing you"
        ListeningState.TRANSCRIBING -> "transcribing"
        ListeningState.THINKING -> "thinking"
        ListeningState.SPEAKING -> "speaking"
        ListeningState.ERROR -> "mic error"
      }

  @Composable
  private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val bg = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
    val fg =
        if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
      Surface(color = bg, shape = MaterialTheme.shapes.medium) {
        Text(
            text = message.content,
            color = fg,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }
  }

  private fun buildNotification(): Notification {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      mgr.createNotificationChannel(
          NotificationChannel(
              CHANNEL_ID,
              getString(R.string.overlay_channel_name),
              NotificationManager.IMPORTANCE_LOW))
    }
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.overlay_notification_title))
        .setContentText(getString(R.string.overlay_notification_text))
        .setSmallIcon(R.mipmap.ic_launcher)
        .setOngoing(true)
        .build()
  }

  private companion object {
    const val TAG = "OverlayService"
    const val CHANNEL_ID = "aura_overlay"
    const val NOTIFICATION_ID = 1001
    const val WATCHDOG_MS = 3000L
  }
}
