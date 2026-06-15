# AuraAI

**An always-on, voice-driven AI assistant for Meta Portal** — a self-hosted alternative to
Google Assistant / Siri / Alexa that floats over every app.

Point it at **OpenAI**, **Claude**, or any **local LLM**, and talk to it hands-free:
weather, timers, alarms, reminders, web lookups, and **smart-home control via Home Assistant**.

> Built on Meta's [portal-samples](https://github.com/meta-quest/portal-samples). Portal runs an
> older AOSP with no Google services, so the voice stack and assistant role are brought in-app.

## Features
- 🫧 **Floating overlay** bubble over any app; tap to chat or talk
- 🗣️ **Always-on voice** — wake word ("Hey Aura") or continuous; whisper-1 STT + tts-1 TTS
- 🧠 **Any AI provider** — OpenAI, Anthropic, or a local OpenAI-compatible URL (Ollama, LM Studio…)
- 🌤️ **Weather** (Open-Meteo, no key) with a configurable default location
- ⏰ **Timers / alarms / reminders** — on-device, stoppable
- 🔎 **Web lookups** · 🕐 time & date awareness
- 🏠 **Home Assistant** — control lights, thermostats, locks, scenes, and read device states
- 🖥️ Keeps the Portal screen on and **survives app-switches** (it registers as the device assistant)

## Install (on a Meta Portal)
See **[SETUP.md](SETUP.md)**. In short: enable ADB on the Portal, then run `setup.ps1` (Windows)
or `setup.sh` (Mac/Linux) from the folder with the APK. Grab the signed APK from the
[Releases](../../releases) page. Afterward, open Aura → Settings and enter your own AI key
(and optionally Home Assistant + a default location).

## Build from source
Requires JDK 17 + Android SDK (compileSdk 36). Targets `minSdk 28 / targetSdk 29` for Portal.
```bash
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # signed release (needs keystore.properties — see PROJECT.md)
```

## Docs
- **[SETUP.md](SETUP.md)** — end-user install & troubleshooting
- **[PROJECT.md](PROJECT.md)** — architecture, design decisions, and the full build log

## License
MIT — see [LICENSE](LICENSE). Derived from Meta's MIT-licensed Portal sample app.
