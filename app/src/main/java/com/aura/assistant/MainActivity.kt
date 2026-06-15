/*
 * Aura — in-app control panel: configure the AI provider, choose activation mode,
 * grant the overlay permission, and start/stop the always-on assistant.
 */
package com.aura.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aura.assistant.data.ActivationMode
import com.aura.assistant.data.AppSettings
import com.aura.assistant.data.ProviderType
import com.aura.assistant.data.SettingsRepository
import com.aura.assistant.overlay.OverlayService
import com.aura.assistant.ui.theme.AuraTheme
import com.aura.assistant.voice.VoiceService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Always-on: bring the overlay up automatically whenever the app opens (if permitted).
    // Combined with OverlayService's START_STICKY, the bubble stays above every app and
    // returns on its own if the process is killed.
    if (Settings.canDrawOverlays(this)) {
      ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
    }
    // Always-on voice: start the mic listener too (it self-checks permission/provider).
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED) {
      ContextCompat.startForegroundService(this, Intent(this, VoiceService::class.java))
    }
    setContent { AuraTheme { AppRoot() } }
  }
}

@Composable
private fun AppRoot() {
  var screen by remember { mutableStateOf(Screen.HOME) }
  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    when (screen) {
      Screen.HOME -> HomeScreen(onOpenSettings = { screen = Screen.SETTINGS })
      Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.HOME })
    }
  }
}

private enum class Screen {
  HOME,
  SETTINGS,
}

@Composable
private fun SetupRow(label: String, ok: Boolean, hint: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(if (ok) "✅" else "⚠️")
    Spacer(Modifier.width(8.dp))
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.weight(1f))
    Text(
        hint,
        style = MaterialTheme.typography.bodySmall,
        color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
    )
  }
}

/** Reserve Portal's top 64dp system overlay strip, plus standard 16dp padding. */
private fun Modifier.portalPadding() = this.padding(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 16.dp)

@Composable
private fun HomeScreen(onOpenSettings: () -> Unit) {
  val context = LocalContext.current
  val repo = remember { SettingsRepository(context) }
  val settings by repo.settings.collectAsState(initial = null)

  Column(
      modifier = Modifier.fillMaxSize().portalPadding().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("AuraAI", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Your always-on AI assistant, over everything.",
        style = MaterialTheme.typography.bodyLarge,
    )

    // Setup checklist — handy when installing on a new Portal
    run {
      val canOverlayNow = Settings.canDrawOverlays(context)
      val micOk =
          ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
              PackageManager.PERMISSION_GRANTED
      val assistantOk =
          (Settings.Secure.getString(context.contentResolver, "voice_interaction_service") ?: "")
              .contains("com.aura.assistant")
      val providerOk = settings?.isUsable() == true
      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Setup", style = MaterialTheme.typography.titleMedium)
          SetupRow("AI provider", providerOk, if (providerOk) "Ready" else "Add your key in Settings")
          SetupRow("Display over apps", canOverlayNow, if (canOverlayNow) "Granted" else "Run setup script")
          SetupRow("Microphone", micOk, if (micOk) "Granted" else "Grant below in Voice")
          SetupRow(
              "Assistant (voice over apps)", assistantOk, if (assistantOk) "Set" else "Run setup script")
        }
      }
    }

    // Provider status
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("AI provider", style = MaterialTheme.typography.titleMedium)
        val s = settings
        if (s == null) {
          Text("Loading…", style = MaterialTheme.typography.bodyMedium)
        } else if (s.isUsable()) {
          Text("${s.providerType.name} · ${s.effectiveModel()}",
              style = MaterialTheme.typography.bodyMedium)
          Text("Activation: ${s.activationMode.name}",
              style = MaterialTheme.typography.bodySmall)
        } else {
          Text("Not configured yet — add an API key or local URL in Settings.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary)
        }
      }
    }

    // Overlay permission
    val canOverlay = Settings.canDrawOverlays(context)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Display over other apps", style = MaterialTheme.typography.titleMedium)
        Text(
            if (canOverlay) "Granted ✓" else "Required so AuraAI can float over other apps.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!canOverlay) {
          Button(onClick = {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")))
          }) {
            Text("Grant permission")
          }
        }
      }
    }

    // Start / stop
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Button(
          onClick = {
            ContextCompat.startForegroundService(
                context, Intent(context, OverlayService::class.java))
          },
          enabled = canOverlay,
      ) {
        Text("Start AuraAI")
      }
      OutlinedButton(onClick = {
        context.stopService(Intent(context, OverlayService::class.java))
      }) {
        Text("Stop")
      }
    }

    // Voice
    val voiceOk =
        settings?.let { it.providerType != ProviderType.CLAUDE && it.apiKey.isNotBlank() } ?: false
    var micGranted by remember {
      mutableStateOf(
          ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
              PackageManager.PERMISSION_GRANTED)
    }
    val micLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
          micGranted = granted
        }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Voice", style = MaterialTheme.typography.titleMedium)
        if (!voiceOk) {
          Text(
              "Voice needs an OpenAI-compatible provider with an API key (Claude has no speech API). Set one in Settings.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
          )
        } else {
          Text("State: ${AssistantSession.listeningState.name}",
              style = MaterialTheme.typography.bodySmall)
          if (!micGranted) {
            Button(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
              Text("Grant microphone")
            }
          } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Button(onClick = {
                ContextCompat.startForegroundService(
                    context, Intent(context, VoiceService::class.java))
              }) {
                Text("Start listening")
              }
              OutlinedButton(onClick = {
                context.stopService(Intent(context, VoiceService::class.java))
              }) {
                Text("Stop listening")
              }
            }
          }
        }
      }
    }

    HorizontalDivider()
    TextButton(onClick = onOpenSettings) { Text("Settings →") }
  }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val repo = remember { SettingsRepository(context) }
  val scope = rememberCoroutineScope()
  val loaded by repo.settings.collectAsState(initial = null)
  // Seed the editable form once, from the first loaded snapshot.
  val initial = remember(loaded != null) { loaded }

  if (initial == null) {
    Column(Modifier.fillMaxSize().portalPadding()) { Text("Loading…") }
    return
  }

  var providerType by remember { mutableStateOf(initial.providerType) }
  var baseUrl by remember { mutableStateOf(initial.baseUrl) }
  var apiKey by remember { mutableStateOf(initial.apiKey) }
  var model by remember { mutableStateOf(initial.model) }
  var systemPrompt by remember { mutableStateOf(initial.systemPrompt) }
  var activation by remember { mutableStateOf(initial.activationMode) }
  var wakePhrase by remember { mutableStateOf(initial.wakePhrase) }
  var autoStart by remember { mutableStateOf(initial.autoStartOnBoot) }
  var defaultLocation by remember { mutableStateOf(initial.defaultLocation) }
  var haUrl by remember { mutableStateOf(initial.haUrl) }
  var haToken by remember { mutableStateOf(initial.haToken) }
  var saved by remember { mutableStateOf(false) }

  Column(
      modifier = Modifier.fillMaxSize().portalPadding().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onBack) { Text("← Back") }
      Spacer(Modifier.weight(1f))
      Text("Settings", style = MaterialTheme.typography.headlineSmall)
    }

    // Provider type
    Text("Provider", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ProviderType.values().forEach { type ->
        val selected = providerType == type
        if (selected) {
          Button(onClick = { providerType = type }) { Text(type.name) }
        } else {
          OutlinedButton(onClick = { providerType = type }) { Text(type.name) }
        }
      }
    }

    OutlinedTextField(
        value = baseUrl,
        onValueChange = { baseUrl = it; saved = false },
        label = { Text("Base URL") },
        placeholder = { Text(providerType.defaultBaseUrl().ifBlank { "http://192.168.x.x:11434/v1" }) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = apiKey,
        onValueChange = { apiKey = it; saved = false },
        label = { Text("API key" + if (providerType == ProviderType.CUSTOM) " (optional)" else "") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = model,
        onValueChange = { model = it; saved = false },
        label = { Text("Model") },
        placeholder = { Text(providerType.defaultModel().ifBlank { "model name" }) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = systemPrompt,
        onValueChange = { systemPrompt = it; saved = false },
        label = { Text("System prompt") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
    )

    OutlinedTextField(
        value = defaultLocation,
        onValueChange = { defaultLocation = it; saved = false },
        label = { Text("Default location") },
        placeholder = { Text("e.g. Miami, FL — used for weather when none is said") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    HorizontalDivider()

    // Smart home
    Text("Smart home (Home Assistant)", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = haUrl,
        onValueChange = { haUrl = it; saved = false },
        label = { Text("Home Assistant URL") },
        placeholder = { Text("http://192.168.1.136:8123") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = haToken,
        onValueChange = { haToken = it; saved = false },
        label = { Text("Home Assistant token") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    HorizontalDivider()

    // Activation mode
    Text("Activation", style = MaterialTheme.typography.titleMedium)
    ActivationMode.values().forEach { mode ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = activation == mode, onClick = { activation = mode; saved = false })
        Spacer(Modifier.width(8.dp))
        Text(
            when (mode) {
              ActivationMode.WAKE_WORD -> "Wake word"
              ActivationMode.CONTINUOUS -> "Continuous listening"
            },
            style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
    if (activation == ActivationMode.WAKE_WORD) {
      OutlinedTextField(
          value = wakePhrase,
          onValueChange = { wakePhrase = it; saved = false },
          label = { Text("Wake phrase") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
      )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Switch(checked = autoStart, onCheckedChange = { autoStart = it; saved = false })
      Spacer(Modifier.width(8.dp))
      Text("Start automatically after reboot", style = MaterialTheme.typography.bodyLarge)
    }

    HorizontalDivider()

    Row(verticalAlignment = Alignment.CenterVertically) {
      Button(onClick = {
        scope.launch {
          repo.save(
              AppSettings(
                  providerType = providerType,
                  baseUrl = baseUrl.trim(),
                  apiKey = apiKey.trim(),
                  model = model.trim(),
                  systemPrompt = systemPrompt,
                  activationMode = activation,
                  wakePhrase = wakePhrase.trim(),
                  autoStartOnBoot = autoStart,
                  defaultLocation = defaultLocation.trim(),
                  haUrl = haUrl.trim(),
                  haToken = haToken.trim(),
              ))
          // Apply changes immediately (provider, location, tools) without a restart.
          AssistantSession.refresh()
          saved = true
        }
      }) {
        Text("Save")
      }
      if (saved) {
        Spacer(Modifier.width(12.dp))
        Text("Saved ✓", color = MaterialTheme.colorScheme.primary)
      }
    }
  }
}
