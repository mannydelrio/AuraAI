# Aura — Project Log & Documentation

An always-on, voice-driven **AI agent** that floats over whatever is on screen — a
self-hosted alternative to Google Assistant / Siri / Alexa, built for **Meta Portal**.

Built from scratch on **2026-06-14 → 2026-06-15**.

- **Location:** `C:\Users\Manny\Desktop\Portal\aura`
- **Package:** `com.aura.assistant`
- **Target device:** Meta Portal (model `Portal`, codename `omni`), serial `821LCM04Z110UJ29`
- **Base:** transformed from Meta's [meta-quest/portal-samples](https://github.com/meta-quest/portal-samples)

---

## 1. What it does (current status)

| Capability | Status | Notes |
|---|---|---|
| Floating overlay bubble over any app | ✅ | `TYPE_APPLICATION_OVERLAY`, draggable, tap-to-chat |
| Voice in/out | ✅ | whisper-1 (STT) + tts-1 "alloy" (TTS) |
| Wake word ("Hey Aura") **or** continuous listening | ✅ | user-selectable in Settings |
| Conversation (any AI provider) | ✅ | Claude / OpenAI / any OpenAI-compatible local URL |
| Time / date awareness | ✅ | real device clock injected into context each turn |
| Weather (live) | ✅ | Open-Meteo, no API key |
| Timers / alarms / reminders | ✅ | on-device, **stoppable** (notification button, "Hey Aura stop", 60s auto-stop) |
| Web search | ⚠️ | basic/keyless (DuckDuckGo Instant Answer) — limited |
| Calendar | ❌ | Portal calendar is Facebook-proprietary (`com.portal.calendar` exposes no content provider; standard `com.android.providers.calendar` is empty) — **not readable**. Aura could manage its *own* calendar via CalendarContract, but it would not see the Portal/family events. |
| Always-on screen (no Superframe / no sleep) | ✅ | device settings + overlay `FLAG_KEEP_SCREEN_ON` |
| Always-on overlay + voice | ✅ | both services `START_STICKY` + `stopWithTask=false` + auto-start in MainActivity.onCreate + BootReceiver. OverlayService also runs a **watchdog** (re-attaches the bubble every 3s if its window got dropped) and a **receiver** that re-asserts it on `SCREEN_ON` / `USER_PRESENT` / `DREAMING_STOPPED`. This keeps the bubble alive across low-RAM kills on **Gen 1 Portals** and after the launcher/screensaver. NOTE: a system screensaver ("dream") renders **above** app overlays, so it can't be covered — the setup script **disables** it (don't rely on drawing over it). |
| Voice works over other apps | ✅ | Android 10 silences the mic for background apps **except the assistant**. Aura registers as a `VoiceInteractionService` (`assist/` package) and is set as the default assistant → its UID is exempt from mic silencing. |
| Smart home (Home Assistant) | ✅ | `ha/HomeAssistantClient` + `home_assistant_get_states` / `home_assistant_call_service` tools. Configure HA URL + long-lived token in Settings. Needs `usesCleartextTraffic=true` (HA is local http). Verified reading live device states. |

Currently configured to use **OpenAI** (`gpt-4o-mini`) for the brain + voice.

---

## 2. Toolchain & environment

- **AGP** 9.2.1 · **Kotlin** 2.2.10 · **Compose BOM** 2026.02.01 · **Gradle** 9.4.1
- **compileSdk** 36.1 · **minSdk 28** · **targetSdk 29** (Meta's documented Portal targets)
- **JDK** 17 (`C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`)
- **Android SDK:** `C:\Users\Manny\AppData\Local\Android\Sdk` (platform-tools on PATH)
- **adb:** the Portal connects over USB-C; ADB enabled via Settings → Debug on the device
- **metavr / hzdb MCP:** Meta's CLI installed at user scope in Claude Code (auto-loads the "Portal skill")

### Portal hardware constraints honored
- **No Google Mobile Services** → no Google STT/TTS/Maps/Firebase; we bring our own voice stack.
- **Older AOSP** (API 29) → minSdk 28 / targetSdk 29.
- **Physical mic-mute switch** → if engaged, the app only hears silence.
- **Thin Settings UI** → the "display over other apps" permission is granted via adb (`appops`).
- **Dark-first design**, Meta blue `#0866FF`, Inter font, reserve top 64dp, 512px launcher icon in `mipmap-xxxhdpi/`.

---

## 3. Architecture

```
com.aura.assistant
├─ MainActivity.kt            Control panel: provider config, overlay perm, start/stop, voice toggle
├─ AuraApp.kt                 Application; initializes AssistantSession
├─ AssistantSession.kt        Shared brain (singleton): one conversation + provider + agent,
│                             observed by Compose, driven by both typed and voice input.
│                             Injects current date/time each turn.
├─ ai/
│  ├─ AiProvider.kt           interface { suspend complete(messages): String }
│  ├─ OpenAiCompatibleProvider.kt   OpenAI + any local/custom OpenAI-style URL
│  ├─ AnthropicProvider.kt    Claude (Messages API)
│  ├─ ProviderFactory.kt      builds provider from settings
│  └─ OpenAiAgent.kt          tool-calling loop (chat/completions + tools)
├─ tools/
│  ├─ Tool.kt                 tool interface
│  ├─ ToolRegistry.kt         exposes tools to the model + dispatches calls
│  ├─ WeatherTool.kt          get_weather (Open-Meteo geocode + forecast)
│  ├─ WebSearchTool.kt        web_search (DuckDuckGo Instant Answer)
│  └─ AlarmTools.kt           set_timer / set_alarm / set_reminder / list_alarms /
│                             cancel_alarm / stop_alarm
├─ alarm/
│  ├─ AlarmScheduler.kt       schedules via AlarmManager, tracks pending items
│  ├─ AlarmReceiver.kt        fires → hands off to AlarmService
│  └─ AlarmService.kt         plays a STOPPABLE looping tone + notification w/ Stop action
├─ data/Settings.kt           DataStore: provider, baseUrl, key, model, prompt,
│                             activation mode, wake phrase, auto-start
├─ overlay/
│  ├─ OverlayService.kt       foreground service; floating bubble + chat panel (Compose);
│  │                          bubble color reflects voice state
│  └─ OverlayLifecycleOwner.kt   lets Compose run inside a WindowManager window
├─ voice/
│  ├─ VoiceService.kt         mic capture (AudioRecord) + energy VAD + activation logic
│  ├─ SpeechToText.kt         OpenAI /audio/transcriptions (whisper-1)
│  ├─ TextToSpeechClient.kt   OpenAI /audio/speech (tts-1) → MediaPlayer
│  ├─ WavUtil.kt              PCM16 → WAV
│  └─ VoiceState.kt           ListeningState enum
├─ boot/BootReceiver.kt       auto-start overlay after reboot (opt-in)
└─ ui/theme/                  Portal palette + Inter typography
```

### Request flow
```
Voice:  mic → VAD → whisper-1 → wake-word check → AssistantSession.sendUserText
Typed:  overlay text field → AssistantSession.sendUserText
                ↓
   AssistantSession builds: [system prompt] + [current date/time] + history
                ↓
   OpenAiAgent.run  →  model decides → (optional) tool calls → tool results → final answer
                ↓
   reply shown in overlay  +  spoken via tts-1 (when voice is active)
```

---

## 4. Build journey & key decisions

1. **ADB / device.** Portal showed a Windows "code 43" (driver) error; cleared on reconnect.
   Added platform-tools to PATH. Confirmed device authorized.
2. **Learned Portal dev** from `developers.meta.com/horizon/.../portal-development` and the
   `portal-samples` `AGENTS.md`. Cloned the sample as the base; retargeted min 28 / target 29.
3. **Phase 1** — overlay + provider abstraction + settings + typed chat. Verified on device.
4. **Provider saga:**
   - Claude key authenticated but the account had **$0 credit** (billing, not code).
   - Tried **OpenAI**; clarified ChatGPT **Pro ≠ API access**.
   - Tried a **Codex-CLI bridge** to use the Pro login → blocked: Codex with a ChatGPT account
     is **server-side gated** (all models rejected). Abandoned. (`../codex-bridge/` kept, unused.)
   - Settled on an **OpenAI API key** (~$5 prepaid). Validated and configured on device.
5. **Phase 2 — voice.** Built mic capture + VAD + cloud STT/TTS. Verified end-to-end
   ("Hey Aura, what is the capital of Japan?" → spoken answer).
   - Fix: `gpt-4o-mini-transcribe` returned empty intermittently → switched to **whisper-1**.
   - Fix: whisper mishears "Aura" ("Yara"/"Ora") → added **fuzzy wake-word matching** (Levenshtein).
   - Tuned VAD (idle RMS ~40, speech ~2500–7000; threshold 1000; 8s max utterance).
6. **Phase 3 — agent/tools.** Added OpenAI function-calling agent + weather, web search, and
   alarms/timers/reminders. Verified weather + timer via typed input.
   - Fix: first alarm version played an unreferenced tone (unstoppable) → rebuilt as a
     **foreground AlarmService** with a held player, Stop action, "Hey Aura stop", and 60s auto-stop.

---

## 5. Configuration (on-device, in Settings)

- **Provider:** OPENAI · model `gpt-4o-mini`
- **API key:** entered in the app's Settings screen (not stored in this repo)
- **Activation:** Wake word `Hey Aura` (switchable to Continuous)
- **Default location:** set in Settings (e.g. "Miami, FL") — used for weather and location
  questions when no place is named; injected into context + used as the `get_weather` fallback.
  Saving settings calls `AssistantSession.refresh()` so changes apply immediately (no restart).
- **STT model:** `whisper-1` · **TTS model:** `tts-1`, voice `alloy`
- To use a **local LLM** instead: set provider = CUSTOM, base URL `http://<host>:11434/v1` (Ollama etc.)

---

## 6. Build & deploy

```powershell
# from C:\Users\Manny\Desktop\Portal\aura
.\gradlew.bat assembleDebug

$adb = "C:\Users\Manny\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk

# one-time permission grants (Portal's Settings UI is too thin)
& $adb shell appops set com.aura.assistant SYSTEM_ALERT_WINDOW allow
& $adb shell pm grant com.aura.assistant android.permission.RECORD_AUDIO

& $adb shell am start -n com.aura.assistant/.MainActivity
```

In the app: **Settings** → pick provider + paste key → **Save** → back → **Start Aura**
(overlay) → **Start listening** (voice). Make sure the **Portal mic is unmuted**.

### Distribution (sharing with others)
The home screen shows a **Setup checklist** (AI provider / display-over-apps / mic / assistant).
Per-user config (AI key, Home Assistant, location, wake word) is entered in-app — no secrets are baked in.
The adb-only steps (overlay perm, assistant role, screensaver) are automated by **`setup.ps1`**
(Windows) / **`setup.sh`** (Mac/Linux); end-user instructions are in **`SETUP.md`**.

**To hand the app to someone:** share `app-release.apk` + `setup.ps1`/`setup.sh` + `SETUP.md`.

**Release build (signed):**
```powershell
.\gradlew.bat assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
```
Signing creds are in `keystore.properties` (gitignored) referencing `aura-release.jks`. **Keep the
keystore safe** — the same key is required to ship updates that install over an existing copy.
Lint's Google-Play "targetSdk 33+" check is disabled in `build.gradle.kts` (we sideload to Portal).

### Keep the screen always on (no Superframe screensaver / no sleep)
Device-level (adb, persists; re-apply if a Portal update resets them):
```powershell
& $adb shell settings put secure screensaver_enabled 0
& $adb shell settings put secure screensaver_activate_on_sleep 0
& $adb shell settings put secure screensaver_activate_on_dock 0
& $adb shell settings put system screen_off_timeout 2147483647
```
App-level: the overlay window holds `FLAG_KEEP_SCREEN_ON` (in `OverlayService`), so while Aura
runs it keeps the screen awake over any app — robust even if the device settings get reset.
The Portal's Superframe screensaver is `com.facebook.alohaapps.superframe/...SuperframeDream`.

### Make voice work over other apps (assistant role)
Android 10 silences the mic for background apps unless they are the device **assistant**.
Aura ships a `VoiceInteractionService`; set it as the default assistant via adb (re-apply if a
Portal update resets it):
```powershell
& $adb shell settings put secure voice_interaction_service com.aura.assistant/.assist.AuraInteractionService
& $adb shell settings put secure assistant com.aura.assistant/.assist.AuraInteractionService
```
Without this, the mic returns RMS 0 (silence) whenever another app is in the foreground.

---

## 7. How to use

**By voice** (wake word): *"Hey Aura, &lt;request&gt;"* — speak ~1–2 ft away, then pause.
**By text:** tap the bubble → type.

Examples:
- "Hey Aura, what's the weather in Miami?"
- "Hey Aura, set a timer for 5 minutes."
- "Hey Aura, remind me to call mom in 1 hour."
- "Hey Aura, what time is it?"
- "Hey Aura, stop." (silences a ringing alert)

---

## 8. Known limitations

- **Web search** is the free keyless DuckDuckGo endpoint — weak on news/current events.
  Swap in a Brave/Tavily API key for real results.
- **Calendar** not wired — needs a calendar account, which a GMS-less Portal lacks.
- **Tool replies are slower** (~5–10s) — two model round-trips plus the API call.
- **Voice quality** depends on room noise / distance; VAD thresholds may need tuning per room.
- Scheduled alarms do **not** persist across reboot (in-memory list; could be added).
- **Inter font** uses Google's downloadable-font provider, which may be absent on Portal
  (falls back to system font; bundle Inter `.ttf` for strict compliance).

---

## 9. Next steps / roadmap

- [ ] Real web search (Brave/Tavily key)
- [ ] Shorter, more natural spoken replies (tighten system prompt)
- [ ] Custom Aura launcher icon (512px PNG)
- [ ] Persist alarms across reboot (DataStore + reschedule in BootReceiver)
- [ ] On-device wake word (Porcupine/Vosk) for lower latency / privacy
- [ ] More tools: smart-home, music, news, unit/currency conversion
- [ ] Calendar (if a calendar account is available on the device)

---

## 10. Useful references

- Portal dev docs: https://developers.meta.com/horizon/documentation/android-apps/portal-development
- Design requirements: https://developers.meta.com/horizon/documentation/android-apps/portal-design-requirements
- Sample base: https://github.com/meta-quest/portal-samples
- Open-Meteo (weather): https://open-meteo.com
- OpenAI API: https://platform.openai.com
